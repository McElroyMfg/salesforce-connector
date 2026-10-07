// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** JDBC metadata backed by Salesforce object and Flow descriptions. */
public class SFDatabaseMetaData implements DatabaseMetaData {
    private static final String TABLE_COLUMNS =
            "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,TABLE_TYPE,REMARKS,TYPE_CAT,TYPE_SCHEM,TYPE_NAME,SELF_REFERENCING_COL_NAME,REF_GENERATION";
    private static final String COLUMN_COLUMNS =
            "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,DATA_TYPE,TYPE_NAME,COLUMN_SIZE,BUFFER_LENGTH,DECIMAL_DIGITS,NUM_PREC_RADIX,NULLABLE,REMARKS,COLUMN_DEF,SQL_DATA_TYPE,SQL_DATETIME_SUB,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SCOPE_CATALOG,SCOPE_SCHEMA,SCOPE_TABLE,SOURCE_DATA_TYPE,IS_AUTOINCREMENT,IS_GENERATEDCOLUMN";
    private static final String KEY_COLUMNS =
            "PKTABLE_CAT,PKTABLE_SCHEM,PKTABLE_NAME,PKCOLUMN_NAME,FKTABLE_CAT,FKTABLE_SCHEM,FKTABLE_NAME,FKCOLUMN_NAME,KEY_SEQ,UPDATE_RULE,DELETE_RULE,FK_NAME,PK_NAME,DEFERRABILITY";
    private static final String PROCEDURE_COLUMNS =
            "PROCEDURE_CAT,PROCEDURE_SCHEM,PROCEDURE_NAME,COLUMN_NAME,COLUMN_TYPE,DATA_TYPE,TYPE_NAME,PRECISION,LENGTH,SCALE,RADIX,NULLABLE,REMARKS,COLUMN_DEF,SQL_DATA_TYPE,SQL_DATETIME_SUB,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SPECIFIC_NAME";
    private final SFConnection connection;

    public SFDatabaseMetaData(SFConnection connection) {
        this.connection = connection;
    }

    private ResultSet result(String columns, List<Object[]> rows) {
        List<String> names = Arrays.asList(columns.split(","));
        List<Integer> types = new ArrayList<>();
        for (String name : names) types.add(metadataColumnType(name, names));
        return new SFListResultSet(names, rows, types);
    }

    private static int metadataColumnType(String name, List<String> columns) {
        switch (name) {
            case "NON_UNIQUE": case "CASE_SENSITIVE": case "UNSIGNED_ATTRIBUTE":
            case "FIXED_PREC_SCALE": case "AUTO_INCREMENT":
                return Types.BOOLEAN;
            case "CARDINALITY": case "PAGES":
                return Types.BIGINT;
            case "KEY_SEQ": case "UPDATE_RULE": case "DELETE_RULE": case "DEFERRABILITY":
            case "PROCEDURE_TYPE": case "FUNCTION_TYPE": case "COLUMN_TYPE":
            case "SCALE": case "RADIX": case "SEARCHABLE": case "MINIMUM_SCALE": case "MAXIMUM_SCALE":
            case "SCOPE": case "PSEUDO_COLUMN": case "TYPE": case "BASE_TYPE": case "SOURCE_DATA_TYPE":
                return Types.SMALLINT;
            case "NULLABLE":
                return columns.contains("PROCEDURE_NAME") || columns.contains("FUNCTION_NAME")
                        || columns.contains("SEARCHABLE") ? Types.SMALLINT : Types.INTEGER;
            case "DECIMAL_DIGITS":
                return columns.contains("PSEUDO_COLUMN") ? Types.SMALLINT : Types.INTEGER;
            case "ORDINAL_POSITION":
                return columns.contains("INDEX_NAME") ? Types.SMALLINT : Types.INTEGER;
            case "DATA_TYPE": case "COLUMN_SIZE": case "BUFFER_LENGTH": case "NUM_PREC_RADIX":
            case "SQL_DATA_TYPE": case "SQL_DATETIME_SUB": case "CHAR_OCTET_LENGTH":
            case "PRECISION": case "LENGTH": case "ATTR_SIZE": case "MAX_LEN":
            case "RESERVED1": case "RESERVED2": case "RESERVED3":
                return Types.INTEGER;
            default:
                return Types.VARCHAR;
        }
    }

