package com.pruthviraj.inventory.dao;

import com.pruthviraj.inventory.model.Models.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public final class SaleDao {
    public int create(Connection c,int customer,BigDecimal markup,BigDecimal total) throws SQLException {
        return Sql.insert(c,"INSERT INTO sales(consumer_id,markup_percent,cart_total) VALUES (?,?,?)",customer,markup,total);
    }
    public int create(Connection c,int customer,BigDecimal markup,BigDecimal total,LocalDateTime date) throws SQLException {
        if(date==null) return create(c,customer,markup,total);
        return Sql.insert(c,"INSERT INTO sales(consumer_id,markup_percent,cart_total,sale_datetime) VALUES (?,?,?,?)",customer,markup,total,date);
    }
    public void addLine(Connection c,int sale,int item,int supplier,int quantity,BigDecimal price) throws SQLException {
        Sql.update(c,"INSERT INTO sale_items(transaction_id,item_id,supplier_id,quantity,price_per_unit) VALUES (?,?,?,?,?)",sale,item,supplier,quantity,price);
    }
    public List<Sale> list(Connection c,LocalDateTime from,LocalDateTime until) throws SQLException {
        var rows=new ArrayList<Sale>();
        try(var p=c.prepareStatement("SELECT s.transaction_id,c.name,s.sale_datetime,s.markup_percent,s.cart_total FROM sales s JOIN customers c ON c.consumer_id=s.consumer_id WHERE s.sale_datetime>=? AND s.sale_datetime<? ORDER BY s.transaction_id DESC")) {
            Sql.bind(p,from,until); try(var r=p.executeQuery()) {
                while(r.next()) rows.add(new Sale(r.getInt(1),r.getString(2),r.getObject(3,LocalDateTime.class),r.getBigDecimal(4),r.getBigDecimal(5)));
            }
        } return rows;
    }
    public List<SaleLine> lines(Connection c,int sale) throws SQLException {
        var rows=new ArrayList<SaleLine>();
        try(var p=c.prepareStatement("SELECT i.item_id,i.supplier_id,p.name,s.name,i.quantity,i.price_per_unit FROM sale_items i JOIN products p ON p.item_id=i.item_id JOIN suppliers s ON s.supplier_id=i.supplier_id WHERE i.transaction_id=? ORDER BY i.item_id,i.supplier_id")) {
            p.setInt(1,sale); try(var r=p.executeQuery()) {
                while(r.next()) rows.add(new SaleLine(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5),r.getBigDecimal(6)));
            }
        } return rows;
    }
}
