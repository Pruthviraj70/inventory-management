package com.pruthviraj.inventory.ui;

import java.time.*;
import java.sql.Timestamp;
import javax.swing.*;

public final class DateTimeChecks {
    private static int count;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS datetime "+label);}
    private static void rejects(String text,String label){try{DateTimes.parse(text);}catch(DateTimeException e){check(true,label);return;}throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        LocalDateTime raw=LocalDateTime.of(2026,3,4,9,7,58,987654000);
        check(DateTimes.format(raw).equals("04/03/2026 09:07"),"display omits seconds and fractions");
        check(DateTimes.format(null).isEmpty(),"null timestamp displays blank");
        check(DateTimes.edited("04/03/2026 09:07",raw).equals(raw),"unchanged text preserves seconds and microseconds");
        check(DateTimes.edited(" 04/03/2026 09:07 ",raw).equals(raw),"unchanged padded text preserves precision");
        check(DateTimes.edited("04/03/2026 09:08",raw).equals(LocalDateTime.of(2026,3,4,9,8)),"changed text supports minute input");
        check(DateTimes.parse("29/02/2028 23:59").getDayOfMonth()==29,"valid leap date accepted");
        rejects("31/02/2026 12:00","invalid calendar date rejected");rejects("04/03/2026 24:00","invalid time rejected");
        rejects("04/03/2026 09:07:58","seconds input rejected");rejects("2026-03-04 09:07","inconsistent format rejected");
        check(DateTimes.display(Timestamp.valueOf(raw)).equals("04/03/2026 09:07"),"JDBC timestamp uses same display");
        SwingUtilities.invokeAndWait(()->{
            DateTimeField field=new DateTimeField(raw);check(field.getDateTime().equals(raw),"editable component preserves original precision");
            field.setText("04/03/2026 10:00");check(field.getDateTime().equals(LocalDateTime.of(2026,3,4,10,0)),"editable component parses minutes");
            JTable table=Tables.table("When");Tables.rows(table,new Object[][]{{raw}});
            JLabel cell=(JLabel)table.prepareRenderer(table.getCellRenderer(0,0),0,0);
            check(cell.getText().equals("04/03/2026 09:07"),"shared table renderer hides seconds");
            check(table.getValueAt(0,0).equals(raw),"table model retains full timestamp");
            table.setRowSelectionInterval(0,0);
            var clipboard=new java.awt.datatransfer.Clipboard("test-only");
            table.getTransferHandler().exportToClipboard(table,clipboard,TransferHandler.COPY);
            try {check(clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor).equals("04/03/2026 09:07"),"clipboard export hides seconds without touching model precision");}
            catch(Exception e){throw new AssertionError(e);}
        });
        System.out.println("Date/time checks passed: "+count);
    }
}
