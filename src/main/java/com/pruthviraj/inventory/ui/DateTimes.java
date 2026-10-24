package com.pruthviraj.inventory.ui;

import java.time.*;
import java.time.format.*;
import java.util.Locale;

/** Presentation only. Never use formatted strings for database storage or FIFO comparisons. */
public final class DateTimes {
    public static final String PATTERN="dd/MM/yyyy HH:mm";
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private DateTimes() { }
    public static String format(LocalDateTime value) {return value==null?"":FORMAT.format(value);}
    public static LocalDateTime parse(String text) {
        String value=text.strip();
        if(!value.matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"))throw new DateTimeException("Use "+PATTERN+".");
        return LocalDateTime.parse(value,FORMAT);
    }
    /** Saving unchanged minute text keeps the original seconds AND nanoseconds. */
    public static LocalDateTime edited(String text,LocalDateTime original) {
        return original!=null && text.strip().equals(format(original))?original:parse(text);
    }
    public static Object display(Object value) {
        if(value instanceof LocalDateTime date)return format(date);
        if(value instanceof java.sql.Timestamp date)return format(date.toLocalDateTime());
        if(value instanceof LocalDate date)return format(date.atStartOfDay());
        return value;
    }
}
