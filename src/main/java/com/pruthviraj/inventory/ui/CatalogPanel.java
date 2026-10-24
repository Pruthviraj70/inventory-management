package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.service.CatalogService;
import com.pruthviraj.inventory.model.Models.*;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

final class CatalogPanel extends JPanel {
    enum Kind { PRODUCTS, SUPPLIERS, CUSTOMERS }
    private final Kind kind;
    private final CatalogService service;
    private final JTextField search=new JTextField(24);
    private final JTable table;
    private List<Product> products=List.of();
    private List<Supplier> suppliers=List.of();
    private List<Customer> customers=List.of();
    CatalogPanel(Kind kind,CatalogService service) {
        super(new BorderLayout(8,8)); this.kind=kind;this.service=service;
        table=switch(kind) {
            case PRODUCTS -> Tables.table("ID","Product","Current stock");
            case SUPPLIERS -> Tables.table("ID","Supplier","Supply time (days)","Minimum goods total (₹)");
            case CUSTOMERS -> Tables.table("ID","Customer");
        };
        add(Tables.buttons(new JLabel("Search name"),search,Tables.button("Search / Refresh",this::refresh),Tables.button("Add",()->edit(false)),Tables.button("Edit",()->edit(true)),Tables.button("Delete",this::delete)),BorderLayout.NORTH);
        add(new JScrollPane(table),BorderLayout.CENTER);
        search.addActionListener(e->refresh());
        if(kind==Kind.PRODUCTS) add(Tables.buttons(Tables.button("Supplier stock breakdown",this::breakdown)),BorderLayout.SOUTH);
        if(kind==Kind.SUPPLIERS) add(Tables.buttons(Tables.button("Supplied products",this::associations)),BorderLayout.SOUTH);
    }
    void refresh() {
        String term=search.getText().strip();
        switch(kind) {
            case PRODUCTS -> Tasks.run(this,()->service.products(term),rows->{products=rows;Tables.rows(table,rows.stream().map(p->new Object[]{p.id(),p.name(),p.stock()}).toArray(Object[][]::new));});
            case SUPPLIERS -> Tasks.run(this,()->service.suppliers(term),rows->{suppliers=rows;Tables.rows(table,rows.stream().map(s->new Object[]{s.id(),s.name(),s.days(),s.minimum()}).toArray(Object[][]::new));});
            case CUSTOMERS -> Tasks.run(this,()->service.customers(term),rows->{customers=rows;Tables.rows(table,rows.stream().map(c->new Object[]{c.id(),c.name()}).toArray(Object[][]::new));});
        }
    }
    private void edit(boolean existing) {
        int index=existing?Tables.selected(table):-1;
        int id=existing?Tables.id(table):0;
        String old=existing?switch(kind) {case PRODUCTS->products.get(index).name();case SUPPLIERS->suppliers.get(index).name();case CUSTOMERS->customers.get(index).name();}:"";
        JTextField name=new JTextField(old,22), days=new JTextField("0"), minimum=new JTextField("0.00");
        if(existing && kind==Kind.SUPPLIERS) { days.setText(""+suppliers.get(index).days()); minimum.setText(suppliers.get(index).minimum().toPlainString()); }
        JPanel form=kind==Kind.SUPPLIERS?Tables.form("Name",name,"Supply time (days)",days,"Minimum goods total (₹)",minimum):Tables.form("Name",name);
        // A modal form stays open on validation or database failure.
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Record details",Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body=new JPanel(new BorderLayout(10,10));body.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));body.add(form,BorderLayout.CENTER);
        body.add(Tables.buttons(Tables.button("Save",()->{
            String value=name.getText();
            int lead=kind==Kind.SUPPLIERS?Integer.parseInt(days.getText().strip()):0;
            BigDecimal min=kind==Kind.SUPPLIERS?new BigDecimal(minimum.getText().strip()):BigDecimal.ZERO;
            Tasks.run(body,()->switch(kind) {case PRODUCTS->service.product(id,value);case SUPPLIERS->service.supplier(id,value,lead,min);case CUSTOMERS->service.customer(id,value);},saved->{dialog.dispose();refresh();});
        }),Tables.button("Cancel",dialog::dispose)),BorderLayout.SOUTH);
        dialog.setContentPane(body);dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private void delete() {
        int id=Tables.id(table);
        if(!Tables.confirm(this,"Delete selected record? Referenced records will be protected.")) return;
        Tasks.run(this,()->{switch(kind) {case PRODUCTS->service.deleteProduct(id);case SUPPLIERS->service.deleteSupplier(id);case CUSTOMERS->service.deleteCustomer(id);}return true;},ok->refresh());
    }
    private void breakdown() {
        int item=Tables.id(table);
        Tasks.run(this,service::associations,rows->{
            JTable detail=Tables.table("Supplier ID","Supplier","Stock in our shop");
            Tables.rows(detail,rows.stream().filter(s->s.itemId()==item).map(s->new Object[]{s.supplierId(),s.supplier(),s.stock()}).toArray(Object[][]::new));
            JOptionPane.showMessageDialog(this,new JScrollPane(detail),"Supplier stock for product #"+item,JOptionPane.PLAIN_MESSAGE);
        });
    }
    private record AssociationData(List<Product> products,List<SupplierStock> associations) { }
    private void associations() {
        int supplier=Tables.id(table);
        Tasks.run(this,()->new AssociationData(service.products(""),service.associations()),data->showAssociations(supplier,data));
    }
    private void showAssociations(int supplier,AssociationData data) {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Products supplied by #"+supplier,Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body=new JPanel(new BorderLayout(8,8));
        JTable pairs=Tables.table("Product ID","Product","Stock in our shop");
        Tables.rows(pairs,data.associations.stream().filter(s->s.supplierId()==supplier).map(s->new Object[]{s.itemId(),s.product(),s.stock()}).toArray(Object[][]::new));
        JComboBox<Product> choice=new JComboBox<>(data.products.toArray(Product[]::new));
        Runnable reload=()->Tasks.run(body,service::associations,rows->Tables.rows(pairs,rows.stream().filter(s->s.supplierId()==supplier).map(s->new Object[]{s.itemId(),s.product(),s.stock()}).toArray(Object[][]::new)));
        body.add(new JScrollPane(pairs),BorderLayout.CENTER);
        body.add(Tables.buttons(choice,Tables.button("Associate",()->{
            Product p=(Product)choice.getSelectedItem(); if(p==null) throw new IllegalArgumentException("Add a product first.");
            Tasks.run(body,()->{service.associate(p.id(),supplier);return true;},ok->reload.run());
        }),Tables.button("Remove selected association",()->{
            int item=Tables.id(pairs);
            if(Tables.confirm(body,"Remove this association? Purchase and sale references prevent removal.")) Tasks.run(body,()->{service.removeAssociation(item,supplier);return true;},ok->reload.run());
        }),Tables.button("Close",dialog::dispose)),BorderLayout.SOUTH);
        dialog.setContentPane(body);dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
}
