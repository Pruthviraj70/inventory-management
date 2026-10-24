package com.pruthviraj.inventory.service;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.CatalogDao;
import com.pruthviraj.inventory.model.Models.*;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogService {
    private final Database db;
    private final CatalogDao dao=new CatalogDao();
    public CatalogService(Database db) { this.db=db; }
    public List<Product> products(String search) throws SQLException { try(var c=db.open()) { return dao.products(c,search); } }
    public List<Supplier> suppliers(String search) throws SQLException { try(var c=db.open()) { return dao.suppliers(c,search); } }
    public List<Customer> customers(String search) throws SQLException { try(var c=db.open()) { return dao.customers(c,search); } }
    public List<SupplierStock> associations() throws SQLException { try(var c=db.open()) { return dao.associations(c); } }
    public int product(int id,String name) throws SQLException {
        if(id<0) throw new IllegalArgumentException("Invalid ID.");
        String valid=Rules.name(name); return db.transaction(c->dao.saveProduct(c,id,valid));
    }
    public int supplier(int id,String name,int days,BigDecimal minimum) throws SQLException {
        if(id<0 || days<0) throw new IllegalArgumentException("Supply time must be a nonnegative whole number.");
        String valid=Rules.name(name); BigDecimal amount=Rules.money(minimum);
        return db.transaction(c->dao.saveSupplier(c,id,valid,days,amount));
    }
    public int customer(int id,String name) throws SQLException {
        if(id<0) throw new IllegalArgumentException("Invalid ID.");
        String valid=Rules.name(name); return db.transaction(c->dao.saveCustomer(c,id,valid));
    }
    public void deleteProduct(int id) throws SQLException { Rules.positive(id); db.transaction(c->{dao.deleteProduct(c,id);return null;}); }
    public void deleteSupplier(int id) throws SQLException { Rules.positive(id); db.transaction(c->{dao.deleteSupplier(c,id);return null;}); }
    public void deleteCustomer(int id) throws SQLException { Rules.positive(id); db.transaction(c->{dao.deleteCustomer(c,id);return null;}); }
    public void associate(int item,int supplier) throws SQLException {
        Rules.positive(item); Rules.positive(supplier); db.transaction(c->{dao.associate(c,item,supplier);return null;});
    }
    public void removeAssociation(int item,int supplier) throws SQLException {
        Rules.positive(item); Rules.positive(supplier); db.transaction(c->{dao.removeAssociation(c,item,supplier);return null;});
    }
}
