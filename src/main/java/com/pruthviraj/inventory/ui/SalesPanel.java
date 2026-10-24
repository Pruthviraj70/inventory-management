package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

final class SalesPanel extends JPanel {
    private final CatalogService catalog;
    private final SaleService sales;
    private final JComboBox<Customer> customers=new JComboBox<>();
    private final JComboBox<Product> products=new JComboBox<>();
    private final JTextField quantity=new JTextField("1",6), markup=new JTextField("20.00",7);
    private final JTable table=Tables.table("Product ID","Product","Quantity","Selling unit price (₹)","Line total (₹)");
    private final JLabel total=new JLabel("Cart total: ₹0.00");
    private final Map<Integer,Integer> cart=new TreeMap<>();
    private final Map<Integer,String> names=new HashMap<>();
    private Quote quote;
    private boolean previewRunning;
    private final javax.swing.Timer previewTimer;
    SalesPanel(CatalogService catalog,SaleService sales) {
        super(new BorderLayout(8,8));this.catalog=catalog;this.sales=sales;
        previewTimer=new javax.swing.Timer(350,e->preview());previewTimer.setRepeats(false);
        JPanel top=new JPanel(new GridLayout(0,1));
        top.add(Tables.buttons(new JLabel("Customer"),customers,Tables.button("Refresh choices",this::refresh)));
        top.add(Tables.buttons(new JLabel("Product"),products,new JLabel("Quantity"),quantity,Tables.button("Add / set quantity",this::setQuantity),Tables.button("Remove selected",()->{cart.remove(Tables.id(table));invalidateQuote();}),new JLabel("Markup %"),markup));
        add(top,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);
        add(Tables.buttons(total,Tables.button("Refresh preview",this::preview),Tables.button("Save sale",this::save),Tables.button("Clear cart",()->{if(Tables.confirm(this,"Clear this unsaved cart?")){cart.clear();invalidateQuote();}})),BorderLayout.SOUTH);
        markup.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { invalidateQuote(); }
            public void removeUpdate(DocumentEvent e) { invalidateQuote(); }
            public void changedUpdate(DocumentEvent e) { invalidateQuote(); }
        });
        table.getSelectionModel().addListSelectionListener(e->{
            if(e.getValueIsAdjusting() || table.getSelectedRow()<0) return;
            int item=Tables.id(table);quantity.setText(""+cart.get(item));
            for(int i=0;i<products.getItemCount();i++) if(products.getItemAt(i).id()==item) {products.setSelectedIndex(i);break;}
        });
    }
    private record Choices(List<Customer> customers,List<Product> products) { }
    void refresh() {
        Integer customer=customers.getSelectedItem() instanceof Customer c?c.id():null;
        Integer product=products.getSelectedItem() instanceof Product p?p.id():null;
        Tasks.run(this,()->new Choices(catalog.customers(""),catalog.products("")),data->{
            customers.removeAllItems();products.removeAllItems();
            for(var c:data.customers) {customers.addItem(c);if(Objects.equals(customer,c.id())) customers.setSelectedItem(c);}
            for(var p:data.products) {products.addItem(p);names.put(p.id(),p.name());if(Objects.equals(product,p.id())) products.setSelectedItem(p);}
            if(!cart.isEmpty()) invalidateQuote();
        });
    }
    private void setQuantity() {
        Product p=(Product)products.getSelectedItem();if(p==null) throw new IllegalArgumentException("Add products and receive stock first.");
        int qty=Rules.positive(Integer.parseInt(quantity.getText().strip()));cart.put(p.id(),qty);names.put(p.id(),p.name());invalidateQuote();
    }
    private void invalidateQuote() {
        quote=null;render(); if(!cart.isEmpty()) previewTimer.restart();
    }
    private void render() {
        Tables.rows(table,cart.entrySet().stream().map(entry->{
            PricedProduct priced=quote==null?null:quote.products().stream().filter(p->p.itemId()==entry.getKey()).findFirst().orElse(null);
            return new Object[]{entry.getKey(),names.get(entry.getKey()),entry.getValue(),priced==null?"…":priced.unitPrice(),priced==null?"…":priced.total()};
        }).toArray(Object[][]::new));
        total.setText(cart.isEmpty()?"Cart total: ₹0.00":quote==null?"Preview pending — changes are unsaved":"Cart total: ₹"+quote.total());
    }
    private void preview() {
        previewTimer.stop();if(cart.isEmpty() || previewRunning || !isEnabled()) return;
        final BigDecimal percent;
        try {percent=Rules.markup(new BigDecimal(markup.getText().strip()));}
        catch(RuntimeException e) {quote=null;total.setText("Enter a valid markup (0–99999.99, up to 2 decimals).");return;}
        previewRunning=true;var snapshot=new TreeMap<>(cart);
        Tasks.run(this,()->sales.preview(snapshot,percent),value->{previewRunning=false;quote=value;render();},e->{previewRunning=false;quote=null;render();Tasks.error(this,e);});
    }
    private void save() {
        Customer customer=(Customer)customers.getSelectedItem();if(customer==null) throw new IllegalArgumentException("Select a customer. Add Walk-in Customer in Customers if needed.");
        if(quote==null) {preview();return;}
        BigDecimal percent=Rules.markup(new BigDecimal(markup.getText().strip()));
        var snapshot=new TreeMap<>(cart);Quote accepted=quote;previewTimer.stop();
        Tasks.run(this,()->sales.save(customer.id(),snapshot,percent,accepted),id->{cart.clear();quote=null;render();JOptionPane.showMessageDialog(this,"Sale #"+id+" saved.");},e->{
            if(e instanceof PriceChangedException changed) {
                quote=changed.revised();render();
                String details=quote.products().stream().map(p->names.get(p.itemId())+": "+p.quantity()+" × ₹"+p.unitPrice()+" = ₹"+p.total()).reduce("",(a,b)->a+b+"\n");
                if(Tables.confirm(this,"Prices changed. Review and accept:\n"+details+"New total: ₹"+quote.total()+"\nSave at these prices?")) save();
            } else Tasks.error(this,e);
        });
    }
}
