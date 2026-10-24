package com.pruthviraj.inventory.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

final class Tables {
    private Tables() { }
    static JTable table(String... columns) {
        JTable table=new JTable(new DefaultTableModel(columns,0) { public boolean isCellEditable(int row,int column) { return false; } });
        // Keep full temporal values in the model; format only while painting cells.
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {super.setValue(DateTimes.display(value));}
        });
        // JTable's default clipboard export uses raw model.toString(); export the display text instead.
        table.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent source){return COPY;}
            @Override protected Transferable createTransferable(JComponent source) {
                JTable selected=(JTable)source;
                return selected.getSelectedRowCount()==0?null:new StringSelection(copyText(selected));
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true); table.setRowHeight(25); table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }
    static String copyText(JTable table) {
        int[] rows=table.getSelectedRows(),columns=table.getSelectedColumns();
        if(columns.length==0)columns=java.util.stream.IntStream.range(0,table.getColumnCount()).toArray();
        StringBuilder text=new StringBuilder();
        for(int i=0;i<rows.length;i++) {
            if(i>0)text.append('\n');
            for(int j=0;j<columns.length;j++) {
                if(j>0)text.append('\t');Object value=DateTimes.display(table.getValueAt(rows[i],columns[j]));
                if(value!=null)text.append(value);
            }
        }
        return text.toString();
    }
    static void rows(JTable table,Object[][] rows) {
        var model=(DefaultTableModel)table.getModel(); model.setRowCount(0);
        for(var row:rows) model.addRow(row);
    }
    static int selected(JTable table) {
        int row=table.getSelectedRow();
        if(row<0) throw new IllegalArgumentException("Select a row first.");
        return table.convertRowIndexToModel(row);
    }
    static int id(JTable table) { return ((Number)table.getModel().getValueAt(selected(table),0)).intValue(); }
    static JPanel form(Object... labelAndFields) {
        JPanel p=new JPanel(new GridLayout(0,2,10,10));
        for(int i=0;i<labelAndFields.length;i+=2) {
            p.add(new JLabel(String.valueOf(DateTimes.display(labelAndFields[i])))); p.add((Component)labelAndFields[i+1]);
        } return p;
    }
    static JPanel buttons(Component... controls) {
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEADING));
        for(var control:controls) p.add(control); return p;
    }
    static JButton button(String text,Runnable action) {
        JButton b=new JButton(text); b.addActionListener(e->{try { action.run(); } catch(RuntimeException ex) { Tasks.error(b,ex); }}); return b;
    }
    static boolean confirm(Component parent,String message) {
        return JOptionPane.showConfirmDialog(parent,message,"Confirm",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;
    }
}
