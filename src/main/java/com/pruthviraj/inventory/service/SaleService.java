package com.pruthviraj.inventory.service;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SaleService {
    private final Database db;
    private final CatalogDao catalog=new CatalogDao();
    private final PurchaseDao purchases=new PurchaseDao();
    private final SaleDao sales=new SaleDao();
    public SaleService(Database db) { this.db=db; }
    private TreeMap<Integer,Integer> validate(Map<Integer,Integer> cart) {
        if(cart==null || cart.isEmpty()) throw new IllegalArgumentException("Add at least one product to the cart.");
        var sorted=new TreeMap<Integer,Integer>();
        cart.forEach((id,qty)->{Rules.positive(id);Rules.positive(qty);sorted.put(id,qty);}); return sorted;
    }
    private Quote quote(Connection c,Map<Integer,Integer> cart,BigDecimal markup,boolean lock) throws SQLException {
        var products=new ArrayList<PricedProduct>(); BigDecimal total=BigDecimal.ZERO;
        for(var entry:cart.entrySet()) {
            var priced=Pricing.allocate(entry.getKey(),entry.getValue(),markup,purchases.available(c,entry.getKey(),lock));
            products.add(priced); total=total.add(priced.total());
        } return new Quote(products,Rules.money(total));
    }
    public Quote preview(Map<Integer,Integer> cart,BigDecimal markup) throws SQLException {
        var sorted=validate(cart); var percent=Rules.markup(markup);
        try(var c=db.open()) { return quote(c,sorted,percent,false); }
    }
    public Quote preview(Connection c,Map<Integer,Integer> cart,BigDecimal markup) throws SQLException {
        return quote(c,validate(cart),Rules.markup(markup),false);
    }
    public int save(int customer,Map<Integer,Integer> cart,BigDecimal markup,Quote accepted) throws SQLException {
        return db.transaction(c->save(c,customer,cart,markup,accepted,null));
    }
    /** Caller owns the transaction; null date preserves the regular UI behavior. */
    public int save(Connection c,int customer,Map<Integer,Integer> cart,BigDecimal markup,Quote accepted,LocalDateTime date) throws SQLException {
        Rules.transaction(c); Rules.optionalDate(date); Rules.positive(customer);
        var sorted=validate(cart); var percent=Rules.markup(markup);
        catalog.requireCustomer(c,customer);
        for(int item:sorted.keySet()) catalog.lockProduct(c,item);
        for(int item:sorted.keySet()) catalog.lockAssociations(c,item);
        Quote actual=quote(c,sorted,percent,true);
        if(date!=null) {
            for(var product:actual.products()) for(var allocation:product.allocations()) {
                if(purchases.arrival(c,allocation.batch().orderId()).isAfter(date))
                    throw new IllegalArgumentException("Sale date cannot precede a consumed purchase arrival.");
            }
        }
        if(!actual.samePrices(accepted)) throw new PriceChangedException(actual);
        int id=sales.create(c,customer,percent,actual.total(),date);
        for(var product:actual.products()) {
            var supplierQuantities=new TreeMap<Integer,Integer>();
            for(var allocation:product.allocations()) {
                purchases.consume(c,allocation);
                supplierQuantities.merge(allocation.batch().supplierId(),allocation.quantity(),Math::addExact);
            }
            for(var entry:supplierQuantities.entrySet()) {
                sales.addLine(c,id,product.itemId(),entry.getKey(),entry.getValue(),product.unitPrice());
                catalog.changeStock(c,product.itemId(),entry.getKey(),-entry.getValue());
            }
        }
        return id;
    }
    public List<Sale> list(LocalDateTime from,LocalDateTime until) throws SQLException { try(var c=db.open()) { return sales.list(c,from,until); } }
    public List<SaleLine> lines(int id) throws SQLException { try(var c=db.open()) { return sales.lines(c,id); } }
}
