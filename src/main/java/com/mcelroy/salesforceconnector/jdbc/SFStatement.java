// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Call_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Catalog_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Upsert_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Write_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Placeholder_Replacer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SFStatement implements Statement {
    private SFConnection sfConnection;
    private SFClientConnection apiConnection;
    private ResultSet resultSet;
    protected String generatedId;
    protected List<String> generatedIds;
    protected int updateCount = -1;
    private final List<BatchItem> batch = new ArrayList<>();

    /** A queued batch entry: either a parsed INSERT/UPSERT with its parameters, or another SQL string. */
    protected static final class BatchItem {
        final String sql;
        final SQL_Write_Statement write;
        final Map<Integer, Object> params;
        final RuntimeException parseError;

        BatchItem(String sql, SQL_Write_Statement write, Map<Integer, Object> params, RuntimeException parseError) {
            this.sql = sql;
            this.write = write;
            this.params = params;
            this.parseError = parseError;
        }
    }

    public SFStatement(SFConnection sfConnection, SFClientConnection apiConnection) {
        this.sfConnection = sfConnection;
        this.apiConnection = apiConnection;
    }

    public SFClientConnection getApiConnection() {
        return apiConnection;
    }

    protected void resetExecution() {
        generatedId = null;
        generatedIds = null;
        updateCount = -1;
        resultSet = null;
    }

    /** One bound and JSON-encoded VALUES row, tagged with the batch entry that produced it. */
    private static final class PreparedRow {
        final int owner;
        final int rowNumber;
        final JSONObject record;
        final String externalId;
        final boolean binary;

        PreparedRow(int owner, int rowNumber, JSONObject record, String externalId, boolean binary) {
            this.owner = owner;
            this.rowNumber = rowNumber;
            this.record = record;
            this.externalId = externalId;
            this.binary = binary;
        }
    }

    /** Binds, validates and encodes every row of a write before anything is sent. */
    private List<PreparedRow> prepareRows(SQL_Write_Statement write, Map<Integer, Object> params, int owner)
            throws SQLException {
        int count = write.getRowCount();
        List<PreparedRow> prepared = new ArrayList<>(count);
        for (int r = 0; r < count; r++) {
            try {
                Map<String, Object> row = write.bind(r, params);
                String externalId = null;
                if (write instanceof SQL_Upsert_Statement) {
                    Object key = row.get(((SQL_Upsert_Statement) write).getExternalIdField());
                    if (key == null || key == JSONObject.NULL || key.toString().isEmpty())
                        throw new SQLException("UPSERT external ID must not be null or empty");
                    externalId = key.toString();
                }
                boolean binary = row.values().stream().anyMatch(value -> value instanceof byte[]);
                prepared.add(new PreparedRow(owner, count == 1 ? 0 : r + 1,
                        encodeRow(write.getTable(), row), externalId, binary));
            } catch (SQLException e) {
                if (count == 1)
                    throw e;
                throw new SQLException("Row " + (r + 1) + ": " + e.getMessage(), e);
            }
        }
        return prepared;
    }

    private JSONObject encodeRow(String table, Map<String, Object> row) throws SQLException {
        Map<String, Object> prepared = SFBinaryFields.prepare(sfConnection, apiConnection, table, row);
        prepared.replaceAll((field, value) -> SFParameterEncoder.toJson(value));
        return new JSONObject(prepared);
    }

    /** Sends one row through the single-record insert or upsert endpoint. */
    private JSONObject sendSingle(SQL_Write_Statement write, PreparedRow row) {
        if (write instanceof SQL_Upsert_Statement) {
            String externalIdField = ((SQL_Upsert_Statement) write).getExternalIdField();
            JSONObject body = new JSONObject();
            for (String field : row.record.keySet())
                if (!field.equals(externalIdField))
                    body.put(field, row.record.get(field));
            return apiConnection.upsert(write.getTable(), externalIdField, row.externalId, body.toString());
        }
        return apiConnection.insert(write.getTable(), row.record.toString());
    }

    public ResultSet execute(SQL_Statement sql_statement, Map<Integer, Object> placeholderValues) throws SQLException {
        resetExecution();
        if (sql_statement instanceof SQL_Call_Statement)
            throw new SQLFeatureNotSupportedException("Use prepareCall for CALL statements");
        if (sql_statement instanceof SQL_Write_Statement) {
            SQL_Write_Statement write = (SQL_Write_Statement) sql_statement;
            List<PreparedRow> rows = prepareRows(write, placeholderValues, 0);
            if (rows.size() == 1) {
                try {
                    JSONObject response = sendSingle(write, rows.get(0));
                    generatedId = response == null ? null : response.optString("id", null);
                    updateCount = 1;
                    return null;
                } catch (RuntimeException e) {
                    throw new SQLException(e.getMessage(), e);
                }
            }
            // Multi-row VALUES: valid rows are saved even if others fail (allOrNone=false).
            BatchRun run = new BatchRun(1, false);
            RowSender sender = new RowSender(write, run);
            for (PreparedRow row : rows)
                sender.add(row);
            sender.flush();
            updateCount = run.counts[0];
            generatedIds = run.ids;
            if (run.firstError != null)
                throw new SQLException(String.join("; ", run.errors), run.firstError);
            return null;
        }

        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        if (placeholderValues != null)
            writer = new SQL_Placeholder_Replacer(writer, placeholderValues, SFParameterEncoder::toSoql);
        sql_statement.accept(writer);

        if(sql_statement instanceof SQL_Catalog_Statement){
            executeUpdate(b.toString());
            return null;
        }

        resultSet = new SFResultSet(this, sql_statement, apiConnection.query(b.toString()));
        return resultSet;
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException {
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        return execute(sql_statement, null);
    }

    @Override
    public int executeUpdate(String s) throws SQLException {
        resetExecution();
        String sl = s.trim().toLowerCase(Locale.ROOT);
        if (sl.startsWith("insert") || sl.startsWith("upsert")) {
            try {
                execute(SQL_Statement.parse(s), null);
                return updateCount;
            } catch (RuntimeException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        if (sl.startsWith("catalog")) {
            String[] parts = sl.replaceAll(" +", " ").split(" ");
            if (parts.length == 2 && !parts[1].trim().equals("null")) {
                sfConnection.setCatalog(parts[1]);
                return 0;
            } else if (parts.length == 1 || parts[1].trim().equals("null")) {
                sfConnection.setCatalog(null);
                return 0;
            }
        }
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int i) throws SQLException {

    }

    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxRows(int i) throws SQLException {

    }

    @Override
    public void setEscapeProcessing(boolean b) throws SQLException {

    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(int i) throws SQLException {

    }

    @Override
    public void cancel() throws SQLException {

    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public void setCursorName(String s) throws SQLException {

    }

    @Override
    public boolean execute(String s) throws SQLException {
        if (s != null) {
            String sl = s.trim().toLowerCase(Locale.ROOT);

            if (sl.startsWith("select")) {
                executeQuery(s);
                return true;
            } else {
                executeUpdate(s);
                return false;
            }
        }

        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return resultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return updateCount;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        updateCount = -1;
        return false;
    }

    @Override
    public void setFetchDirection(int i) throws SQLException {

    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int i) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 2000;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return 0;
    }

    @Override
    public void addBatch(String s) throws SQLException {
        String sl = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (sl.startsWith("insert") || sl.startsWith("upsert")) {
            try {
                SQL_Statement parsed = SQL_Statement.parse(s);
                if (parsed instanceof SQL_Write_Statement) {
                    batch.add(new BatchItem(s, (SQL_Write_Statement) parsed, null, null));
                    return;
                }
            } catch (RuntimeException e) {
                batch.add(new BatchItem(s, null, null, e));
                return;
            }
        }
        batch.add(new BatchItem(s, null, null, null));
    }

    protected void addBatch(SQL_Write_Statement write, Map<Integer, Object> params) {
        batch.add(new BatchItem(null, write, new HashMap<>(params), null));
    }

    @Override
    public void clearBatch() throws SQLException {
        batch.clear();
    }

    /**
     * Executes queued statements. Rows from consecutive INSERTs (or UPSERTs with the same external
     * ID field) into the same table, including every row of multi-row VALUES, are sent through the
     * Salesforce sObject Collections API in requests of up to 200 records with allOrNone=false.
     * Rows containing binary data, and non-write statements, run individually. Processing continues
     * after failures; if any entry fails a {@link BatchUpdateException} is thrown carrying the status
     * of every entry.
     */
    @Override
    public int[] executeBatch() throws SQLException {
        List<BatchItem> items = new ArrayList<>(batch);
        batch.clear();
        resetExecution();
        BatchRun run = new BatchRun(items.size(), true);
        int i = 0;
        while (i < items.size()) {
            BatchItem item = items.get(i);
            if (item.parseError != null) {
                run.fail(i, 0, item.parseError.getMessage(), item.parseError);
                i++;
            } else if (item.write == null) {
                try {
                    run.counts[i] = executeUpdate(item.sql);
                } catch (SQLException | RuntimeException e) {
                    run.fail(i, 0, e.getMessage(), e);
                }
                i++;
            } else {
                i = executeWriteGroup(items, i, run);
            }
        }
        resetExecution();
        generatedIds = run.ids;
        int[] counts = run.finalCounts();
        if (run.firstError != null)
            throw new BatchUpdateException(String.join("; ", run.errors), counts, run.firstError);
        return counts;
    }

    /** Tracks per-entry update counts, saved record IDs (in order) and failures. */
    private static final class BatchRun {
        final int[] counts;
        final boolean[] failed;
        final boolean batchMode;
        final List<String> ids = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Throwable firstError;

        BatchRun(int size, boolean batchMode) {
            counts = new int[size];
            failed = new boolean[size];
            this.batchMode = batchMode;
        }

        void saved(PreparedRow row, String id) {
            counts[row.owner]++;
            if (id != null)
                ids.add(id);
        }

        /** @param rowNumber 1-based VALUES row, or 0 when the statement has a single row */
        void fail(int owner, int rowNumber, String message, Throwable cause) {
            failed[owner] = true;
            String label = batchMode
                    ? "Batch entry " + owner + (rowNumber > 0 ? " row " + rowNumber : "")
                    : "Row " + rowNumber;
            errors.add(label + ": " + message);
            if (firstError == null)
                firstError = cause != null ? cause : new SQLException(message);
        }

        int[] finalCounts() {
            int[] result = counts.clone();
            for (int i = 0; i < result.length; i++)
                if (failed[i])
                    result[i] = EXECUTE_FAILED;
            return result;
        }
    }

    private static String groupKey(SQL_Write_Statement write) {
        String key = write.getTable().toLowerCase(Locale.ROOT);
        if (write instanceof SQL_Upsert_Statement)
            return "upsert:" + key + ":"
                    + ((SQL_Upsert_Statement) write).getExternalIdField().toLowerCase(Locale.ROOT);
        return "insert:" + key;
    }

    /** Sends consecutive compatible writes starting at {@code start}; returns the next unprocessed index. */
    private int executeWriteGroup(List<BatchItem> items, int start, BatchRun run) {
        String key = groupKey(items.get(start).write);
        RowSender sender = new RowSender(items.get(start).write, run);
        int i = start;
        for (; i < items.size(); i++) {
            BatchItem item = items.get(i);
            if (item.write == null || item.parseError != null || !key.equals(groupKey(item.write)))
                break;
            List<PreparedRow> rows;
            try {
                rows = prepareRows(item.write, item.params, i);
            } catch (SQLException | RuntimeException e) {
                run.fail(i, 0, e.getMessage(), e);
                continue;
            }
            for (PreparedRow row : rows)
                sender.add(row);
        }
        sender.flush();
        return i;
    }

    /**
     * Accumulates rows for one table/operation and sends them as sObject Collections requests,
     * splitting at the 200-record limit and before a repeated UPSERT external ID.
     */
    private final class RowSender {
        private final SQL_Write_Statement write;
        private final BatchRun run;
        private final List<PreparedRow> pending = new ArrayList<>();
        private final Set<String> externalIds = new HashSet<>();

        RowSender(SQL_Write_Statement write, BatchRun run) {
            this.write = write;
            this.run = run;
        }

        void add(PreparedRow row) {
            if (row.binary) {
                // Keep large Base64 payloads out of collection requests.
                flush();
                try {
                    JSONObject response = sendSingle(write, row);
                    run.saved(row, response == null ? null : response.optString("id", null));
                } catch (RuntimeException e) {
                    run.fail(row.owner, row.rowNumber, e.getMessage(), e);
                }
                return;
            }
            // Salesforce rejects duplicate external IDs within one collection request.
            String externalId = row.externalId == null ? null : row.externalId.toLowerCase(Locale.ROOT);
            if (pending.size() >= SFClientConnection.COLLECTION_LIMIT
                    || (externalId != null && externalIds.contains(externalId)))
                flush();
            if (externalId != null)
                externalIds.add(externalId);
            pending.add(row);
        }

        void flush() {
            if (pending.isEmpty())
                return;
            JSONArray records = new JSONArray();
            for (PreparedRow row : pending)
                records.put(row.record);
            try {
                JSONArray results;
                if (write instanceof SQL_Upsert_Statement)
                    results = apiConnection.upsertCollection(write.getTable(),
                            ((SQL_Upsert_Statement) write).getExternalIdField(), records, false);
                else
                    results = apiConnection.insertCollection(write.getTable(), records, false);
                for (int j = 0; j < pending.size(); j++) {
                    PreparedRow row = pending.get(j);
                    JSONObject result = results == null ? null : results.optJSONObject(j);
                    if (result != null && result.optBoolean("success"))
                        run.saved(row, result.optString("id", null));
                    else
                        run.fail(row.owner, row.rowNumber, describeFailure(result), null);
                }
            } catch (RuntimeException e) {
                for (PreparedRow row : pending)
                    run.fail(row.owner, row.rowNumber, e.getMessage(), e);
            } finally {
                pending.clear();
                externalIds.clear();
            }
        }
    }

    private static String describeFailure(JSONObject result) {
        if (result == null)
            return "No result returned for record";
        JSONArray errors = result.optJSONArray("errors");
        if (errors == null || errors.length() == 0)
            return "Record was not saved";
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < errors.length(); i++) {
            JSONObject error = errors.optJSONObject(i);
            if (error == null)
                continue;
            String message = error.optString("statusCode", "ERROR") + ": " + error.optString("message", "");
            JSONArray fields = error.optJSONArray("fields");
            if (fields != null && fields.length() > 0)
                message += " " + fields;
            messages.add(message);
        }
        return messages.isEmpty() ? "Record was not saved" : String.join(", ", messages);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return sfConnection;
    }

    @Override
    public boolean getMoreResults(int i) throws SQLException {
        return getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        if (generatedIds != null) {
            for (String id : generatedIds)
                rows.add(new Object[]{id});
        } else if (generatedId != null) {
            rows.add(new Object[]{generatedId});
        }
        return new SFListResultSet(Collections.singletonList("Id"), rows,
                Collections.singletonList(Types.VARCHAR));
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
    }

    @Override
    public boolean execute(String s, int i) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public boolean execute(String s, int[] ints) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public boolean execute(String s, String[] strings) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void setPoolable(boolean b) throws SQLException {

    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {

    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        if (isWrapperFor(aClass))
            return aClass.cast(this);
        throw new SQLException("Not a wrapper for " + aClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return aClass != null && aClass.isInstance(this);
    }
}
