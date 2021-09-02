# salesforce-connector
Rest and JDBC connector for Salesforce. Experimental and very minimal.

#### JDBC
Currently handles queries with optional column and table alias (no insert, update or delete yet) and stored procedures (Salesforce custom action flows.)

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
