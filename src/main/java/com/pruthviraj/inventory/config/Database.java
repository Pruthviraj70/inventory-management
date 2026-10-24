package com.pruthviraj.inventory.config;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Opens a fresh connection per operation; never stores a global Connection. */
public final class Database {
    private final Path file;
    public Database() { this(Path.of(System.getProperty("inventory.config", "config/database.properties"))); }
    public Database(Path file) { this.file = file; }
    public Connection open() throws SQLException {
        Properties settings = new Properties();
        try (var input = Files.newInputStream(file)) { settings.load(input); }
        catch (IOException e) { throw new SQLException("Cannot read database configuration. Create config/database.properties from the example.", "08001"); }
        for (String key : new String[]{"db.url", "db.user", "db.password"}) {
            if (!settings.containsKey(key)) throw new SQLException("Missing configuration property: " + key, "08001");
        }
        String url = settings.getProperty("db.url");
        if (!url.startsWith("jdbc:mysql://")) throw new SQLException("Use a MySQL JDBC URL.", "08001");
        Properties credentials = new Properties();
        credentials.setProperty("user", settings.getProperty("db.user"));
        credentials.setProperty("password", settings.getProperty("db.password"));
        credentials.setProperty("connectTimeout", "3000");
        credentials.setProperty("socketTimeout", "10000");
        return DriverManager.getConnection(url, credentials);
    }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    public <T> T transaction(Work<T> work) throws SQLException {
        try (Connection c = open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
