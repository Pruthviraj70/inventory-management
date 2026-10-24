package com.pruthviraj.inventory.dao;

import java.sql.*;

/** Only JDBC mechanics; domain SQL remains in the concrete DAOs. */
final class Sql {
    private Sql() { }
    static void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
    }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (var p = c.prepareStatement(sql)) { bind(p, args); return p.executeUpdate(); }
    }
    static int insert(Connection c, String sql, Object... args) throws SQLException {
        try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(p, args); p.executeUpdate();
            try (var r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("No generated ID returned.");
                return r.getInt(1);
            }
        }
    }
    static void one(int count) throws SQLException {
        if (count != 1) throw new SQLException("Record changed or no longer exists. Refresh and try again.");
    }
}