    private ResultSet empty(String columns) {
        return result(columns, Collections.emptyList());
    }

    private JSONObject cached(String key, Supplier<JSONObject> loader) throws SQLException {
        try {
            return connection.getMetadataCache().get(key, loader);
        } catch (RuntimeException e) {
            throw new SQLException("Unable to retrieve Salesforce metadata: " + key, e);
        }
    }

    private JSONObject global() throws SQLException {
        return cached("global", () -> connection.getClientConnection().describeGlobal());
    }

    private JSONObject describe(String name) throws SQLException {
        return cached("sobject:" + name, () -> connection.getClientConnection().describe(name));
    }

    private JSONObject flows() throws SQLException {
        return cached("flows", () -> connection.getClientConnection().listFlows());
    }

    private JSONObject describeFlow(String name) throws SQLException {
        return cached("flow:" + name, () -> connection.getClientConnection().describeFlow(name));
    }

    private static JSONArray array(JSONObject object, String key) {
        JSONArray value = object.optJSONArray(key);
        return value == null ? new JSONArray() : value;
    }

    private boolean scope(String catalog, String schema) throws SQLException {
        return (catalog == null || catalog.equals(connection.getCatalog())
                || (catalog.isEmpty() && connection.getCatalog() == null))
                && (schema == null || matches("", schema));
    }

    private static boolean matches(String value, String pattern) {
        if (pattern == null) return true;
        StringBuilder regex = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (escaped) {
                regex.append(Pattern.quote(String.valueOf(c)));
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        if (escaped) regex.append(Pattern.quote("\\"));
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL)
                .matcher(value).matches();
    }

