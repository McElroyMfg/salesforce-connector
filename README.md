# salesforce-connector
Salesforce JDBC driver using REST API v 50.
Starting in 3.1 changed to grant_type client_credentials
Starting in 3.2 added JWT bearer authentication, removed the password property,
and added optional sandbox (catalog) selection in the JDBC URL

#### Authentication
Two OAuth flows are supported, selected by which properties are provided:

| Property | client_credentials | JWT bearer |
|---|---|---|
| `clientId` | required | required |
| `clientSecret` | required | not used |
| `user` | optional | required (Salesforce username, used as the JWT `sub` claim) |
| `privateKey` | not used | required — presence of a non-empty key selects the JWT flow |

The `password` property is no longer supported.

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
Currently handles queries with optional column and table alias (no insert, update, or delete statements yet) 
and stored procedures (Salesforce custom action flows.)

Now supports row updates and inserts from the ResultSet.

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
