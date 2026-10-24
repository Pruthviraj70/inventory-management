package com.pruthviraj.inventory.service;

import java.math.*;

public final class Rules {
    private Rules() { }
    public static void transaction(java.sql.Connection c) throws java.sql.SQLException {
        if(c.getAutoCommit()) throw new IllegalArgumentException("A caller-owned transaction is required.");
    }
    public static void optionalDate(java.time.LocalDateTime value) {
        if(value!=null && (value.getYear()<1000 || value.getYear()>9999)) throw new IllegalArgumentException("Date must be within MySQL years 1000–9999.");
    }
    public static String name(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 150) throw new IllegalArgumentException("Name must contain 1–150 characters.");
        return name.strip();
    }
    public static int positive(int value) {
        if (value <= 0) throw new IllegalArgumentException("Quantity and selected IDs must be positive whole numbers.");
        return value;
    }
    public static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new IllegalArgumentException("Money must be between 0 and 999999999999.99.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    public static BigDecimal markup(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("99999.99")) > 0)
            throw new IllegalArgumentException("Markup must be between 0 and 99999.99 percent.");
        if (value.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Markup accepts at most two decimal places.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
