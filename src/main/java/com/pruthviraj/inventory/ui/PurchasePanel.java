package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

final class PurchasePanel extends JPanel {
    private final CatalogService catalog;
    private final PurchaseService purchases;
    private final JTable orders=Tables.table("Order ID","Supplier","Status","Ordered","Arrived");
    PurchasePanel(CatalogService catalog,PurchaseService purchases) {
        super(new BorderLayout(8,8));this.catalog=catalog;this.purchases=purchases;
        add(Tables.buttons(Tables.button("Refresh",this::refresh),Tables.button("Create order",this::create),Tables.button("View lines",()->view(Tables.id(orders),false)),Tables.button("Receive entire order",()->view(Tables.id(orders),true)),Tables.button("Cancel order",this::cancel)),BorderLayout.NORTH);
        add(new JScrollPane(orders),BorderLayout.CENTER);
        add(new JLabel("Creating and cancelling orders do not change stock. Receive the entire pending order once."),BorderLayout.SOUTH);
    }
    void refresh() { Tasks.run(this,()->purchases.list(LocalDateTime.of(1000,1,1,0,0),LocalDateTime.of(9999,12,31,0,0)),rows->Tables.rows(orders,rows.stream().map(p->new Object[]{p.id(),p.supplier(),p.status(),p.ordered(),p.arrived()}).toArray(Object[][]::new))); }
    private record Choices(List<Supplier> suppliers,List<Product> products,List<SupplierStock> pairs) { }
    private void create() {
        Tasks.run(this,()->new Choices(catalog.suppliers(""),catalog.products(""),catalog.associations()),this::createDialog);
    }
    private void createDialog(Choices data) {
        if(data.suppliers.isEmpty()) throw new IllegalArgumentException("Add a supplier and associate products first.");
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Create purchase order",Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body=new JPanel(new BorderLayout(8,8));body.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JComboBox<Supplier> supplier=new JComboBox<>(data.suppliers.toArray(Supplier[]::new));
        JComboBox<Product> product=new JComboBox<>(); JTextField quantity=new JTextField("1",6), price=new JTextField("0.00",10);
        JLabel minimum=new JLabel();JLabel subtotal=new JLabel("Goods subtotal: ₹0.00");
        JTable lines=Tables.table("Product ID","Product","Quantity","Supplier unit price (₹)","Goods total (₹)");
        var cart=new ArrayList<PurchaseInput>();
        Runnable render=()->{
            Tables.rows(lines,cart.stream().map(l->new Object[]{l.itemId(),data.products.stream().filter(p->p.id()==l.itemId()).findFirst().orElseThrow().name(),l.quantity(),l.price(),l.price().multiply(BigDecimal.valueOf(l.quantity()))}).toArray(Object[][]::new));
            subtotal.setText("Goods subtotal: ₹"+cart.stream().map(l->l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(new BigDecimal("0.00"),BigDecimal::add));
        };
        Runnable filter=()->{
            Supplier s=(Supplier)supplier.getSelectedItem();product.removeAllItems();cart.clear();render.run();
            if(s==null) return;
            minimum.setText("Minimum goods total: ₹"+s.minimum()+" | Supply time: "+s.days()+" days");
            for(var p:data.products) if(data.pairs.stream().anyMatch(a->a.itemId()==p.id() && a.supplierId()==s.id())) product.addItem(p);
        };
        supplier.addActionListener(e->filter.run());filter.run();
        JPanel top=new JPanel(new GridLayout(0,1));
        top.add(Tables.buttons(new JLabel("Supplier"),supplier,minimum));
        top.add(Tables.buttons(new JLabel("Product"),product,new JLabel("Qty"),quantity,new JLabel("Unit price ₹"),price,Tables.button("Add / merge",()->{
            Product p=(Product)product.getSelectedItem();if(p==null) throw new IllegalArgumentException("Associate a product with this supplier first.");
            var proposed=new ArrayList<>(cart);proposed.add(new PurchaseInput(p.id(),Integer.parseInt(quantity.getText().strip()),new BigDecimal(price.getText().strip())));
            var merged=purchases.merge(proposed);cart.clear();cart.addAll(merged);render.run();
        }),Tables.button("Remove line",()->{cart.remove(Tables.selected(lines));render.run();})));
        body.add(top,BorderLayout.NORTH);body.add(new JScrollPane(lines),BorderLayout.CENTER);
        body.add(Tables.buttons(subtotal,Tables.button("Save pending order",()->{
            Supplier s=(Supplier)supplier.getSelectedItem();var snapshot=List.copyOf(cart);
            Tasks.run(body,()->purchases.create(s.id(),snapshot),id->{dialog.dispose();refresh();JOptionPane.showMessageDialog(this,"Pending order #"+id+" saved.");});
        }),Tables.button("Cancel",dialog::dispose)),BorderLayout.SOUTH);
        dialog.setContentPane(body);dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private void cancel() {
        int id=Tables.id(orders);
        if(Tables.confirm(this,"Cancel pending order #"+id+"? Stock will remain unchanged.")) Tasks.run(this,()->{purchases.cancel(id);return true;},ok->refresh());
    }
    private void view(int id,boolean receive) {
        if(receive && !"PENDING".equals(orders.getModel().getValueAt(Tables.selected(orders),2))) throw new IllegalArgumentException("Select a pending order.");
        Tasks.run(this,()->purchases.lines(id),rows->{
            if(receive) receiveDialog(id,rows); else showLines(this,rows);
        });
    }
    static void showLines(Component parent,List<PurchaseLine> rows) {
        JTable table=Tables.table("Product ID","Product","Supplier ID","Quantity","Supplier unit price (₹)","Landed unit cost (₹)","Remaining");
        Tables.rows(table,rows.stream().map(l->new Object[]{l.itemId(),l.product(),l.supplierId(),l.quantity(),l.price(),l.landed(),l.remaining()}).toArray(Object[][]::new));
        JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(850,280));
        JOptionPane.showMessageDialog(parent,scroll,"Purchase lines",JOptionPane.PLAIN_MESSAGE);
    }
    private void receiveDialog(int id,List<PurchaseLine> lines) {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Receive order #"+id,Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body=new JPanel(new BorderLayout(10,10));body.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel costs=new JPanel(new GridLayout(0,2,8,8));var fields=new TreeMap<Integer,JTextField>();
        for(var line:lines) {
            JTextField field=new JTextField(line.price().toPlainString(),12);fields.put(line.itemId(),field);
            costs.add(new JLabel(line.product()+" — qty "+line.quantity()+", supplier unit ₹"+line.price()+"; landed unit ₹"));costs.add(field);
        }
        body.add(new JLabel("Confirm actual landed unit costs including allocated expenses (each ≥ supplier unit price)."),BorderLayout.NORTH);
        body.add(new JScrollPane(costs),BorderLayout.CENTER);
        body.add(Tables.buttons(Tables.button("Confirm full receipt",()->{
            var confirmed=new TreeMap<Integer,BigDecimal>();fields.forEach((item,field)->confirmed.put(item,new BigDecimal(field.getText().strip())));
            Tasks.run(body,()->{purchases.receive(id,confirmed);return true;},ok->{dialog.dispose();refresh();});
        }),Tables.button("Cancel",dialog::dispose)),BorderLayout.SOUTH);
        dialog.setContentPane(body);dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
}
