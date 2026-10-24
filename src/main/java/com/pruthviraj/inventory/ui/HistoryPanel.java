package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.service.*;
import com.pruthviraj.inventory.model.Models.*;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.List;

final class HistoryPanel extends JPanel {
    private final PurchaseService purchases;
    private final SaleService sales;
    private final DateTimeField from=new DateTimeField(LocalDate.now().minusDays(30).atStartOfDay()), through=new DateTimeField(LocalDate.now().atTime(23,59));
    private final JComboBox<String> type=new JComboBox<>(new String[]{"Sales","Purchase orders"});
    private final JTable table=Tables.table("ID","Customer / Supplier","Date","Markup % / Status","Total ₹ / Arrival");
    HistoryPanel(PurchaseService purchases,SaleService sales) {
        super(new BorderLayout(8,8));this.purchases=purchases;this.sales=sales;
        JPanel filters=new JPanel(new GridLayout(0,1));
        filters.add(Tables.buttons(type,Tables.button("Filter / Refresh",this::refresh),Tables.button("View details",this::details)));
        filters.add(Tables.buttons(new JLabel("From ("+DateTimes.PATTERN+")"),from,new JLabel("Through ("+DateTimes.PATTERN+")"),through));
        add(filters,BorderLayout.NORTH);
        add(new JScrollPane(table),BorderLayout.CENTER);
        type.addActionListener(e->{Tables.rows(table,new Object[0][]);try {refresh();}catch(RuntimeException ex){Tasks.error(this,ex);}});
        add(new JLabel("Range includes the entire final minute. Purchases use order datetime. Completed records are read-only."),BorderLayout.SOUTH);
    }
    void refresh() {
        LocalDateTime start,end;
        try {start=from.getDateTime();end=through.getDateTime();}
        catch(DateTimeException e) {throw new IllegalArgumentException("Use date/time in dd/MM/yyyy HH:mm format.");}
        if(end.isBefore(start) || start.getYear()<1000 || end.getYear()>9998) throw new IllegalArgumentException("Use an ordered date range within years 1000–9998.");
        if(type.getSelectedIndex()==0) Tasks.run(this,()->sales.list(start,end.plusMinutes(1)),rows->Tables.rows(table,rows.stream().map(s->new Object[]{s.id(),s.customer(),s.date(),s.markup(),s.total()}).toArray(Object[][]::new)));
        else Tasks.run(this,()->purchases.list(start,end.plusMinutes(1)),rows->Tables.rows(table,rows.stream().map(p->new Object[]{p.id(),p.supplier(),p.ordered(),p.status(),p.arrived()}).toArray(Object[][]::new)));
    }
    private void details() {
        int id=Tables.id(table);
        if(type.getSelectedIndex()==1) Tasks.run(this,()->purchases.lines(id),rows->PurchasePanel.showLines(this,rows));
        else Tasks.run(this,()->sales.lines(id),this::showSale);
    }
    private void showSale(List<SaleLine> rows) {
        JTable detail=Tables.table("Product ID","Product","Supplier ID","Supplier","Quantity","Saved unit price ₹","Line total ₹");
        Tables.rows(detail,rows.stream().map(l->new Object[]{l.itemId(),l.product(),l.supplierId(),l.supplier(),l.quantity(),l.price(),l.price().multiply(java.math.BigDecimal.valueOf(l.quantity()))}).toArray(Object[][]::new));
        JScrollPane scroll=new JScrollPane(detail);scroll.setPreferredSize(new Dimension(850,280));
        JOptionPane.showMessageDialog(this,scroll,"Historical sale lines",JOptionPane.PLAIN_MESSAGE);
    }
}
