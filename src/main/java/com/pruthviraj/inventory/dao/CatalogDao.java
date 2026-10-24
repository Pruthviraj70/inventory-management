package com.pruthviraj.inventory.dao;

import com.pruthviraj.inventory.model.Models.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

public final class CatalogDao {
    public List<Product> products(Connection c, String search) throws SQLException {
        var result = new ArrayList<Product>();
        try (var p = c.prepareStatement("SELECT item_id,name,stock FROM products WHERE LOCATE(?,name)>0 ORDER BY item_id")) {
            p.setString(1, search); try (var r = p.executeQuery()) {
                while (r.next()) result.add(new Product(r.getInt(1), r.getString(2), r.getInt(3)));
            }
        } return result;
    }
    public List<Supplier> suppliers(Connection c, String search) throws SQLException {
        var result = new ArrayList<Supplier>();
        try (var p = c.prepareStatement("SELECT supplier_id,name,supply_time_days,min_order_total FROM suppliers WHERE LOCATE(?,name)>0 ORDER BY supplier_id")) {
            p.setString(1, search); try (var r = p.executeQuery()) {
                while (r.next()) result.add(new Supplier(r.getInt(1), r.getString(2), r.getInt(3), r.getBigDecimal(4)));
            }
        } return result;
    }
    public List<Customer> customers(Connection c, String search) throws SQLException {
        var result = new ArrayList<Customer>();
        try (var p = c.prepareStatement("SELECT consumer_id,name FROM customers WHERE LOCATE(?,name)>0 ORDER BY consumer_id")) {
            p.setString(1, search); try (var r = p.executeQuery()) {
                while (r.next()) result.add(new Customer(r.getInt(1), r.getString(2)));
            }
        } return result;
    }
    public int saveProduct(Connection c, int id, String name) throws SQLException {
        if (id == 0) return Sql.insert(c, "INSERT INTO products(name) VALUES (?)", name);
        Sql.one(Sql.update(c, "UPDATE products SET name=? WHERE item_id=?", name, id)); return id;
    }
    public int saveSupplier(Connection c, int id, String name, int days, BigDecimal minimum) throws SQLException {
        if (id == 0) return Sql.insert(c, "INSERT INTO suppliers(name,supply_time_days,min_order_total) VALUES (?,?,?)", name, days, minimum);
        Sql.one(Sql.update(c, "UPDATE suppliers SET name=?,supply_time_days=?,min_order_total=? WHERE supplier_id=?", name, days, minimum, id)); return id;
    }
    public int saveCustomer(Connection c, int id, String name) throws SQLException {
        if (id == 0) return Sql.insert(c, "INSERT INTO customers(name) VALUES (?)", name);
        Sql.one(Sql.update(c, "UPDATE customers SET name=? WHERE consumer_id=?", name, id)); return id;
    }
    public void deleteProduct(Connection c, int id) throws SQLException { Sql.one(Sql.update(c,"DELETE FROM products WHERE item_id=?", id)); }
    public void deleteSupplier(Connection c, int id) throws SQLException { Sql.one(Sql.update(c,"DELETE FROM suppliers WHERE supplier_id=?", id)); }
    public void deleteCustomer(Connection c, int id) throws SQLException { Sql.one(Sql.update(c,"DELETE FROM customers WHERE consumer_id=?", id)); }
    public void associate(Connection c, int item, int supplier) throws SQLException {
        Sql.update(c,"INSERT INTO supplier_products(item_id,supplier_id) VALUES (?,?)", item, supplier);
    }
    public void removeAssociation(Connection c, int item, int supplier) throws SQLException {
        Sql.one(Sql.update(c,"DELETE FROM supplier_products WHERE item_id=? AND supplier_id=?", item, supplier));
    }
    public List<SupplierStock> associations(Connection c) throws SQLException {
        var result = new ArrayList<SupplierStock>();
        try (var p = c.prepareStatement("SELECT sp.item_id,sp.supplier_id,p.name,s.name,sp.supplier_specific_stock FROM supplier_products sp JOIN products p ON p.item_id=sp.item_id JOIN suppliers s ON s.supplier_id=sp.supplier_id ORDER BY sp.item_id,sp.supplier_id"); var r = p.executeQuery()) {
            while(r.next()) result.add(new SupplierStock(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5)));
        } return result;
    }
    public Supplier supplier(Connection c, int id) throws SQLException {
        try (var p = c.prepareStatement("SELECT name,supply_time_days,min_order_total FROM suppliers WHERE supplier_id=? FOR SHARE")) {
            p.setInt(1,id); try(var r=p.executeQuery()) {
                if(!r.next()) throw new IllegalArgumentException("Select an existing supplier.");
                return new Supplier(id,r.getString(1),r.getInt(2),r.getBigDecimal(3));
            }
        }
    }
    public void requireCustomer(Connection c, int id) throws SQLException {
        try(var p=c.prepareStatement("SELECT consumer_id FROM customers WHERE consumer_id=? FOR SHARE")) {
            p.setInt(1,id); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Select an existing customer."); }
        }
    }
    public void requireAssociation(Connection c, int item, int supplier) throws SQLException {
        try(var p=c.prepareStatement("SELECT item_id FROM supplier_products WHERE item_id=? AND supplier_id=? FOR SHARE")) {
            Sql.bind(p,item,supplier); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("This supplier does not supply the selected product."); }
        }
    }
    public void lockProduct(Connection c, int item) throws SQLException {
        try(var p=c.prepareStatement("SELECT stock FROM products WHERE item_id=? FOR UPDATE")) {
            p.setInt(1,item); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Product no longer exists."); }
        }
    }
    public void lockAssociations(Connection c, int item) throws SQLException {
        try(var p=c.prepareStatement("SELECT supplier_id FROM supplier_products WHERE item_id=? ORDER BY supplier_id FOR UPDATE")) {
            p.setInt(1,item); try(var r=p.executeQuery()) { while(r.next()) { /* Acquire locks in supplier order. */ } }
        }
    }
    public void changeStock(Connection c, int item, int supplier, int delta) throws SQLException {
        Sql.one(Sql.update(c,"UPDATE supplier_products SET supplier_specific_stock=supplier_specific_stock+? WHERE item_id=? AND supplier_id=? AND supplier_specific_stock+? >= 0",delta,item,supplier,delta));
        Sql.one(Sql.update(c,"UPDATE products SET stock=stock+? WHERE item_id=? AND stock+? >= 0",delta,item,delta));
    }
}
