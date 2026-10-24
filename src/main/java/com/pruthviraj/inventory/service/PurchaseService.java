package com.pruthviraj.inventory.service;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public final class PurchaseService {
    private final Database db;
    private final PurchaseDao dao=new PurchaseDao();
    private final CatalogDao catalog=new CatalogDao();
    public PurchaseService(Database db) { this.db=db; }
    public List<PurchaseInput> merge(List<PurchaseInput> lines) {
        if(lines==null || lines.isEmpty()) throw new IllegalArgumentException("Add at least one purchase line.");
        var merged=new TreeMap<Integer,PurchaseInput>();
        for(var line:lines) {
            Rules.positive(line.itemId()); Rules.positive(line.quantity()); BigDecimal price=Rules.money(line.price());
            var old=merged.get(line.itemId());
            if(old!=null && old.price().compareTo(price)!=0) throw new IllegalArgumentException("Duplicate product must use the same supplier unit price; remove the old line to change its price.");
            int quantity=old==null?line.quantity():Math.addExact(old.quantity(),line.quantity());
            merged.put(line.itemId(),new PurchaseInput(line.itemId(),quantity,price));
        } return List.copyOf(merged.values());
    }
    public int create(int supplier,List<PurchaseInput> inputs) throws SQLException {
        return db.transaction(c->create(c,supplier,inputs,null));
    }
    /** Caller owns the transaction. A null datetime keeps the normal database timestamp. */
    public int create(java.sql.Connection c,int supplier,List<PurchaseInput> inputs,LocalDateTime ordered) throws SQLException {
        Rules.transaction(c); Rules.optionalDate(ordered); Rules.positive(supplier); var lines=merge(inputs);
        BigDecimal subtotal=Rules.money(lines.stream().map(l->l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add));
        var s=catalog.supplier(c,supplier);
        if(subtotal.compareTo(s.minimum())<0) throw new IllegalArgumentException("Goods subtotal must be at least ₹"+s.minimum()+". Extra costs do not count.");
        for(var line:lines) catalog.requireAssociation(c,line.itemId(),supplier);
        int id=dao.create(c,supplier,ordered);
        for(var line:lines) dao.addLine(c,id,supplier,line);
        return id;
    }
    public List<Purchase> list(LocalDateTime from,LocalDateTime until) throws SQLException { try(var c=db.open()) { return dao.list(c,from,until); } }
    public List<PurchaseLine> lines(int order) throws SQLException { try(var c=db.open()) { return dao.lines(c,order); } }
    public void cancel(int order) throws SQLException {
        db.transaction(c->{cancel(c,order);return null;});
    }
    public void cancel(java.sql.Connection c,int order) throws SQLException {
        Rules.transaction(c); Rules.positive(order); dao.lockPending(c,order); dao.cancel(c,order);
    }
    public void receive(int order,Map<Integer,BigDecimal> costs) throws SQLException {
        db.transaction(c->{receive(c,order,costs,null);return null;});
    }
    /** All receipt statements participate in the caller's existing transaction. */
    public void receive(java.sql.Connection c,int order,Map<Integer,BigDecimal> costs,LocalDateTime arrived) throws SQLException {
        Rules.transaction(c); Rules.optionalDate(arrived); Rules.positive(order);
        var confirmed=new TreeMap<Integer,BigDecimal>();
        costs.forEach((id,cost)->confirmed.put(id,Rules.money(cost)));
        LocalDateTime ordered=dao.lockPending(c,order);
        if(arrived!=null && arrived.isBefore(ordered)) throw new IllegalArgumentException("Arrival cannot be before the order date.");
        var lines=dao.lines(c,order);
        if(lines.isEmpty() || confirmed.size()!=lines.size()) throw new IllegalArgumentException("Confirm a landed unit cost for every line.");
        for(var line:lines) {
            var cost=confirmed.get(line.itemId());
            if(cost==null || cost.compareTo(line.price())<0) throw new IllegalArgumentException("Landed cost must be at least the supplier unit price for every line.");
        }
        for(var line:lines) catalog.lockProduct(c,line.itemId());
        for(var line:lines) catalog.lockAssociations(c,line.itemId());
        for(var line:lines) {
            dao.receiveLine(c,line,confirmed.get(line.itemId()));
            catalog.changeStock(c,line.itemId(),line.supplierId(),line.quantity());
        }
        dao.receiveHeader(c,order,arrived);
    }
}
