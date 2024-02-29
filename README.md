# salesforce-connector
Salesforce JDBC driver using REST API v 50.

#### JDBC
Currently handles queries with optional column and table alias (no insert, update, or delete statements yet) 
and stored procedures (Salesforce custom action flows.)

Now supports row updates and inserts from the ResultSet.

#### Example Usage
````Java
    public static void main(String[] args) {
        String url = System.getProperty("url", "jdbc:sf:https://<your_company>.my.salesforce.com");

        Driver d = new SFDriver();
        Properties p = new Properties();
        p.setProperty("clientId", System.getProperty("clientId"));
        p.setProperty("clientSecret", System.getProperty("clientSecret"));
        p.setProperty("user", System.getProperty("user"));
        p.setProperty("password", System.getProperty("password"));

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
        p.setProperty("hibernate.connection.user", System.getProperty("user"));
        p.setProperty("hibernate.connection.password", System.getProperty("password"));
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