    private List<JSONObject> tables(String pattern) throws SQLException {
        List<JSONObject> tables = new ArrayList<>();
        JSONArray objects = array(global(), "sobjects");
        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.getJSONObject(i);
            if (object.optBoolean("queryable") && matches(object.optString("name"), pattern))
                tables.add(object);
        }
        tables.sort(Comparator.comparing(o -> o.optString("name")));
        return tables;
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        if (!scope(catalog, schemaPattern)
                || (types != null && Arrays.stream(types).noneMatch("TABLE"::equalsIgnoreCase)))
            return result(TABLE_COLUMNS, rows);
        for (JSONObject table : tables(tableNamePattern)) {
            rows.add(new Object[]{connection.getCatalog(), null, table.getString("name"), "TABLE",
                    table.optString("label"), null, null, null, null, null});
        }
        return result(TABLE_COLUMNS, rows);
    }

    private static int sqlType(String type) {
        switch (type.toLowerCase(Locale.ROOT)) {
            case "id": case "reference": case "string": case "picklist": case "multipicklist":
            case "combobox": case "email": case "phone": case "url": case "encryptedstring":
                return Types.VARCHAR;
            case "textarea": return Types.LONGVARCHAR;
            case "boolean": return Types.BOOLEAN;
            case "int": return Types.INTEGER;
            case "double": case "currency": case "percent": return Types.DECIMAL;
            case "date": return Types.DATE;
            case "datetime": return Types.TIMESTAMP;
            case "time": return Types.TIME;
            case "base64": return Types.BLOB;
            default: return Types.VARCHAR;
        }
    }

    private static int flowSqlType(String type) {
        switch (type.toUpperCase(Locale.ROOT)) {
            case "BOOLEAN": return Types.BOOLEAN;
            case "INTEGER": return Types.INTEGER;
            case "DOUBLE": case "CURRENCY": case "PERCENT": case "NUMBER": return Types.DECIMAL;
            case "DATE": return Types.DATE;
            case "DATETIME": return Types.TIMESTAMP;
            case "TIME": return Types.TIME;
            case "SOBJECT": case "APEX": return Types.STRUCT;
            default: return Types.VARCHAR;
        }
    }

    private static boolean characterType(int type) {
        return type == Types.VARCHAR || type == Types.LONGVARCHAR;
    }

    private static boolean numericType(int type) {
        return type == Types.INTEGER || type == Types.DECIMAL;
    }

    private static int size(JSONObject field) {
        int length = field.optInt("length");
        return length > 0 ? length : field.optInt("precision");
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
                                String columnNamePattern) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        if (!scope(catalog, schemaPattern)) return result(COLUMN_COLUMNS, rows);
        for (JSONObject table : tables(tableNamePattern)) {
            String name = table.getString("name");
            JSONArray fields = array(describe(name), "fields");
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (!matches(field.optString("name"), columnNamePattern)) continue;
                String typeName = field.optString("type");
                int type = sqlType(typeName);
                boolean nullable = field.optBoolean("nillable");
                rows.add(new Object[]{connection.getCatalog(), null, name, field.getString("name"),
                        type, typeName, size(field), null,
                        field.optInt("scale"), numericType(type) ? 10 : null,
                        nullable ? columnNullable : columnNoNulls, field.optString("label"),
                        field.isNull("defaultValue") ? null : field.opt("defaultValue"), null, null,
                        characterType(type) ? field.optInt("byteLength", field.optInt("length")) : null, i + 1,
                        nullable ? "YES" : "NO", null, null, null, null,
                        "NO",
                        field.optBoolean("calculated") ? "YES" : "NO"});
            }
        }
        return result(COLUMN_COLUMNS, rows);
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        String columns = "TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,KEY_SEQ,PK_NAME";
        List<Object[]> rows = new ArrayList<>();
        if (table == null || !scope(catalog, schema)) return result(columns, rows);
        rows.add(new Object[]{connection.getCatalog(), null, table, "Id", (short) 1, table + "_PK"});
        return result(columns, rows);
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        if (table == null || !scope(catalog, schema)) return result(KEY_COLUMNS, rows);
        JSONArray fields = array(describe(table), "fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (!"reference".equalsIgnoreCase(field.optString("type"))) continue;
            JSONArray references = array(field, "referenceTo");
            for (int j = 0; j < references.length(); j++) {
                String parent = references.getString(j);
                rows.add(new Object[]{connection.getCatalog(), null, parent, "Id",
                        connection.getCatalog(), null, table, field.getString("name"), (short) 1,
                        (short) importedKeyNoAction, (short) importedKeyNoAction,
                        field.optString("relationshipName", null),
                        parent + "_PK", (short) importedKeyNotDeferrable});
            }
        }
        rows.sort(Comparator.comparing((Object[] row) -> (String) row[2])
                .thenComparing(row -> (String) row[7]));
        return result(KEY_COLUMNS, rows);
    }

    private List<JSONObject> matchingFlows(String pattern) throws SQLException {
        List<JSONObject> flows = new ArrayList<>();
        JSONArray actions = array(flows(), "actions");
        for (int i = 0; i < actions.length(); i++) {
            JSONObject flow = actions.getJSONObject(i);
            if (matches(flow.optString("name"), pattern)) flows.add(flow);
        }
        flows.sort(Comparator.comparing(o -> o.optString("name")));
        return flows;
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        String columns = "PROCEDURE_CAT,PROCEDURE_SCHEM,PROCEDURE_NAME,RESERVED1,RESERVED2,RESERVED3,REMARKS,PROCEDURE_TYPE,SPECIFIC_NAME";
        List<Object[]> rows = new ArrayList<>();
        if (!scope(catalog, schemaPattern)) return result(columns, rows);
        for (JSONObject flow : matchingFlows(procedureNamePattern)) {
            String name = flow.getString("name");
            rows.add(new Object[]{connection.getCatalog(), null, name, null, null, null,
                    flow.optString("label"), (short) procedureNoResult, name});
        }
        return result(columns, rows);
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
                                         String columnNamePattern) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        if (!scope(catalog, schemaPattern)) return result(PROCEDURE_COLUMNS, rows);
        for (JSONObject flow : matchingFlows(procedureNamePattern)) {
            String name = flow.getString("name");
            JSONObject description = describeFlow(name);
            int ordinal = 1;
            for (String direction : Arrays.asList("inputs", "outputs")) {
                JSONArray parameters = description.optJSONArray(direction);
                if (parameters == null)
                    parameters = array(description, "inputs".equals(direction) ? "inputParameters" : "outputParameters");
                for (int i = 0; i < parameters.length(); i++, ordinal++) {
                    JSONObject parameter = parameters.getJSONObject(i);
                    if (!matches(parameter.optString("name"), columnNamePattern)) continue;
                    String typeName = parameter.optString("type");
                    String sobjectType = parameter.optString("sobjectType");
                    if (!sobjectType.isEmpty()) typeName += "<" + sobjectType + ">";
                    int maxOccurs = parameter.optInt("maxOccurs", 1);
                    boolean collection = parameter.optBoolean("isCollection") || maxOccurs > 1 || maxOccurs == -1;
                    int type = collection ? Types.ARRAY : flowSqlType(parameter.optString("type"));
                    if (collection) typeName += "[]";
                    boolean required = parameter.optBoolean("required");
                    String remarks = parameter.optString("description", parameter.optString("label"));
                    rows.add(new Object[]{connection.getCatalog(), null, name, parameter.getString("name"),
                            (short) ("inputs".equals(direction) ? procedureColumnIn : procedureColumnOut),
                            type, typeName, parameter.optInt("byteLength"), parameter.optInt("byteLength"),
                            numericType(type) ? parameter.optInt("scale") : null, numericType(type) ? 10 : null,
                            (short) (required ? procedureNoNulls : procedureNullable), remarks,
                            null, null, null, characterType(type) ? parameter.optInt("byteLength") : null,
                            ordinal, required ? "NO" : "YES", name});
                }
            }
        }
        return result(PROCEDURE_COLUMNS, rows);
    }

    @Override public ResultSet getCatalogs() throws SQLException {
        return result("TABLE_CAT", Collections.singletonList(new Object[]{connection.getCatalog()}));
    }
    @Override public ResultSet getSchemas() { return empty("TABLE_SCHEM,TABLE_CATALOG"); }
    @Override public ResultSet getSchemas(String catalog, String schemaPattern) { return getSchemas(); }
    @Override public ResultSet getTableTypes() {
        return result("TABLE_TYPE", Collections.singletonList(new Object[]{"TABLE"}));
    }
    @Override public ResultSet getTypeInfo() {
        return empty("TYPE_NAME,DATA_TYPE,PRECISION,LITERAL_PREFIX,LITERAL_SUFFIX,CREATE_PARAMS,NULLABLE,CASE_SENSITIVE,SEARCHABLE,UNSIGNED_ATTRIBUTE,FIXED_PREC_SCALE,AUTO_INCREMENT,LOCAL_TYPE_NAME,MINIMUM_SCALE,MAXIMUM_SCALE,SQL_DATA_TYPE,SQL_DATETIME_SUB,NUM_PREC_RADIX");
    }
    @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) {
        return empty("TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,GRANTOR,GRANTEE,PRIVILEGE,IS_GRANTABLE");
    }
    @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) {
        return empty("TABLE_CAT,TABLE_SCHEM,TABLE_NAME,GRANTOR,GRANTEE,PRIVILEGE,IS_GRANTABLE");
    }
    @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) {
        return empty("SCOPE,COLUMN_NAME,DATA_TYPE,TYPE_NAME,COLUMN_SIZE,BUFFER_LENGTH,DECIMAL_DIGITS,PSEUDO_COLUMN");
    }
    @Override public ResultSet getVersionColumns(String catalog, String schema, String table) {
        return empty("SCOPE,COLUMN_NAME,DATA_TYPE,TYPE_NAME,COLUMN_SIZE,BUFFER_LENGTH,DECIMAL_DIGITS,PSEUDO_COLUMN");
    }
    @Override public ResultSet getExportedKeys(String catalog, String schema, String table) { return empty(KEY_COLUMNS); }
    @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
                                               String foreignCatalog, String foreignSchema, String foreignTable) {
        return empty(KEY_COLUMNS);
    }
    @Override public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) {
        return empty("TABLE_CAT,TABLE_SCHEM,TABLE_NAME,NON_UNIQUE,INDEX_QUALIFIER,INDEX_NAME,TYPE,ORDINAL_POSITION,COLUMN_NAME,ASC_OR_DESC,CARDINALITY,PAGES,FILTER_CONDITION");
    }
    @Override public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) {
        return empty("TYPE_CAT,TYPE_SCHEM,TYPE_NAME,CLASS_NAME,DATA_TYPE,REMARKS,BASE_TYPE");
    }
    @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) {
        return empty("TYPE_CAT,TYPE_SCHEM,TYPE_NAME,SUPERTYPE_CAT,SUPERTYPE_SCHEM,SUPERTYPE_NAME");
    }
    @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) {
        return empty("TABLE_CAT,TABLE_SCHEM,TABLE_NAME,SUPERTABLE_NAME");
    }
    @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) {
        return empty("TYPE_CAT,TYPE_SCHEM,TYPE_NAME,ATTR_NAME,DATA_TYPE,ATTR_TYPE_NAME,ATTR_SIZE,DECIMAL_DIGITS,NUM_PREC_RADIX,NULLABLE,REMARKS,ATTR_DEF,SQL_DATA_TYPE,SQL_DATETIME_SUB,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SCOPE_CATALOG,SCOPE_SCHEMA,SCOPE_TABLE,SOURCE_DATA_TYPE");
    }
    @Override public ResultSet getClientInfoProperties() { return empty("NAME,MAX_LEN,DEFAULT_VALUE,DESCRIPTION"); }
    @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) {
        return empty("FUNCTION_CAT,FUNCTION_SCHEM,FUNCTION_NAME,REMARKS,FUNCTION_TYPE,SPECIFIC_NAME");
    }
    @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) {
        return empty("FUNCTION_CAT,FUNCTION_SCHEM,FUNCTION_NAME,COLUMN_NAME,COLUMN_TYPE,DATA_TYPE,TYPE_NAME,PRECISION,LENGTH,SCALE,RADIX,NULLABLE,REMARKS,CHAR_OCTET_LENGTH,ORDINAL_POSITION,IS_NULLABLE,SPECIFIC_NAME");
    }
    @Override public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) {
        return empty("TABLE_CAT,TABLE_SCHEM,TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_SIZE,DECIMAL_DIGITS,NUM_PREC_RADIX,COLUMN_USAGE,REMARKS,CHAR_OCTET_LENGTH,IS_NULLABLE");
    }

    @Override public Connection getConnection() { return connection; }
    @Override public boolean allProceduresAreCallable() { return true; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public String getURL() { return ""; }
    @Override public String getUserName() { return ""; }
    @Override public boolean isReadOnly() { return false; }
    @Override public boolean nullsAreSortedHigh() { return false; }
    @Override public boolean nullsAreSortedLow() { return true; }
    @Override public boolean nullsAreSortedAtStart() { return false; }
    @Override public boolean nullsAreSortedAtEnd() { return false; }
    @Override public String getDatabaseProductName() { return "Salesforce"; }
    @Override public String getDatabaseProductVersion() { return ""; }
    @Override public String getDriverName() { return "Salesforce Connector"; }
    @Override public String getDriverVersion() { return "3.2"; }
    @Override public int getDriverMajorVersion() { return 3; }
    @Override public int getDriverMinorVersion() { return 2; }
    @Override public boolean usesLocalFiles() { return false; }
    @Override public boolean usesLocalFilePerTable() { return false; }
    @Override public boolean supportsMixedCaseIdentifiers() { return false; }
    @Override public boolean storesUpperCaseIdentifiers() { return false; }
    @Override public boolean storesLowerCaseIdentifiers() { return false; }
    @Override public boolean storesMixedCaseIdentifiers() { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesUpperCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesLowerCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesMixedCaseQuotedIdentifiers() { return true; }
    @Override public String getIdentifierQuoteString() { return " "; }
    @Override public String getSQLKeywords() { return ""; }
    @Override public String getNumericFunctions() { return ""; }
    @Override public String getStringFunctions() { return ""; }
    @Override public String getSystemFunctions() { return ""; }
    @Override public String getTimeDateFunctions() { return ""; }
    @Override public String getSearchStringEscape() { return "\\"; }
    @Override public String getExtraNameCharacters() { return ""; }
    @Override public boolean supportsAlterTableWithAddColumn() { return false; }
    @Override public boolean supportsAlterTableWithDropColumn() { return false; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean nullPlusNonNullIsNull() { return true; }
    @Override public boolean supportsConvert() { return false; }
    @Override public boolean supportsConvert(int fromType, int toType) { return false; }
    @Override public boolean supportsTableCorrelationNames() { return true; }
    @Override public boolean supportsDifferentTableCorrelationNames() { return false; }
    @Override public boolean supportsExpressionsInOrderBy() { return false; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return false; }
    @Override public boolean supportsGroupByBeyondSelect() { return false; }
    @Override public boolean supportsLikeEscapeClause() { return false; }
    @Override public boolean supportsMultipleResultSets() { return false; }
    @Override public boolean supportsMultipleTransactions() { return false; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMinimumSQLGrammar() { return false; }
    @Override public boolean supportsCoreSQLGrammar() { return false; }
    @Override public boolean supportsExtendedSQLGrammar() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return false; }
    @Override public boolean supportsANSI92IntermediateSQL() { return false; }
    @Override public boolean supportsANSI92FullSQL() { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() { return false; }
    @Override public boolean supportsOuterJoins() { return false; }
    @Override public boolean supportsFullOuterJoins() { return false; }
    @Override public boolean supportsLimitedOuterJoins() { return false; }
    @Override public String getSchemaTerm() { return ""; }
    @Override public String getProcedureTerm() { return "flow"; }
    @Override public String getCatalogTerm() { return "environment"; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public String getCatalogSeparator() { return "."; }
    @Override public boolean supportsSchemasInDataManipulation() { return false; }
    @Override public boolean supportsSchemasInProcedureCalls() { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() { return false; }
    @Override public boolean supportsSchemasInIndexDefinitions() { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return false; }
    @Override public boolean supportsCatalogsInProcedureCalls() { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() { return false; }
    @Override public boolean supportsCatalogsInIndexDefinitions() { return false; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsPositionedDelete() { return false; }
    @Override public boolean supportsPositionedUpdate() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsStoredProcedures() { return true; }
    @Override public boolean supportsSubqueriesInComparisons() { return false; }
    @Override public boolean supportsSubqueriesInExists() { return false; }
    @Override public boolean supportsSubqueriesInIns() { return false; }
    @Override public boolean supportsSubqueriesInQuantifieds() { return false; }
    @Override public boolean supportsCorrelatedSubqueries() { return false; }
    @Override public boolean supportsUnion() { return false; }
    @Override public boolean supportsUnionAll() { return false; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return false; }
    @Override public boolean supportsOpenStatementsAcrossRollback() { return false; }
    @Override public int getMaxBinaryLiteralLength() { return 0; }
    @Override public int getMaxCharLiteralLength() { return 0; }
    @Override public int getMaxColumnNameLength() { return 0; }
    @Override public int getMaxColumnsInGroupBy() { return 0; }
    @Override public int getMaxColumnsInIndex() { return 0; }
    @Override public int getMaxColumnsInOrderBy() { return 0; }
    @Override public int getMaxColumnsInSelect() { return 0; }
    @Override public int getMaxColumnsInTable() { return 0; }
    @Override public int getMaxConnections() { return 0; }
    @Override public int getMaxCursorNameLength() { return 0; }
    @Override public int getMaxIndexLength() { return 0; }
    @Override public int getMaxSchemaNameLength() { return 0; }
    @Override public int getMaxProcedureNameLength() { return 0; }
    @Override public int getMaxCatalogNameLength() { return 0; }
    @Override public int getMaxRowSize() { return 0; }
    @Override public boolean doesMaxRowSizeIncludeBlobs() { return false; }
    @Override public int getMaxStatementLength() { return 0; }
    @Override public int getMaxStatements() { return 0; }
    @Override public int getMaxTableNameLength() { return 0; }
    @Override public int getMaxTablesInSelect() { return 0; }
    @Override public int getMaxUserNameLength() { return 0; }
    @Override public int getDefaultTransactionIsolation() { return Connection.TRANSACTION_NONE; }
    @Override public boolean supportsTransactions() { return false; }
    @Override public boolean supportsTransactionIsolationLevel(int level) { return level == Connection.TRANSACTION_NONE; }
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() { return false; }
    @Override public boolean supportsDataManipulationTransactionsOnly() { return false; }
    @Override public boolean dataDefinitionCausesTransactionCommit() { return false; }
    @Override public boolean dataDefinitionIgnoredInTransactions() { return false; }
    @Override public boolean supportsResultSetType(int type) { return type == ResultSet.TYPE_FORWARD_ONLY; }
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) {
        return supportsResultSetType(type) && concurrency == ResultSet.CONCUR_READ_ONLY;
    }
    @Override public boolean ownUpdatesAreVisible(int type) { return false; }
    @Override public boolean ownDeletesAreVisible(int type) { return false; }
    @Override public boolean ownInsertsAreVisible(int type) { return false; }
    @Override public boolean othersUpdatesAreVisible(int type) { return false; }
    @Override public boolean othersDeletesAreVisible(int type) { return false; }
    @Override public boolean othersInsertsAreVisible(int type) { return false; }
    @Override public boolean updatesAreDetected(int type) { return false; }
    @Override public boolean deletesAreDetected(int type) { return false; }
    @Override public boolean insertsAreDetected(int type) { return false; }
    @Override public boolean supportsBatchUpdates() { return false; }
    @Override public boolean supportsSavepoints() { return false; }
    @Override public boolean supportsNamedParameters() { return false; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public boolean supportsGetGeneratedKeys() { return false; }
    @Override public boolean supportsResultSetHoldability(int holdability) {
        return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }
    @Override public int getResultSetHoldability() { return ResultSet.HOLD_CURSORS_OVER_COMMIT; }
    @Override public int getDatabaseMajorVersion() { return 0; }
    @Override public int getDatabaseMinorVersion() { return 0; }
    @Override public int getJDBCMajorVersion() { return 4; }
    @Override public int getJDBCMinorVersion() { return 0; }
    @Override public int getSQLStateType() { return sqlStateSQL; }
    @Override public boolean locatorsUpdateCopy() { return false; }
    @Override public boolean supportsStatementPooling() { return false; }
    @Override public RowIdLifetime getRowIdLifetime() { return RowIdLifetime.ROWID_UNSUPPORTED; }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }
    @Override public boolean generatedKeyAlwaysReturned() { return false; }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface != null && iface.isInstance(this); }
    @Override public <T> T unwrap(Class<T> iface) { return isWrapperFor(iface) ? iface.cast(this) : null; }
}
