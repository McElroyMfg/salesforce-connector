# salesforce-connector
Rest and JDBC connector for Salesforce. Experimental and very minimal.

#### JDBC
Currently handles queries with optional column and table alias (no insert, update or delete yet) and stored procedures (Salesforce custom action flows.)

#### Example Usage
````Java
public class Example {
    public static void main(String[] args) {
        String url = "jdbc:sf:https://<your_company>.my.salesforce.com";
        String clientId = "<...>";
        String clientSecret = "<...>";
        String user = "<...>";
        String password = "<...>";

        Driver d = new SFDriver();
        Properties p = new Properties();
        p.setProperty("clientId", clientId);
        p.setProperty("clientSecret", clientSecret);
        p.setProperty("user", user);
        p.setProperty("password", password);

        try {
            Connection c = d.connect(url, p);
            PreparedStatement s = c.prepareStatement("select id, name from account where name like ? order by name");
            s.setString(1, "Mc%");
            boolean status = s.execute();
            do {
                if (status) {
                    ResultSet rs = s.getResultSet();
                    while (rs.next()) {
                        System.out.println(rs.getString("id") + " " + rs.getString("name"));
                    }
                    status = s.getMoreResults();
                }
            } while (status);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
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
