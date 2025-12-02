package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A wrapper around JDBC Connection that provides monitoring and metrics
 */
@Slf4j
public class MonitoredConnection implements Connection {
    
    private final Connection delegate;
    private final String dataSourceKey;
    private final QueryContext queryContext;
    private final long creationTime;
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    public MonitoredConnection(Connection delegate, String dataSourceKey, QueryContext queryContext) {
        this.delegate = delegate;
        this.dataSourceKey = dataSourceKey;
        this.queryContext = queryContext;
        this.creationTime = System.currentTimeMillis();
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new MonitoredStatement(delegate.createStatement(), this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(delegate.prepareStatement(sql), sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkClosed();
        return new MonitoredCallableStatement(delegate.prepareCall(sql), sql, this);
    }
    
    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkClosed();
        return delegate.nativeSQL(sql);
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        delegate.setAutoCommit(autoCommit);
    }
    
    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return delegate.getAutoCommit();
    }
    
    @Override
    public void commit() throws SQLException {
        checkClosed();
        long startTime = System.currentTimeMillis();
        try {
            delegate.commit();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Commit completed in {}ms on {}", duration, dataSourceKey);
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        checkClosed();
        long startTime = System.currentTimeMillis();
        try {
            delegate.rollback();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Rollback completed in {}ms on {}", duration, dataSourceKey);
        }
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        
        long connectionLifetime = System.currentTimeMillis() - creationTime;
        log.debug("Closing connection on {} after {}ms, {} queries executed with avg time {}ms",
                dataSourceKey, connectionLifetime, queryCount.get(),
                queryCount.get() > 0 ? totalQueryTime.get() / queryCount.get() : 0);
        
        delegate.close();
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed.get() || delegate.isClosed();
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return delegate.getMetaData();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkClosed();
        delegate.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return delegate.isReadOnly();
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkClosed();
        delegate.setCatalog(catalog);
    }
    
    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return delegate.getCatalog();
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
        delegate.setTransactionIsolation(level);
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return delegate.getTransactionIsolation();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return delegate.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
        delegate.clearWarnings();
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return new MonitoredStatement(delegate.createStatement(resultSetType, resultSetConcurrency), this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(
            delegate.prepareStatement(sql, resultSetType, resultSetConcurrency), sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return new MonitoredCallableStatement(
            delegate.prepareCall(sql, resultSetType, resultSetConcurrency), sql, this);
    }
    
    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkClosed();
        return delegate.getTypeMap();
    }
    
    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkClosed();
        delegate.setTypeMap(map);
    }
    
    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkClosed();
        delegate.setHoldability(holdability);
    }
    
    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return delegate.getHoldability();
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkClosed();
        return delegate.setSavepoint();
    }
    
    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkClosed();
        return delegate.setSavepoint(name);
    }
    
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkClosed();
        delegate.rollback(savepoint);
    }
    
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkClosed();
        delegate.releaseSavepoint(savepoint);
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return new MonitoredStatement(
            delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(
            delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql, this);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        return new MonitoredCallableStatement(
            delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql, this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(delegate.prepareStatement(sql, autoGeneratedKeys), sql, this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(delegate.prepareStatement(sql, columnIndexes), sql, this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkClosed();
        return new MonitoredPreparedStatement(delegate.prepareStatement(sql, columnNames), sql, this);
    }
    
    @Override
    public Clob createClob() throws SQLException {
        checkClosed();
        return delegate.createClob();
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        checkClosed();
        return delegate.createBlob();
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        checkClosed();
        return delegate.createNClob();
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkClosed();
        return delegate.createSQLXML();
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !closed.get() && delegate.isValid(timeout);
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        delegate.setClientInfo(name, value);
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        delegate.setClientInfo(properties);
    }
    
    @Override
    public String getClientInfo(String name) throws SQLException {
        checkClosed();
        return delegate.getClientInfo(name);
    }
    
    @Override
    public Properties getClientInfo() throws SQLException {
        checkClosed();
        return delegate.getClientInfo();
    }
    
    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkClosed();
        return delegate.createArrayOf(typeName, elements);
    }
    
    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkClosed();
        return delegate.createStruct(typeName, attributes);
    }
    
    @Override
    public void setSchema(String schema) throws SQLException {
        checkClosed();
        delegate.setSchema(schema);
    }
    
    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return delegate.getSchema();
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        closed.set(true);
        delegate.abort(executor);
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkClosed();
        delegate.setNetworkTimeout(executor, milliseconds);
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        checkClosed();
        return delegate.getNetworkTimeout();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass()) || delegate.isWrapperFor(iface);
    }
    
    private void checkClosed() throws SQLException {
        if (closed.get()) {
            throw new SQLException("Connection is closed");
        }
    }
    
    void recordQueryExecution(long duration) {
        queryCount.incrementAndGet();
        totalQueryTime.addAndGet(duration);
    }
    
    String getDataSourceKey() {
        return dataSourceKey;
    }
    
    QueryContext getQueryContext() {
        return queryContext;
    }
    
    // Inner classes for monitored statements
    
    private static class MonitoredStatement implements Statement {
        protected final Statement delegate;
        protected final MonitoredConnection connection;
        
        MonitoredStatement(Statement delegate, MonitoredConnection connection) {
            this.delegate = delegate;
            this.connection = connection;
        }
        
        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            long startTime = System.currentTimeMillis();
            try {
                return delegate.executeQuery(sql);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                connection.recordQueryExecution(duration);
                log.debug("Query executed in {}ms on {}: {}", duration, connection.getDataSourceKey(), 
                         sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            }
        }
        
        @Override
        public int executeUpdate(String sql) throws SQLException {
            long startTime = System.currentTimeMillis();
            try {
                return delegate.executeUpdate(sql);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                connection.recordQueryExecution(duration);
                log.debug("Update executed in {}ms on {}: {}", duration, connection.getDataSourceKey(), 
                         sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            }
        }
        
        // Delegate all other methods
        @Override public void close() throws SQLException { delegate.close(); }
        @Override public int getMaxFieldSize() throws SQLException { return delegate.getMaxFieldSize(); }
        @Override public void setMaxFieldSize(int max) throws SQLException { delegate.setMaxFieldSize(max); }
        @Override public int getMaxRows() throws SQLException { return delegate.getMaxRows(); }
        @Override public void setMaxRows(int max) throws SQLException { delegate.setMaxRows(max); }
        @Override public void setEscapeProcessing(boolean enable) throws SQLException { delegate.setEscapeProcessing(enable); }
        @Override public int getQueryTimeout() throws SQLException { return delegate.getQueryTimeout(); }
        @Override public void setQueryTimeout(int seconds) throws SQLException { delegate.setQueryTimeout(seconds); }
        @Override public void cancel() throws SQLException { delegate.cancel(); }
        @Override public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override public void setCursorName(String name) throws SQLException { delegate.setCursorName(name); }
        @Override public boolean execute(String sql) throws SQLException { return delegate.execute(sql); }
        @Override public ResultSet getResultSet() throws SQLException { return delegate.getResultSet(); }
        @Override public int getUpdateCount() throws SQLException { return delegate.getUpdateCount(); }
        @Override public boolean getMoreResults() throws SQLException { return delegate.getMoreResults(); }
        @Override public void setFetchDirection(int direction) throws SQLException { delegate.setFetchDirection(direction); }
        @Override public int getFetchDirection() throws SQLException { return delegate.getFetchDirection(); }
        @Override public void setFetchSize(int rows) throws SQLException { delegate.setFetchSize(rows); }
        @Override public int getFetchSize() throws SQLException { return delegate.getFetchSize(); }
        @Override public int getResultSetConcurrency() throws SQLException { return delegate.getResultSetConcurrency(); }
        @Override public int getResultSetType() throws SQLException { return delegate.getResultSetType(); }
        @Override public void addBatch(String sql) throws SQLException { delegate.addBatch(sql); }
        @Override public void clearBatch() throws SQLException { delegate.clearBatch(); }
        @Override public int[] executeBatch() throws SQLException { return delegate.executeBatch(); }
        @Override public Connection getConnection() throws SQLException { return connection; }
        @Override public boolean getMoreResults(int current) throws SQLException { return delegate.getMoreResults(current); }
        @Override public ResultSet getGeneratedKeys() throws SQLException { return delegate.getGeneratedKeys(); }
        @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException { return delegate.executeUpdate(sql, autoGeneratedKeys); }
        @Override public int executeUpdate(String sql, int[] columnIndexes) throws SQLException { return delegate.executeUpdate(sql, columnIndexes); }
        @Override public int executeUpdate(String sql, String[] columnNames) throws SQLException { return delegate.executeUpdate(sql, columnNames); }
        @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException { return delegate.execute(sql, autoGeneratedKeys); }
        @Override public boolean execute(String sql, int[] columnIndexes) throws SQLException { return delegate.execute(sql, columnIndexes); }
        @Override public boolean execute(String sql, String[] columnNames) throws SQLException { return delegate.execute(sql, columnNames); }
        @Override public int getResultSetHoldability() throws SQLException { return delegate.getResultSetHoldability(); }
        @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }
        @Override public void setPoolable(boolean poolable) throws SQLException { delegate.setPoolable(poolable); }
        @Override public boolean isPoolable() throws SQLException { return delegate.isPoolable(); }
        @Override public void closeOnCompletion() throws SQLException { delegate.closeOnCompletion(); }
        @Override public boolean isCloseOnCompletion() throws SQLException { return delegate.isCloseOnCompletion(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }
    
    private static class MonitoredPreparedStatement extends MonitoredStatement implements PreparedStatement {
        private final PreparedStatement preparedDelegate;
        private final String sql;
        
        MonitoredPreparedStatement(PreparedStatement delegate, String sql, MonitoredConnection connection) {
            super(delegate, connection);
            this.preparedDelegate = delegate;
            this.sql = sql;
        }
        
        @Override
        public ResultSet executeQuery() throws SQLException {
            long startTime = System.currentTimeMillis();
            try {
                return preparedDelegate.executeQuery();
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                connection.recordQueryExecution(duration);
                log.debug("Prepared query executed in {}ms on {}: {}", duration, connection.getDataSourceKey(), 
                         sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            }
        }
        
        @Override
        public int executeUpdate() throws SQLException {
            long startTime = System.currentTimeMillis();
            try {
                return preparedDelegate.executeUpdate();
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                connection.recordQueryExecution(duration);
                log.debug("Prepared update executed in {}ms on {}: {}", duration, connection.getDataSourceKey(), 
                         sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            }
        }
        
        // Delegate all PreparedStatement methods
        @Override public void setNull(int parameterIndex, int sqlType) throws SQLException { preparedDelegate.setNull(parameterIndex, sqlType); }
        @Override public void setBoolean(int parameterIndex, boolean x) throws SQLException { preparedDelegate.setBoolean(parameterIndex, x); }
        @Override public void setByte(int parameterIndex, byte x) throws SQLException { preparedDelegate.setByte(parameterIndex, x); }
        @Override public void setShort(int parameterIndex, short x) throws SQLException { preparedDelegate.setShort(parameterIndex, x); }
        @Override public void setInt(int parameterIndex, int x) throws SQLException { preparedDelegate.setInt(parameterIndex, x); }
        @Override public void setLong(int parameterIndex, long x) throws SQLException { preparedDelegate.setLong(parameterIndex, x); }
        @Override public void setFloat(int parameterIndex, float x) throws SQLException { preparedDelegate.setFloat(parameterIndex, x); }
        @Override public void setDouble(int parameterIndex, double x) throws SQLException { preparedDelegate.setDouble(parameterIndex, x); }
        @Override public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException { preparedDelegate.setBigDecimal(parameterIndex, x); }
        @Override public void setString(int parameterIndex, String x) throws SQLException { preparedDelegate.setString(parameterIndex, x); }
        @Override public void setBytes(int parameterIndex, byte[] x) throws SQLException { preparedDelegate.setBytes(parameterIndex, x); }
        @Override public void setDate(int parameterIndex, Date x) throws SQLException { preparedDelegate.setDate(parameterIndex, x); }
        @Override public void setTime(int parameterIndex, Time x) throws SQLException { preparedDelegate.setTime(parameterIndex, x); }
        @Override public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException { preparedDelegate.setTimestamp(parameterIndex, x); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { preparedDelegate.setAsciiStream(parameterIndex, x, length); }
        @Override public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { preparedDelegate.setUnicodeStream(parameterIndex, x, length); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { preparedDelegate.setBinaryStream(parameterIndex, x, length); }
        @Override public void clearParameters() throws SQLException { preparedDelegate.clearParameters(); }
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException { preparedDelegate.setObject(parameterIndex, x, targetSqlType); }
        @Override public void setObject(int parameterIndex, Object x) throws SQLException { preparedDelegate.setObject(parameterIndex, x); }
        @Override public boolean execute() throws SQLException { return preparedDelegate.execute(); }
        @Override public void addBatch() throws SQLException { preparedDelegate.addBatch(); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException { preparedDelegate.setCharacterStream(parameterIndex, reader, length); }
        @Override public void setRef(int parameterIndex, Ref x) throws SQLException { preparedDelegate.setRef(parameterIndex, x); }
        @Override public void setBlob(int parameterIndex, Blob x) throws SQLException { preparedDelegate.setBlob(parameterIndex, x); }
        @Override public void setClob(int parameterIndex, Clob x) throws SQLException { preparedDelegate.setClob(parameterIndex, x); }
        @Override public void setArray(int parameterIndex, Array x) throws SQLException { preparedDelegate.setArray(parameterIndex, x); }
        @Override public ResultSetMetaData getMetaData() throws SQLException { return preparedDelegate.getMetaData(); }
        @Override public void setDate(int parameterIndex, Date x, java.util.Calendar cal) throws SQLException { preparedDelegate.setDate(parameterIndex, x, cal); }
        @Override public void setTime(int parameterIndex, Time x, java.util.Calendar cal) throws SQLException { preparedDelegate.setTime(parameterIndex, x, cal); }
        @Override public void setTimestamp(int parameterIndex, Timestamp x, java.util.Calendar cal) throws SQLException { preparedDelegate.setTimestamp(parameterIndex, x, cal); }
        @Override public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException { preparedDelegate.setNull(parameterIndex, sqlType, typeName); }
        @Override public void setURL(int parameterIndex, java.net.URL x) throws SQLException { preparedDelegate.setURL(parameterIndex, x); }
        @Override public ParameterMetaData getParameterMetaData() throws SQLException { return preparedDelegate.getParameterMetaData(); }
        @Override public void setRowId(int parameterIndex, RowId x) throws SQLException { preparedDelegate.setRowId(parameterIndex, x); }
        @Override public void setNString(int parameterIndex, String value) throws SQLException { preparedDelegate.setNString(parameterIndex, value); }
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException { preparedDelegate.setNCharacterStream(parameterIndex, value, length); }
        @Override public void setNClob(int parameterIndex, NClob value) throws SQLException { preparedDelegate.setNClob(parameterIndex, value); }
        @Override public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { preparedDelegate.setClob(parameterIndex, reader, length); }
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException { preparedDelegate.setBlob(parameterIndex, inputStream, length); }
        @Override public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { preparedDelegate.setNClob(parameterIndex, reader, length); }
        @Override public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException { preparedDelegate.setSQLXML(parameterIndex, xmlObject); }
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException { preparedDelegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { preparedDelegate.setAsciiStream(parameterIndex, x, length); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { preparedDelegate.setBinaryStream(parameterIndex, x, length); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException { preparedDelegate.setCharacterStream(parameterIndex, reader, length); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException { preparedDelegate.setAsciiStream(parameterIndex, x); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException { preparedDelegate.setBinaryStream(parameterIndex, x); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException { preparedDelegate.setCharacterStream(parameterIndex, reader); }
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException { preparedDelegate.setNCharacterStream(parameterIndex, value); }
        @Override public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException { preparedDelegate.setClob(parameterIndex, reader); }
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException { preparedDelegate.setBlob(parameterIndex, inputStream); }
        @Override public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException { preparedDelegate.setNClob(parameterIndex, reader); }
    }
    
    private static class MonitoredCallableStatement extends MonitoredPreparedStatement implements CallableStatement {
        private final CallableStatement callableDelegate;
        
        MonitoredCallableStatement(CallableStatement delegate, String sql, MonitoredConnection connection) {
            super(delegate, sql, connection);
            this.callableDelegate = delegate;
        }
        
        // Delegate all CallableStatement methods
        @Override public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException { callableDelegate.registerOutParameter(parameterIndex, sqlType); }
        @Override public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException { callableDelegate.registerOutParameter(parameterIndex, sqlType, scale); }
        @Override public boolean wasNull() throws SQLException { return callableDelegate.wasNull(); }
        @Override public String getString(int parameterIndex) throws SQLException { return callableDelegate.getString(parameterIndex); }
        @Override public boolean getBoolean(int parameterIndex) throws SQLException { return callableDelegate.getBoolean(parameterIndex); }
        @Override public byte getByte(int parameterIndex) throws SQLException { return callableDelegate.getByte(parameterIndex); }
        @Override public short getShort(int parameterIndex) throws SQLException { return callableDelegate.getShort(parameterIndex); }
        @Override public int getInt(int parameterIndex) throws SQLException { return callableDelegate.getInt(parameterIndex); }
        @Override public long getLong(int parameterIndex) throws SQLException { return callableDelegate.getLong(parameterIndex); }
        @Override public float getFloat(int parameterIndex) throws SQLException { return callableDelegate.getFloat(parameterIndex); }
        @Override public double getDouble(int parameterIndex) throws SQLException { return callableDelegate.getDouble(parameterIndex); }
        @Override public java.math.BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException { return callableDelegate.getBigDecimal(parameterIndex, scale); }
        @Override public byte[] getBytes(int parameterIndex) throws SQLException { return callableDelegate.getBytes(parameterIndex); }
        @Override public Date getDate(int parameterIndex) throws SQLException { return callableDelegate.getDate(parameterIndex); }
        @Override public Time getTime(int parameterIndex) throws SQLException { return callableDelegate.getTime(parameterIndex); }
        @Override public Timestamp getTimestamp(int parameterIndex) throws SQLException { return callableDelegate.getTimestamp(parameterIndex); }
        @Override public Object getObject(int parameterIndex) throws SQLException { return callableDelegate.getObject(parameterIndex); }
        @Override public java.math.BigDecimal getBigDecimal(int parameterIndex) throws SQLException { return callableDelegate.getBigDecimal(parameterIndex); }
        @Override public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException { return callableDelegate.getObject(parameterIndex, map); }
        @Override public Ref getRef(int parameterIndex) throws SQLException { return callableDelegate.getRef(parameterIndex); }
        @Override public Blob getBlob(int parameterIndex) throws SQLException { return callableDelegate.getBlob(parameterIndex); }
        @Override public Clob getClob(int parameterIndex) throws SQLException { return callableDelegate.getClob(parameterIndex); }
        @Override public Array getArray(int parameterIndex) throws SQLException { return callableDelegate.getArray(parameterIndex); }
        @Override public Date getDate(int parameterIndex, java.util.Calendar cal) throws SQLException { return callableDelegate.getDate(parameterIndex, cal); }
        @Override public Time getTime(int parameterIndex, java.util.Calendar cal) throws SQLException { return callableDelegate.getTime(parameterIndex, cal); }
        @Override public Timestamp getTimestamp(int parameterIndex, java.util.Calendar cal) throws SQLException { return callableDelegate.getTimestamp(parameterIndex, cal); }
        @Override public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException { callableDelegate.registerOutParameter(parameterIndex, sqlType, typeName); }
        @Override public void registerOutParameter(String parameterName, int sqlType) throws SQLException { callableDelegate.registerOutParameter(parameterName, sqlType); }
        @Override public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException { callableDelegate.registerOutParameter(parameterName, sqlType, scale); }
        @Override public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException { callableDelegate.registerOutParameter(parameterName, sqlType, typeName); }
        @Override public java.net.URL getURL(int parameterIndex) throws SQLException { return callableDelegate.getURL(parameterIndex); }
        @Override public void setURL(String parameterName, java.net.URL val) throws SQLException { callableDelegate.setURL(parameterName, val); }
        @Override public void setNull(String parameterName, int sqlType) throws SQLException { callableDelegate.setNull(parameterName, sqlType); }
        @Override public void setBoolean(String parameterName, boolean x) throws SQLException { callableDelegate.setBoolean(parameterName, x); }
        @Override public void setByte(String parameterName, byte x) throws SQLException { callableDelegate.setByte(parameterName, x); }
        @Override public void setShort(String parameterName, short x) throws SQLException { callableDelegate.setShort(parameterName, x); }
        @Override public void setInt(String parameterName, int x) throws SQLException { callableDelegate.setInt(parameterName, x); }
        @Override public void setLong(String parameterName, long x) throws SQLException { callableDelegate.setLong(parameterName, x); }
        @Override public void setFloat(String parameterName, float x) throws SQLException { callableDelegate.setFloat(parameterName, x); }
        @Override public void setDouble(String parameterName, double x) throws SQLException { callableDelegate.setDouble(parameterName, x); }
        @Override public void setBigDecimal(String parameterName, java.math.BigDecimal x) throws SQLException { callableDelegate.setBigDecimal(parameterName, x); }
        @Override public void setString(String parameterName, String x) throws SQLException { callableDelegate.setString(parameterName, x); }
        @Override public void setBytes(String parameterName, byte[] x) throws SQLException { callableDelegate.setBytes(parameterName, x); }
        @Override public void setDate(String parameterName, Date x) throws SQLException { callableDelegate.setDate(parameterName, x); }
        @Override public void setTime(String parameterName, Time x) throws SQLException { callableDelegate.setTime(parameterName, x); }
        @Override public void setTimestamp(String parameterName, Timestamp x) throws SQLException { callableDelegate.setTimestamp(parameterName, x); }
        @Override public void setAsciiStream(String parameterName, java.io.InputStream x, int length) throws SQLException { callableDelegate.setAsciiStream(parameterName, x, length); }
        @Override public void setBinaryStream(String parameterName, java.io.InputStream x, int length) throws SQLException { callableDelegate.setBinaryStream(parameterName, x, length); }
        @Override public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException { callableDelegate.setObject(parameterName, x, targetSqlType, scale); }
        @Override public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException { callableDelegate.setObject(parameterName, x, targetSqlType); }
        @Override public void setObject(String parameterName, Object x) throws SQLException { callableDelegate.setObject(parameterName, x); }
        @Override public void setCharacterStream(String parameterName, java.io.Reader reader, int length) throws SQLException { callableDelegate.setCharacterStream(parameterName, reader, length); }
        @Override public void setDate(String parameterName, Date x, java.util.Calendar cal) throws SQLException { callableDelegate.setDate(parameterName, x, cal); }
        @Override public void setTime(String parameterName, Time x, java.util.Calendar cal) throws SQLException { callableDelegate.setTime(parameterName, x, cal); }
        @Override public void setTimestamp(String parameterName, Timestamp x, java.util.Calendar cal) throws SQLException { callableDelegate.setTimestamp(parameterName, x, cal); }
        @Override public void setNull(String parameterName, int sqlType, String typeName) throws SQLException { callableDelegate.setNull(parameterName, sqlType, typeName); }
        @Override public String getString(String parameterName) throws SQLException { return callableDelegate.getString(parameterName); }
        @Override public boolean getBoolean(String parameterName) throws SQLException { return callableDelegate.getBoolean(parameterName); }
        @Override public byte getByte(String parameterName) throws SQLException { return callableDelegate.getByte(parameterName); }
        @Override public short getShort(String parameterName) throws SQLException { return callableDelegate.getShort(parameterName); }
        @Override public int getInt(String parameterName) throws SQLException { return callableDelegate.getInt(parameterName); }
        @Override public long getLong(String parameterName) throws SQLException { return callableDelegate.getLong(parameterName); }
        @Override public float getFloat(String parameterName) throws SQLException { return callableDelegate.getFloat(parameterName); }
        @Override public double getDouble(String parameterName) throws SQLException { return callableDelegate.getDouble(parameterName); }
        @Override public byte[] getBytes(String parameterName) throws SQLException { return callableDelegate.getBytes(parameterName); }
        @Override public Date getDate(String parameterName) throws SQLException { return callableDelegate.getDate(parameterName); }
        @Override public Time getTime(String parameterName) throws SQLException { return callableDelegate.getTime(parameterName); }
        @Override public Timestamp getTimestamp(String parameterName) throws SQLException { return callableDelegate.getTimestamp(parameterName); }
        @Override public Object getObject(String parameterName) throws SQLException { return callableDelegate.getObject(parameterName); }
        @Override public java.math.BigDecimal getBigDecimal(String parameterName) throws SQLException { return callableDelegate.getBigDecimal(parameterName); }
        @Override public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException { return callableDelegate.getObject(parameterName, map); }
        @Override public Ref getRef(String parameterName) throws SQLException { return callableDelegate.getRef(parameterName); }
        @Override public Blob getBlob(String parameterName) throws SQLException { return callableDelegate.getBlob(parameterName); }
        @Override public Clob getClob(String parameterName) throws SQLException { return callableDelegate.getClob(parameterName); }
        @Override public Array getArray(String parameterName) throws SQLException { return callableDelegate.getArray(parameterName); }
        @Override public Date getDate(String parameterName, java.util.Calendar cal) throws SQLException { return callableDelegate.getDate(parameterName, cal); }
        @Override public Time getTime(String parameterName, java.util.Calendar cal) throws SQLException { return callableDelegate.getTime(parameterName, cal); }
        @Override public Timestamp getTimestamp(String parameterName, java.util.Calendar cal) throws SQLException { return callableDelegate.getTimestamp(parameterName, cal); }
        @Override public java.net.URL getURL(String parameterName) throws SQLException { return callableDelegate.getURL(parameterName); }
        @Override public RowId getRowId(int parameterIndex) throws SQLException { return callableDelegate.getRowId(parameterIndex); }
        @Override public RowId getRowId(String parameterName) throws SQLException { return callableDelegate.getRowId(parameterName); }
        @Override public void setRowId(String parameterName, RowId x) throws SQLException { callableDelegate.setRowId(parameterName, x); }
        @Override public void setNString(String parameterName, String value) throws SQLException { callableDelegate.setNString(parameterName, value); }
        @Override public void setNCharacterStream(String parameterName, java.io.Reader value, long length) throws SQLException { callableDelegate.setNCharacterStream(parameterName, value, length); }
        @Override public void setNClob(String parameterName, NClob value) throws SQLException { callableDelegate.setNClob(parameterName, value); }
        @Override public void setClob(String parameterName, java.io.Reader reader, long length) throws SQLException { callableDelegate.setClob(parameterName, reader, length); }
        @Override public void setBlob(String parameterName, java.io.InputStream inputStream, long length) throws SQLException { callableDelegate.setBlob(parameterName, inputStream, length); }
        @Override public void setNClob(String parameterName, java.io.Reader reader, long length) throws SQLException { callableDelegate.setNClob(parameterName, reader, length); }
        @Override public NClob getNClob(int parameterIndex) throws SQLException { return callableDelegate.getNClob(parameterIndex); }
        @Override public NClob getNClob(String parameterName) throws SQLException { return callableDelegate.getNClob(parameterName); }
        @Override public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException { callableDelegate.setSQLXML(parameterName, xmlObject); }
        @Override public SQLXML getSQLXML(int parameterIndex) throws SQLException { return callableDelegate.getSQLXML(parameterIndex); }
        @Override public SQLXML getSQLXML(String parameterName) throws SQLException { return callableDelegate.getSQLXML(parameterName); }
        @Override public String getNString(int parameterIndex) throws SQLException { return callableDelegate.getNString(parameterIndex); }
        @Override public String getNString(String parameterName) throws SQLException { return callableDelegate.getNString(parameterName); }
        @Override public java.io.Reader getNCharacterStream(int parameterIndex) throws SQLException { return callableDelegate.getNCharacterStream(parameterIndex); }
        @Override public java.io.Reader getNCharacterStream(String parameterName) throws SQLException { return callableDelegate.getNCharacterStream(parameterName); }
        @Override public java.io.Reader getCharacterStream(int parameterIndex) throws SQLException { return callableDelegate.getCharacterStream(parameterIndex); }
        @Override public java.io.Reader getCharacterStream(String parameterName) throws SQLException { return callableDelegate.getCharacterStream(parameterName); }
        @Override public void setBlob(String parameterName, Blob x) throws SQLException { callableDelegate.setBlob(parameterName, x); }
        @Override public void setClob(String parameterName, Clob x) throws SQLException { callableDelegate.setClob(parameterName, x); }
        @Override public void setAsciiStream(String parameterName, java.io.InputStream x, long length) throws SQLException { callableDelegate.setAsciiStream(parameterName, x, length); }
        @Override public void setBinaryStream(String parameterName, java.io.InputStream x, long length) throws SQLException { callableDelegate.setBinaryStream(parameterName, x, length); }
        @Override public void setCharacterStream(String parameterName, java.io.Reader reader, long length) throws SQLException { callableDelegate.setCharacterStream(parameterName, reader, length); }
        @Override public void setAsciiStream(String parameterName, java.io.InputStream x) throws SQLException { callableDelegate.setAsciiStream(parameterName, x); }
        @Override public void setBinaryStream(String parameterName, java.io.InputStream x) throws SQLException { callableDelegate.setBinaryStream(parameterName, x); }
        @Override public void setCharacterStream(String parameterName, java.io.Reader reader) throws SQLException { callableDelegate.setCharacterStream(parameterName, reader); }
        @Override public void setNCharacterStream(String parameterName, java.io.Reader value) throws SQLException { callableDelegate.setNCharacterStream(parameterName, value); }
        @Override public void setClob(String parameterName, java.io.Reader reader) throws SQLException { callableDelegate.setClob(parameterName, reader); }
        @Override public void setBlob(String parameterName, java.io.InputStream inputStream) throws SQLException { callableDelegate.setBlob(parameterName, inputStream); }
        @Override public void setNClob(String parameterName, java.io.Reader reader) throws SQLException { callableDelegate.setNClob(parameterName, reader); }
        @Override public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException { return callableDelegate.getObject(parameterIndex, type); }
        @Override public <T> T getObject(String parameterName, Class<T> type) throws SQLException { return callableDelegate.getObject(parameterName, type); }
    }
}