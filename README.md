# salesforce-connector
Salesforce JDBC driver using REST API v 50.
Starting in 3.1 changed to grant_type client_credentials
Starting in 3.2 added JWT bearer authentication
and added optional sandbox (catalog) selection in the JDBC URL

#### Authentication
Two OAuth flows are supported, selected by which properties are provided:

| Property | client_credentials | JWT bearer |
|---|---|---|
| `clientId` | required | required |
| `clientSecret` | required | not used |
| `user` | optional | required (Salesforce username, used as the JWT `sub` claim) |
| `privateKey` | not used | required — presence of a non-empty key selects the JWT flow |
| `password` | ignored | optional backup for `privateKey` |

The `password` property is accepted for compatibility (some applications always set it,
even blank). If `privateKey` is not set and `password` contains a PEM private key, it is
used as the JWT signing key; otherwise it is ignored.

For JWT bearer authentication the Connected App must have a certificate uploaded and
"Issue JSON Web Token (JWT)-based access tokens" enabled. The `privateKey` must be the
PKCS#8 PEM of the RSA key matching that certificate (`-----BEGIN PRIVATE KEY-----`).
The JWT is signed with RS256, with `iss` = clientId, `sub` = user, `aud` = the JDBC host,
and a 3 minute expiry (Salesforce rejects `exp` more than 3 minutes in the future).

##### Generating the private key and certificate

The private key stays with the client (the `privateKey` property); the certificate is
uploaded to the Connected App. Using OpenSSL:

````bash
# 1. Generate a 2048-bit RSA private key
openssl genrsa -out server.key 2048

# 2. Convert it to unencrypted PKCS#8 PEM (the format the driver expects)
openssl pkcs8 -topk8 -nocrypt -in server.key -out private_key.pem

# 3. Generate a self-signed certificate to upload to the Connected App
#    (fill in the prompts; the values are not validated by Salesforce)
openssl req -x509 -new -key private_key.pem -sha256 -days 3650 -out certificate.crt
````

Then in Salesforce: **Setup → App Manager → New Connected App → Enable OAuth Settings**,
check **Use digital signatures**, upload `certificate.crt`, and add the OAuth scopes you
need (e.g. `api`; `refresh_token, offline_access` is not required for the JWT flow).
After saving, click **Manage Consumer Details** to get the consumer key (`clientId`).

The contents of `private_key.pem` (including the `-----BEGIN PRIVATE KEY-----` and
`-----END PRIVATE KEY-----` lines) are what you pass as the `privateKey` property.

#### JDBC
Handles queries with optional column and table alias and single-row `INSERT INTO ... VALUES ...` statements
and stored procedures (Salesforce custom action flows.)

Now supports row updates and inserts from the ResultSet.

##### File uploads and generated keys
Binary parameters (`setBytes`, `setBinaryStream`, `setBlob`, or binary `setObject`)
are Base64-encoded for Salesforce fields whose describe type is `base64`.
Binding binary data to any other field fails with `SQLException`.

```java
try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO ContentVersion " +
        "(Title, PathOnClient, Description, ContentLocation, Temporary_File__c, VersionData) " +
        "VALUES (?, ?, ?, 'S', true, ?)", Statement.RETURN_GENERATED_KEYS)) {
    ps.setString(1, name);
    ps.setString(2, name);
    ps.setString(3, "Uploaded by " + userEmail);
    ps.setBinaryStream(4, fileInputStream);
    ps.executeUpdate(); // returns 1
    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (keys.next()) {
            id = keys.getString("Id");
        }
    }
}
```

`getGeneratedKeys()` exposes the inserted Salesforce `Id` as a VARCHAR column;
before an insert it returns an empty result set. INSERT values may be placeholders,
quoted strings, numbers, booleans, or `null`. SQL UPDATE/DELETE statements are not
supported; use the updatable ResultSet for record updates.

The `convertHeic` connection property defaults to `true` (also configurable with
`SFDataSource.setConvertHeic(boolean)`). HEIC/HEIF uploads are detected by content,
converted to JPEG for Salesforce preview, and `.heic`/`.heif` extensions in
`PathOnClient`, `Title`, and `Name` are changed to `.jpg`. Set `convertHeic=false`
to upload the original bytes without renaming. This also applies to binary
ResultSet inserts and updates. Streams are buffered in memory; supplied stream
lengths are respected, and caller-owned InputStreams remain open.

HEIC decoding uses `com.aspose:openize-heic:26.5` from the official Aspose Maven
repository and targets Java 8. Its Openize license is BSD-derived, not MIT:
redistribution requires retaining its notices, and users/distributors are
responsible for any required HEVC patent licenses and the license's indemnification
terms. Review these obligations before deployment. The connector itself remains
MIT-licensed. HEIF container rotation/mirroring is applied by the decoder;
EXIF-only orientation is not currently applied.

