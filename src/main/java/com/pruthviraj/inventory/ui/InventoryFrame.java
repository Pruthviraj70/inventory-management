package com.pruthviraj.inventory.ui;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.service.*;
import javax.swing.*;
import java.awt.*;

public final class InventoryFrame extends JFrame {
    public InventoryFrame(Database db) {
        super("Inventory Management — OOP + RDBMS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        var catalog=new CatalogService(db);var purchases=new PurchaseService(db);var sales=new SaleService(db);
        JTabbedPane tabs=new JTabbedPane();
        var products=new CatalogPanel(CatalogPanel.Kind.PRODUCTS,catalog);
        var suppliers=new CatalogPanel(CatalogPanel.Kind.SUPPLIERS,catalog);
        var customers=new CatalogPanel(CatalogPanel.Kind.CUSTOMERS,catalog);
        var purchase=new PurchasePanel(catalog,purchases);
        var selling=new SalesPanel(catalog,sales);
        var history=new HistoryPanel(purchases,sales);
        tabs.addTab("Products",products);tabs.addTab("Suppliers",suppliers);tabs.addTab("Customers",customers);
        tabs.addTab("Purchase Orders",purchase);tabs.addTab("Sales",selling);tabs.addTab("History",history);
        tabs.addChangeListener(e->{try {switch(tabs.getSelectedIndex()) {
            case 0->products.refresh();case 1->suppliers.refresh();case 2->customers.refresh();case 3->purchase.refresh();case 4->selling.refresh();case 5->history.refresh();
        }}catch(RuntimeException ex){Tasks.error(tabs,ex);}});
        JPanel body=new JPanel(new BorderLayout(8,8));body.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));body.add(tabs,BorderLayout.CENTER);
        body.add(new JLabel("Local inventory • ₹ INR • Stock changes only through full receipts and saved sales"),BorderLayout.SOUTH);
        setContentPane(body);setMinimumSize(new Dimension(1000,600));setSize(1200,720);setLocationRelativeTo(null);
        products.refresh();
    }
}
