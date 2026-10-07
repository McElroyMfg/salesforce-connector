// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClient;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SFDatabaseMetaDataTest {
    private SFClientConnection client;
    private SFClient sfClient;
    private SFConnection connection;
    private SFDatabaseMetaData metadata;

    @Before
    public void setUp() {
        client = mock(SFClientConnection.class);
        sfClient = mock(SFClient.class);
        when(sfClient.getConnection("sandbox")).thenReturn(client);
        connection = new SFConnection(sfClient, "sandbox");
        metadata = new SFDatabaseMetaData(connection);
        when(client.describeGlobal()).thenReturn(new JSONObject().put("sobjects", new JSONArray()
                .put(object("Account", true)).put(object("Audit", false))
                .put(object("Custom_Object__c", true)).put(object("CustomXObject__c", true))
                .put(object("Rate%Object", true))));
        when(client.describe("Account")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(field("Id", "id", false, 18, 0, 0))
                .put(field("Name", "string", false, 255, 0, 0))
                .put(field("Revenue", "currency", true, 0, 18, 2))
                .put(field("OwnerId", "reference", false, 18, 0, 0)
                        .put("relationshipName", "Owner")
                        .put("referenceTo", new JSONArray().put("User").put("Group")))));
        when(client.listFlows()).thenReturn(new JSONObject().put("actions", new JSONArray()
                .put(new JSONObject().put("name", "Run_Flow").put("label", "Human label"))));
        when(client.describeFlow("Run_Flow")).thenReturn(new JSONObject()
                .put("inputs", new JSONArray()
                        .put(new JSONObject().put("name", "record").put("type", "SOBJECT")
                                .put("sobjectType", "Account").put("maxOccurs", 100)
                                .put("required", true).put("description", "Records to update"))
                        .put(new JSONObject().put("name", "enabled").put("type", "BOOLEAN").put("minOccurs", 1)))
                .put("outputs", new JSONArray()
                        .put(new JSONObject().put("name", "done").put("type", "BOOLEAN"))
                        .put(new JSONObject().put("name", "names").put("type", "STRING").put("isCollection", true))));
    }

    private static JSONObject object(String name, boolean queryable) {
        return new JSONObject().put("name", name).put("label", name + " label").put("queryable", queryable);
    }

    private static JSONObject field(String name, String type, boolean nullable, int length, int precision, int scale) {
        return new JSONObject().put("name", name).put("type", type).put("label", name + " label")
                .put("nillable", nullable).put("length", length).put("precision", precision).put("scale", scale);
    }

    private static void columns(ResultSet result, String expected) throws SQLException {
        String[] names = expected.split(",");
        ResultSetMetaData rsMetadata = result.getMetaData();
        assertEquals(names.length, rsMetadata.getColumnCount());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], rsMetadata.getColumnName(i + 1));
            assertEquals(names[i], rsMetadata.getColumnLabel(i + 1));
        }
    }

    private static void columnType(ResultSet result, String column, int type) throws SQLException {
        assertEquals(column, type, result.getMetaData().getColumnType(result.findColumn(column)));
    }

    @Test
    public void driverVersionMatchesBuildVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = SFDatabaseMetaData.class.getResourceAsStream("driver-version.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        String version = properties.getProperty("version");
        String[] components = version.split("\\.");

        assertEquals(version, metadata.getDriverVersion());
        assertEquals(Integer.parseInt(components[0]), metadata.getDriverMajorVersion());
        assertEquals(Integer.parseInt(components[1].replaceFirst("[^0-9].*$", "")),
                metadata.getDriverMinorVersion());
    }

    @Test
    public void tableColumnsNamesTypesAndQueryableFiltering() throws SQLException {
        try (ResultSet rows = metadata.getTables(null, null, "a%", null)) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,TABLE_TYPE,REMARKS,TYPE_CAT,TYPE_SCHEM,TYPE_NAME,SELF_REFERENCING_COL_NAME,REF_GENERATION");
            assertTrue(rows.next());
            assertEquals("sandbox", rows.getString("TABLE_CAT"));
            assertNull(rows.getString("TABLE_SCHEM"));
            assertEquals("Account", rows.getString("TABLE_NAME"));
            assertEquals("TABLE", rows.getString("TABLE_TYPE"));
            assertEquals("Account label", rows.getString("REMARKS"));
            assertFalse(rows.next());
        }
        assertFalse(metadata.getTables(null, null, "%", new String[]{"VIEW"}).next());
        assertFalse(metadata.getTables(null, null, "%", new String[0]).next());
        assertTrue(metadata.getTables("sandbox", "", "account", new String[]{"TABLE"}).next());
        assertFalse(metadata.getTables("other", null, "%", null).next());
        assertFalse(metadata.getTables(null, "not_a_schema", "%", null).next());
    }

    @Test
    public void likePatternsEscapeLiteralsAndTreatRegexCharactersLiterally() throws SQLException {
        try (ResultSet rows = metadata.getTables(null, null, "custom\\_object\\_\\_c", null)) {
            assertTrue(rows.next());
            assertEquals("Custom_Object__c", rows.getString("TABLE_NAME"));
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getTables(null, null, "custom_object%", null)) {
            assertTrue(rows.next());
            assertTrue(rows.next());
            assertFalse(rows.next());
        }
        assertTrue(metadata.getTables(null, null, "Rate\\%Object", null).next());
        assertFalse(metadata.getTables(null, null, "Account.*", null).next());
    }

    @Test
    public void columnNamesPrecisionScaleNullabilityAndOriginalOrdinal() throws SQLException {
        try (ResultSet rows = metadata.getColumns(null, null, "account", "rev%")) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,DATA_TYPE,TYPE_NAME,COLUMN_SIZE,BUFFER_LENGTH,DECIMAL_DIGITS,NUM_PREC_RADIX,NULLABLE,REMARKS,COLUMN_DEF,SQL_DATA_TYPE,SQL_DATETIME_SUB,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SCOPE_CATALOG,SCOPE_SCHEMA,SCOPE_TABLE,SOURCE_DATA_TYPE,IS_AUTOINCREMENT,IS_GENERATEDCOLUMN");
            assertTrue(rows.next());
            assertEquals("Revenue", rows.getString("COLUMN_NAME"));
            assertEquals(Types.DECIMAL, rows.getInt("DATA_TYPE"));
            assertEquals("currency", rows.getString("TYPE_NAME"));
            assertEquals(18, rows.getInt("COLUMN_SIZE"));
            assertEquals(2, rows.getInt("DECIMAL_DIGITS"));
            assertEquals(10, rows.getInt("NUM_PREC_RADIX"));
            assertEquals(DatabaseMetaData.columnNullable, rows.getInt("NULLABLE"));
            assertEquals("YES", rows.getString("IS_NULLABLE"));
            assertEquals(3, rows.getInt("ORDINAL_POSITION"));
            assertNull(rows.getObject("CHAR_OCTET_LENGTH"));
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getColumns(null, null, "Account", "Name")) {
            assertTrue(rows.next());
            assertEquals(255, rows.getInt("COLUMN_SIZE"));
            assertEquals(255, rows.getInt("CHAR_OCTET_LENGTH"));
            assertEquals(DatabaseMetaData.columnNoNulls, rows.getInt("NULLABLE"));
            assertEquals("NO", rows.getString("IS_NULLABLE"));
            assertEquals(0, rows.getInt("DECIMAL_DIGITS"));
            assertEquals(2, rows.getInt("ORDINAL_POSITION"));
        }
    }

    @Test
    public void mapsSalesforceFieldTypes() throws SQLException {
        String[] types = {"id", "reference", "string", "picklist", "multipicklist", "combobox", "email",
                "phone", "url", "encryptedstring", "textarea", "boolean", "int", "integer", "long",
                "double", "currency", "percent", "date", "datetime", "time", "base64", "address"};
        int[] jdbcTypes = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.LONGVARCHAR,
                Types.BOOLEAN, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.DECIMAL, Types.DECIMAL,
                Types.DECIMAL, Types.DATE, Types.TIMESTAMP, Types.TIME, Types.BLOB, Types.VARCHAR};
        JSONArray fields = new JSONArray();
        for (int i = 0; i < types.length; i++)
            fields.put(field("Field" + i, types[i], true, 128, 18, 3));
        when(client.describe("Account")).thenReturn(new JSONObject().put("fields", fields));
        try (ResultSet rows = metadata.getColumns(null, null, "Account", null)) {
            for (int i = 0; i < types.length; i++) {
                assertTrue(types[i], rows.next());
                assertEquals(types[i], jdbcTypes[i], rows.getInt("DATA_TYPE"));
                assertEquals(types[i], rows.getString("TYPE_NAME"));
                assertEquals(i + 1, rows.getInt("ORDINAL_POSITION"));
                assertEquals(128, rows.getInt("COLUMN_SIZE"));
                assertEquals(3, rows.getInt("DECIMAL_DIGITS"));
            }
            assertFalse(rows.next());
        }
    }

    @Test
    public void stringOctetLengthUsesByteLengthAndGeneratedFlagsAreReported() throws SQLException {
        when(client.describe("Account")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(field("Code", "string", false, 40, 0, 0).put("byteLength", 120)
                        .put("autoNumber", true).put("calculated", true).put("defaultValue", "initial"))));
        try (ResultSet rows = metadata.getColumns(null, null, "Account", "Code")) {
            assertTrue(rows.next());
            assertEquals(40, rows.getInt("COLUMN_SIZE"));
            assertEquals(120, rows.getInt("CHAR_OCTET_LENGTH"));
            assertEquals("NO", rows.getString("IS_AUTOINCREMENT"));
            assertEquals("YES", rows.getString("IS_GENERATEDCOLUMN"));
            assertEquals("initial", rows.getString("COLUMN_DEF"));
            assertFalse(rows.next());
        }
    }

    @Test
    public void textareaUsesLongVarcharAndNonNumericSizesFallBackToPrecision() throws SQLException {
        when(client.describe("Account")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(field("Notes", "textarea", true, 32768, 18, 4).put("byteLength", 98304))
                .put(field("Future", "futureType", true, 0, 44, 5))));
        try (ResultSet rows = metadata.getColumns(null, null, "Account", null)) {
            assertTrue(rows.next());
            assertEquals(Types.LONGVARCHAR, rows.getInt("DATA_TYPE"));
            assertEquals(32768, rows.getInt("COLUMN_SIZE"));
            assertEquals(98304, rows.getInt("CHAR_OCTET_LENGTH"));
            assertEquals(4, rows.getInt("DECIMAL_DIGITS"));
            assertTrue(rows.next());
            assertEquals(Types.VARCHAR, rows.getInt("DATA_TYPE"));
            assertEquals(44, rows.getInt("COLUMN_SIZE"));
            assertEquals(5, rows.getInt("DECIMAL_DIGITS"));
            assertFalse(rows.next());
        }
    }

    @Test
    public void primaryAndPolymorphicImportedKeys() throws SQLException {
        try (ResultSet rows = metadata.getPrimaryKeys(null, null, "Account")) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,KEY_SEQ,PK_NAME");
            assertTrue(rows.next());
            assertEquals("Id", rows.getString("COLUMN_NAME"));
            assertEquals(1, rows.getShort("KEY_SEQ"));
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getImportedKeys(null, null, "Account")) {
            columns(rows, "PKTABLE_CAT,PKTABLE_SCHEM,PKTABLE_NAME,PKCOLUMN_NAME,FKTABLE_CAT,FKTABLE_SCHEM,FKTABLE_NAME,FKCOLUMN_NAME,KEY_SEQ,UPDATE_RULE,DELETE_RULE,FK_NAME,PK_NAME,DEFERRABILITY");
            for (String parent : new String[]{"Group", "User"}) {
                assertTrue(rows.next());
                assertEquals(parent, rows.getString("PKTABLE_NAME"));
                assertEquals("Id", rows.getString("PKCOLUMN_NAME"));
                assertEquals("Account", rows.getString("FKTABLE_NAME"));
                assertEquals("OwnerId", rows.getString("FKCOLUMN_NAME"));
                assertEquals("Owner", rows.getString("FK_NAME"));
                assertEquals(1, rows.getInt("KEY_SEQ"));
                assertEquals(DatabaseMetaData.importedKeyNoAction, rows.getInt("UPDATE_RULE"));
                assertEquals(DatabaseMetaData.importedKeyNotDeferrable, rows.getInt("DEFERRABILITY"));
            }
            assertFalse(rows.next());
        }
        assertFalse(metadata.getPrimaryKeys("other", null, "Account").next());
        assertFalse(metadata.getImportedKeys(null, "missing", "Account").next());
        verify(client, times(1)).describe("Account");
    }

    @Test
    public void primaryKeyDoesNotRequireDescribe() throws SQLException {
        when(client.describe("Unlisted")).thenThrow(new RuntimeException("No describe available"));
        try (ResultSet rows = metadata.getPrimaryKeys(null, null, "Unlisted")) {
            assertTrue(rows.next());
            assertEquals("Unlisted", rows.getString("TABLE_NAME"));
            assertEquals("Id", rows.getString("COLUMN_NAME"));
            assertFalse(rows.next());
        }
        verifyZeroInteractions(client);
    }

    @Test
    public void proceduresUseApiNamesAndHumanLabels() throws SQLException {
        try (ResultSet rows = metadata.getProcedures(null, null, "run\\_flow")) {
            columns(rows, "PROCEDURE_CAT,PROCEDURE_SCHEM,PROCEDURE_NAME,RESERVED1,RESERVED2,RESERVED3,REMARKS,PROCEDURE_TYPE,SPECIFIC_NAME");
            assertTrue(rows.next());
            assertEquals("Run_Flow", rows.getString("PROCEDURE_NAME"));
            assertEquals("Run_Flow", rows.getString("SPECIFIC_NAME"));
            assertEquals("Human label", rows.getString("REMARKS"));
            assertEquals(DatabaseMetaData.procedureNoResult, rows.getInt("PROCEDURE_TYPE"));
            assertFalse(rows.next());
        }
        assertFalse(metadata.getProcedures(null, null, "Human label").next());
    }

    @Test
    public void procedureColumnsInputsThenOutputsCollectionsAndRequiredDescriptions() throws SQLException {
        try (ResultSet rows = metadata.getProcedureColumns(null, null, "run%", null)) {
            columns(rows, "PROCEDURE_CAT,PROCEDURE_SCHEM,PROCEDURE_NAME,COLUMN_NAME,COLUMN_TYPE,DATA_TYPE,TYPE_NAME,PRECISION,LENGTH,SCALE,RADIX,NULLABLE,REMARKS,COLUMN_DEF,SQL_DATA_TYPE,SQL_DATETIME_SUB,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SPECIFIC_NAME");
            assertTrue(rows.next());
            assertEquals("record", rows.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnIn, rows.getInt("COLUMN_TYPE"));
            assertEquals(Types.ARRAY, rows.getInt("DATA_TYPE"));
            assertEquals("SOBJECT<Account>[]", rows.getString("TYPE_NAME"));
            assertEquals(DatabaseMetaData.procedureNoNulls, rows.getInt("NULLABLE"));
            assertEquals("NO", rows.getString("IS_NULLABLE"));
            assertEquals("Records to update", rows.getString("REMARKS"));
            assertEquals(1, rows.getInt("ORDINAL_POSITION"));
            assertTrue(rows.next());
            assertEquals("enabled", rows.getString("COLUMN_NAME"));
            assertEquals(Types.BOOLEAN, rows.getInt("DATA_TYPE"));
            assertEquals("YES", rows.getString("IS_NULLABLE"));
            assertEquals(2, rows.getInt("ORDINAL_POSITION"));
            assertTrue(rows.next());
            assertEquals("done", rows.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnOut, rows.getInt("COLUMN_TYPE"));
            assertEquals(3, rows.getInt("ORDINAL_POSITION"));
            assertTrue(rows.next());
            assertEquals("STRING[]", rows.getString("TYPE_NAME"));
            assertEquals(Types.ARRAY, rows.getInt("DATA_TYPE"));
            assertEquals(4, rows.getInt("ORDINAL_POSITION"));
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getProcedureColumns(null, null, "Run_Flow", "done")) {
            assertTrue(rows.next());
            assertEquals(3, rows.getInt("ORDINAL_POSITION"));
            assertFalse(rows.next());
        }
        verify(client, times(1)).listFlows();
        verify(client, times(1)).describeFlow("Run_Flow");
    }

    @Test
    public void catalogsSchemasTableTypesAndTypeInfo() throws SQLException {
        try (ResultSet rows = metadata.getCatalogs()) {
            columns(rows, "TABLE_CAT");
            assertTrue(rows.next());
            assertEquals("sandbox", rows.getString(1));
            assertFalse(rows.next());
        }
        connection.setCatalog(null);
        try (ResultSet rows = metadata.getCatalogs()) {
            assertTrue(rows.next());
            assertNull(rows.getString("TABLE_CAT"));
            assertTrue(rows.wasNull());
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getSchemas()) {
            columns(rows, "TABLE_SCHEM,TABLE_CATALOG");
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getTableTypes()) {
            columns(rows, "TABLE_TYPE");
            assertTrue(rows.next());
            assertEquals("TABLE", rows.getString(1));
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getTypeInfo()) {
            columns(rows, "TYPE_NAME,DATA_TYPE,PRECISION,LITERAL_PREFIX,LITERAL_SUFFIX,CREATE_PARAMS,NULLABLE,CASE_SENSITIVE,SEARCHABLE,UNSIGNED_ATTRIBUTE,FIXED_PREC_SCALE,AUTO_INCREMENT,LOCAL_TYPE_NAME,MINIMUM_SCALE,MAXIMUM_SCALE,SQL_DATA_TYPE,SQL_DATETIME_SUB,NUM_PREC_RADIX");
            assertFalse(rows.next());
        }
    }

    @Test
    public void acceptsParameterAliasesInFlowDescriptions() throws SQLException {
        when(client.describeFlow("Run_Flow")).thenReturn(new JSONObject()
                .put("inputParameters", new JSONArray()
                        .put(new JSONObject().put("name", "input").put("type", "STRING")))
                .put("outputParameters", new JSONArray()
                        .put(new JSONObject().put("name", "output").put("type", "STRING"))));
        try (ResultSet rows = metadata.getProcedureColumns(null, null, "Run_Flow", null)) {
            assertTrue(rows.next());
            assertEquals("input", rows.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnIn, rows.getInt("COLUMN_TYPE"));
            assertTrue(rows.next());
            assertEquals("output", rows.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnOut, rows.getInt("COLUMN_TYPE"));
            assertEquals(2, rows.getInt("ORDINAL_POSITION"));
            assertFalse(rows.next());
        }
    }

    @Test
    public void flowTypesByteLengthsAndRequiredOnlyNullability() throws SQLException {
        String[] types = {"BOOLEAN", "INTEGER", "DOUBLE", "CURRENCY", "PERCENT", "NUMBER",
                "DATE", "DATETIME", "TIME", "SOBJECT", "APEX", "STRING", "UNKNOWN"};
        int[] jdbcTypes = {Types.BOOLEAN, Types.INTEGER, Types.DECIMAL, Types.DECIMAL, Types.DECIMAL,
                Types.DECIMAL, Types.DATE, Types.TIMESTAMP, Types.TIME, Types.STRUCT, Types.STRUCT,
                Types.VARCHAR, Types.VARCHAR};
        JSONArray parameters = new JSONArray();
        for (int i = 0; i < types.length; i++)
            parameters.put(new JSONObject().put("name", "Param" + i).put("type", types[i])
                    .put("byteLength", 32).put("length", 500).put("precision", 400)
                    .put("minOccurs", 1).put("required", i % 2 == 0));
        when(client.describeFlow("Run_Flow")).thenReturn(new JSONObject().put("inputs", parameters));
        try (ResultSet rows = metadata.getProcedureColumns(null, null, "Run_Flow", null)) {
            for (int i = 0; i < types.length; i++) {
                assertTrue(types[i], rows.next());
                assertEquals(types[i], jdbcTypes[i], rows.getInt("DATA_TYPE"));
                assertEquals(32, rows.getInt("PRECISION"));
                assertEquals(32, rows.getInt("LENGTH"));
                assertEquals(i % 2 == 0 ? DatabaseMetaData.procedureNoNulls : DatabaseMetaData.procedureNullable,
                        rows.getInt("NULLABLE"));
                assertEquals(i % 2 == 0 ? "NO" : "YES", rows.getString("IS_NULLABLE"));
            }
            assertFalse(rows.next());
        }
    }

    @Test
    public void usesSharedCacheKeyContract() throws SQLException {
        connection.getMetadataCache().get("global", () -> new JSONObject()
                .put("sobjects", new JSONArray().put(object("Account", true))));
        connection.getMetadataCache().get("sobject:Account", () -> new JSONObject()
                .put("fields", new JSONArray().put(field("Id", "id", false, 18, 0, 0))));
        connection.getMetadataCache().get("flows", () -> new JSONObject()
                .put("actions", new JSONArray().put(new JSONObject().put("name", "Run_Flow"))));
        connection.getMetadataCache().get("flow:Run_Flow", () -> new JSONObject()
                .put("inputs", new JSONArray().put(new JSONObject().put("name", "input").put("type", "STRING"))));
        assertTrue(metadata.getTables(null, null, "Account", null).next());
        assertTrue(metadata.getColumns(null, null, "Account", "Id").next());
        assertTrue(metadata.getProcedures(null, null, "Run_Flow").next());
        assertTrue(metadata.getProcedureColumns(null, null, "Run_Flow", "input").next());
        verifyZeroInteractions(client);
    }

    @Test
    public void failedExplicitFlowLaunchInvalidatesDescriptorLoadedByMetadata() throws SQLException {
        when(client.describeFlow("Run_Flow")).thenReturn(
                new JSONObject().put("inputs", new JSONArray()
                        .put(new JSONObject().put("name", "before").put("type", "STRING"))),
                new JSONObject().put("inputs", new JSONArray()
                        .put(new JSONObject().put("name", "after").put("type", "STRING"))));
        when(client.launchFlow(eq("Run_Flow"), anyString())).thenReturn(
                new JSONObject().put("isSuccess", false).put("errors", "Flow changed"));
        try (ResultSet rows = connection.getMetaData().getProcedureColumns(null, null, "Run_Flow", null)) {
            assertTrue(rows.next());
            assertEquals("before", rows.getString("COLUMN_NAME"));
        }
        try (CallableStatement statement = connection.prepareCall("call Run_Flow(record)")) {
            statement.setString(1, "value");
            try {
                statement.execute();
                fail("Expected failed Flow launch");
            } catch (SQLException e) {
                assertEquals("Flow changed", e.getMessage());
            }
        }
        verify(client, times(1)).describeFlow("Run_Flow");
        try (ResultSet rows = connection.getMetaData().getProcedureColumns(null, null, "Run_Flow", null)) {
            assertTrue(rows.next());
            assertEquals("after", rows.getString("COLUMN_NAME"));
        }
        verify(client, times(2)).describeFlow("Run_Flow");
        verify(client, times(1)).listFlows();
    }

    private void browseMetadata(DatabaseMetaData browser) throws SQLException {
        try (ResultSet rows = browser.getColumns(null, null, "Account", "Id")) {
            assertTrue(rows.next());
            assertEquals(connection.getCatalog(), rows.getString("TABLE_CAT"));
            assertEquals("Id", rows.getString("COLUMN_NAME"));
            assertFalse(rows.next());
        }
        try (ResultSet rows = browser.getProcedureColumns(null, null, "Run_Flow", "record")) {
            assertTrue(rows.next());
            assertEquals(connection.getCatalog(), rows.getString("PROCEDURE_CAT"));
            assertEquals("record", rows.getString("COLUMN_NAME"));
            assertFalse(rows.next());
        }
    }

    private void verifyMetadataLoads(int count) {
        verify(client, times(count)).describeGlobal();
        verify(client, times(count)).describe("Account");
        verify(client, times(count)).listFlows();
        verify(client, times(count)).describeFlow("Run_Flow");
    }

    @Test
    public void newMetadataInstancesShareCacheAndCloseResetsBrowsingLoads() throws SQLException {
        DatabaseMetaData first = connection.getMetaData();
        DatabaseMetaData second = connection.getMetaData();
        assertNotSame(first, second);
        browseMetadata(first);
        browseMetadata(second);
        verifyMetadataLoads(1);
        connection.close();
        browseMetadata(connection.getMetaData());
        verifyMetadataLoads(2);
    }

    @Test
    public void catalogChangeResetsBrowsingLoadsAndUpdatesMetadataCatalogs() throws SQLException {
        browseMetadata(connection.getMetaData());
        verifyMetadataLoads(1);
        when(sfClient.getConnection("other")).thenReturn(client);
        connection.setCatalog("other");
        browseMetadata(connection.getMetaData());
        browseMetadata(connection.getMetaData());
        verifyMetadataLoads(2);
        verify(sfClient, atLeastOnce()).getConnection("other");
    }

    @Test
    public void unsupportedResultSetsHaveJdbcColumns() throws SQLException {
        try (ResultSet rows = metadata.getIndexInfo(null, null, "Account", false, false)) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,NON_UNIQUE,INDEX_QUALIFIER,INDEX_NAME,TYPE,ORDINAL_POSITION,COLUMN_NAME,ASC_OR_DESC,CARDINALITY,PAGES,FILTER_CONDITION");
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getFunctions(null, null, null)) {
            columns(rows, "FUNCTION_CAT,FUNCTION_SCHEM,FUNCTION_NAME,REMARKS,FUNCTION_TYPE,SPECIFIC_NAME");
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getPseudoColumns(null, null, null, null)) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_SIZE,DECIMAL_DIGITS,NUM_PREC_RADIX,COLUMN_USAGE,REMARKS,CHAR_OCTET_LENGTH,IS_NULLABLE");
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getColumnPrivileges(null, null, "Account", null)) {
            columns(rows, "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,GRANTOR,GRANTEE,PRIVILEGE,IS_GRANTABLE");
            assertFalse(rows.next());
        }
    }

    @Test
    public void populatedAndEmptyMetadataUseStableJdbcColumnTypes() throws SQLException {
        for (String pattern : new String[]{"Account", "missing"}) {
            try (ResultSet rows = metadata.getColumns(null, null, pattern, null)) {
                columnType(rows, "DATA_TYPE", Types.INTEGER);
                columnType(rows, "COLUMN_SIZE", Types.INTEGER);
                columnType(rows, "NULLABLE", Types.INTEGER);
                columnType(rows, "DECIMAL_DIGITS", Types.INTEGER);
                columnType(rows, "ORDINAL_POSITION", Types.INTEGER);
                columnType(rows, "SOURCE_DATA_TYPE", Types.SMALLINT);
                columnType(rows, "COLUMN_NAME", Types.VARCHAR);
                assertEquals("Account".equals(pattern), rows.next());
            }
        }
        for (String pattern : new String[]{"Run_Flow", "missing"}) {
            try (ResultSet rows = metadata.getProcedureColumns(null, null, pattern, null)) {
                columnType(rows, "COLUMN_TYPE", Types.SMALLINT);
                columnType(rows, "DATA_TYPE", Types.INTEGER);
                columnType(rows, "NULLABLE", Types.SMALLINT);
                columnType(rows, "SCALE", Types.SMALLINT);
                columnType(rows, "RADIX", Types.SMALLINT);
                columnType(rows, "PRECISION", Types.INTEGER);
                columnType(rows, "ORDINAL_POSITION", Types.INTEGER);
                assertEquals("Run_Flow".equals(pattern), rows.next());
            }
        }
        try (ResultSet rows = metadata.getPrimaryKeys(null, null, null)) {
            columnType(rows, "KEY_SEQ", Types.SMALLINT);
            columnType(rows, "TABLE_NAME", Types.VARCHAR);
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getImportedKeys(null, null, null)) {
            columnType(rows, "KEY_SEQ", Types.SMALLINT);
            columnType(rows, "UPDATE_RULE", Types.SMALLINT);
            columnType(rows, "DELETE_RULE", Types.SMALLINT);
            columnType(rows, "DEFERRABILITY", Types.SMALLINT);
            assertFalse(rows.next());
        }
    }

    @Test
    public void unsupportedMetadataHasJdbcNumericAndBooleanColumnTypes() throws SQLException {
        try (ResultSet rows = metadata.getIndexInfo(null, null, null, false, false)) {
            columnType(rows, "NON_UNIQUE", Types.BOOLEAN);
            columnType(rows, "TYPE", Types.SMALLINT);
            columnType(rows, "ORDINAL_POSITION", Types.SMALLINT);
            columnType(rows, "CARDINALITY", Types.BIGINT);
            columnType(rows, "PAGES", Types.BIGINT);
            assertFalse(rows.next());
        }
        try (ResultSet rows = metadata.getTypeInfo()) {
            columnType(rows, "DATA_TYPE", Types.INTEGER);
            columnType(rows, "NULLABLE", Types.SMALLINT);
            columnType(rows, "SEARCHABLE", Types.SMALLINT);
            columnType(rows, "MINIMUM_SCALE", Types.SMALLINT);
            columnType(rows, "MAXIMUM_SCALE", Types.SMALLINT);
            columnType(rows, "CASE_SENSITIVE", Types.BOOLEAN);
            columnType(rows, "UNSIGNED_ATTRIBUTE", Types.BOOLEAN);
            columnType(rows, "FIXED_PREC_SCALE", Types.BOOLEAN);
            columnType(rows, "AUTO_INCREMENT", Types.BOOLEAN);
        }
        try (ResultSet rows = metadata.getBestRowIdentifier(null, null, null, 0, false)) {
            columnType(rows, "SCOPE", Types.SMALLINT);
            columnType(rows, "DATA_TYPE", Types.INTEGER);
            columnType(rows, "DECIMAL_DIGITS", Types.SMALLINT);
            columnType(rows, "PSEUDO_COLUMN", Types.SMALLINT);
        }
        try (ResultSet rows = metadata.getAttributes(null, null, null, null)) {
            columnType(rows, "ATTR_SIZE", Types.INTEGER);
            columnType(rows, "NULLABLE", Types.INTEGER);
            columnType(rows, "DECIMAL_DIGITS", Types.INTEGER);
            columnType(rows, "SOURCE_DATA_TYPE", Types.SMALLINT);
        }
        try (ResultSet rows = metadata.getFunctions(null, null, null)) {
            columnType(rows, "FUNCTION_TYPE", Types.SMALLINT);
        }
        try (ResultSet rows = metadata.getFunctionColumns(null, null, null, null)) {
            columnType(rows, "COLUMN_TYPE", Types.SMALLINT);
            columnType(rows, "NULLABLE", Types.SMALLINT);
            columnType(rows, "SCALE", Types.SMALLINT);
            columnType(rows, "RADIX", Types.SMALLINT);
            columnType(rows, "ORDINAL_POSITION", Types.INTEGER);
        }
        try (ResultSet rows = metadata.getPseudoColumns(null, null, null, null)) {
            columnType(rows, "DATA_TYPE", Types.INTEGER);
            columnType(rows, "COLUMN_SIZE", Types.INTEGER);
            columnType(rows, "DECIMAL_DIGITS", Types.INTEGER);
        }
        try (ResultSet rows = metadata.getUDTs(null, null, null, null)) {
            columnType(rows, "DATA_TYPE", Types.INTEGER);
            columnType(rows, "BASE_TYPE", Types.SMALLINT);
        }
        try (ResultSet rows = metadata.getClientInfoProperties()) {
            columnType(rows, "MAX_LEN", Types.INTEGER);
        }
    }

    @Test
    public void capabilitiesAndRemainingInterfaceMethodsDoNotThrow() throws Exception {
        assertEquals("Salesforce", metadata.getDatabaseProductName());
        assertEquals("flow", metadata.getProcedureTerm());
        assertTrue(metadata.supportsStoredProcedures());
        assertFalse(metadata.supportsTransactions());
        assertFalse(metadata.isReadOnly());
        assertEquals(Connection.TRANSACTION_NONE, metadata.getDefaultTransactionIsolation());
        assertSame(connection, metadata.getConnection());
        assertSame(metadata, metadata.unwrap(DatabaseMetaData.class));
        assertNull(metadata.unwrap(String.class));
        assertFalse(metadata.isWrapperFor(null));
        for (Method method : DatabaseMetaData.class.getMethods()) {
            if (ResultSet.class.equals(method.getReturnType())) continue;
            Object[] arguments = new Object[method.getParameterCount()];
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == int.class) arguments[i] = 0;
                else if (types[i] == boolean.class) arguments[i] = false;
            }
            method.invoke(metadata, arguments);
        }
        ResultSet[] results = {
                metadata.getBestRowIdentifier(null, null, null, 0, false),
                metadata.getVersionColumns(null, null, null),
                metadata.getExportedKeys(null, null, null),
                metadata.getCrossReference(null, null, null, null, null, null),
                metadata.getUDTs(null, null, null, null),
                metadata.getSuperTypes(null, null, null),
                metadata.getSuperTables(null, null, null),
                metadata.getAttributes(null, null, null, null),
                metadata.getClientInfoProperties(),
                metadata.getFunctionColumns(null, null, null, null),
                metadata.getTablePrivileges(null, null, null),
                metadata.getSchemas(null, null)
        };
        for (ResultSet result : results) {
            try (ResultSet rows = result) {
                assertTrue(rows.getMetaData().getColumnCount() > 0);
                assertFalse(rows.next());
            }
        }
    }

    @Test
    public void wrapsRestFailuresAsSqlExceptionsAndDoesNotCacheFailures() throws SQLException {
        RuntimeException failure = new RuntimeException("REST unavailable");
        when(client.describeGlobal()).thenThrow(failure)
                .thenReturn(new JSONObject().put("sobjects", new JSONArray()));
        try {
            metadata.getTables(null, null, "%", null);
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertSame(failure, e.getCause());
        }
        assertFalse(metadata.getTables(null, null, "%", null).next());
        when(client.describe("Account")).thenThrow(failure);
        try {
            metadata.getImportedKeys(null, null, "Account");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertSame(failure, e.getCause());
        }
        when(client.listFlows()).thenThrow(failure);
        try {
            metadata.getProcedures(null, null, "%");
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertSame(failure, e.getCause());
        }
    }
}