##### Metadata and caching
`Connection.getMetaData()` exposes queryable Salesforce objects as tables, fields as
columns, and autolaunched Flows as stored procedures with input/output parameters.
Flow calls may use explicit input names or JDBC `?` placeholders (input order comes
from the Flow describe response).

| Driver property | Default | Meaning |
|---|---|---|
| `metadataCacheTtlSeconds` | `300` | Metadata lifetime in seconds; `0` (or negative) disables caching |
| `metadataCacheMaxEntries` | `500` | Maximum cached describe/list responses; must be positive |

The expiring cache belongs to each JDBC connection, never to the shared REST client.
`getMetaData()` returns a fresh metadata object backed by that cache. `close()` and
`setCatalog(...)` clear it, and failed Flow calls evict the Flow describe entry.
For a pooled connection, reset metadata manually with
`connection.unwrap(SFConnection.class).clearMetadataCache()`.

##### Sandbox selection
The catalog (sandbox) can be set in the JDBC URL by adding it after the host:

````text
jdbc:sf:https://<your_company>.my.salesforce.com/<sandbox_name>
````

For example `jdbc:sf:https://mycompany.my.salesforce.com/demo` connects to the `demo`
sandbox. Omitting the sandbox name (or leaving it empty, e.g. `...salesforce.com/`)
connects to production. The sandbox can still be changed on an open connection with
`setCatalog(...)` or a `catalog <name>` statement (`catalog null` returns to production).

#### Example Usage
````Java
    public static void main(String[] args) {
        String url = System.getProperty("url", "jdbc:sf:https://<your_company>.my.salesforce.com");

        Driver d = new SFDriver();
        Properties p = new Properties();
        p.setProperty("clientId", System.getProperty("clientId"));
        p.setProperty("clientSecret", System.getProperty("clientSecret"));

        try {
            Connection c = d.connect(url, p);
            PreparedStatement s = c.prepareStatement("select id, name from account where name like ? order by name");
            s.setString(1, "Mc%");
            boolean status = s.execute();
            ResultSet rs = s.getResultSet();
            while (rs.next()) {
                System.out.println(rs.getString("id") + " " + rs.getString("name"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
````

#### Example JWT Usage
````Java
    public static void main(String[] args) throws IOException {
        String url = System.getProperty("url", "jdbc:sf:https://<your_company>.my.salesforce.com");

        Driver d = new SFDriver();
        Properties p = new Properties();
        p.setProperty("clientId", System.getProperty("clientId"));
        p.setProperty("user", System.getProperty("user")); // salesforce username, e.g. integration@your_company.com
        p.setProperty("privateKey", new String(Files.readAllBytes(Paths.get(System.getProperty("keyFile")))));

        try {
            Connection c = d.connect(url, p);
            PreparedStatement s = c.prepareStatement("select id, name from account where name like ? order by name");
            s.setString(1, "Mc%");
            boolean status = s.execute();
            ResultSet rs = s.getResultSet();
            while (rs.next()) {
                System.out.println(rs.getString("id") + " " + rs.getString("name"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
````

#### Example Hibernate Usage
````Java
    public static void main(String[] args) {
        String url = System.getProperty("url", "jdbc:sf:https://<your_company>.my.salesforce.com");

        Properties p = new Properties();
        p.setProperty("hibernate.connection.clientId", System.getProperty("clientId"));
        p.setProperty("hibernate.connection.clientSecret", System.getProperty("clientSecret"));
        p.setProperty("hibernate.connection.url", url);
        p.setProperty("hibernate.dialect", "com.mcelroy.salesforceconnector.jdbc.SFDialect");
        p.setProperty("hibernate.show_sql", "true");

        SessionFactory sf = new AnnotationConfiguration().addAnnotatedClass(Account.class).addProperties(p).buildSessionFactory();
        Session s = sf.openSession();

        Criteria c = s.createCriteria(Account.class);
        c.add(Restrictions.like("name", "Mc%"));
        for (Account a : (List<Account>) c.list()) {
            System.out.println(a.id + ' ' + a.name);
        }
    }

    @Entity
    @Table(name = "Account")
    public static class Account {
        @Id
        public String id;
        public String name;
    }
````

#### Maven Dependency
````XML
<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>
  
<dependency>
	<groupId>com.github.McElroyMfg</groupId>
	<artifactId>salesforce-connector</artifactId>
	<version>a864be6f53</version>
</dependency>
````
