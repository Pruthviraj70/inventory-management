package com.pruthviraj.inventory.dao;

import com.pruthviraj.inventory.model.Models.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public final class PurchaseDao {
    public int create(Connection c, int supplier) throws SQLException {
        return Sql.insert(c,"INSERT INTO purchase_orders(supplier_id) VALUES (?)",supplier);
    }
    public int create(Connection c, int supplier, LocalDateTime ordered) throws SQLException {
        if(ordered==null) return create(c,supplier);
        return Sql.insert(c,"INSERT INTO purchase_orders(supplier_id,order_datetime) VALUES (?,?)",supplier,ordered);
    }
    public void addLine(Connection c, int order, int supplier, PurchaseInput line) throws SQLException {
        Sql.update(c,"INSERT INTO purchase_order_items(order_id,item_id,supplier_id,quantity,order_price) VALUES (?,?,?,?,?)",order,line.itemId(),supplier,line.quantity(),line.price());
    }
    public List<Purchase> list(Connection c, LocalDateTime from, LocalDateTime until) throws SQLException {
        var rows=new ArrayList<Purchase>();
        try(var p=c.prepareStatement("SELECT po.order_id,po.supplier_id,s.name,po.status,po.order_datetime,po.arrived_datetime FROM purchase_orders po JOIN suppliers s ON s.supplier_id=po.supplier_id WHERE po.order_datetime>=? AND po.order_datetime<? ORDER BY po.order_id DESC")) {
            Sql.bind(p,from,until); try(var r=p.executeQuery()) {
                while(r.next()) rows.add(new Purchase(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getObject(5,LocalDateTime.class),r.getObject(6,LocalDateTime.class)));
            }
        } return rows;
    }
    public List<PurchaseLine> lines(Connection c, int order) throws SQLException {
        var rows=new ArrayList<PurchaseLine>();
        try(var p=c.prepareStatement("SELECT i.order_id,i.item_id,i.supplier_id,p.name,i.quantity,i.order_price,i.landed_unit_cost,i.remaining_quantity FROM purchase_order_items i JOIN products p ON p.item_id=i.item_id WHERE i.order_id=? ORDER BY i.item_id")) {
            p.setInt(1,order); try(var r=p.executeQuery()) {
                while(r.next()) rows.add(new PurchaseLine(r.getInt(1),r.getInt(2),r.getInt(3),r.getString(4),r.getInt(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getInt(8)));
            }
        } return rows;
    }
    public LocalDateTime lockPending(Connection c, int order) throws SQLException {
        try(var p=c.prepareStatement("SELECT status,order_datetime FROM purchase_orders WHERE order_id=? FOR UPDATE")) {
            p.setInt(1,order); try(var r=p.executeQuery()) {
                if(!r.next() || !"PENDING".equals(r.getString(1))) throw new IllegalArgumentException("Only a pending order can be received or cancelled.");
                return r.getObject(2,LocalDateTime.class);
            }
        }
    }
    public void receiveLine(Connection c, PurchaseLine line, BigDecimal cost) throws SQLException {
        Sql.one(Sql.update(c,"UPDATE purchase_order_items SET landed_unit_cost=?,remaining_quantity=quantity WHERE order_id=? AND item_id=? AND supplier_id=?",cost,line.orderId(),line.itemId(),line.supplierId()));
    }
    public void receiveHeader(Connection c,int order) throws SQLException {
        Sql.one(Sql.update(c,"UPDATE purchase_orders SET status='RECEIVED',arrived_datetime=CURRENT_TIMESTAMP(6) WHERE order_id=? AND status='PENDING'",order));
    }
    public void receiveHeader(Connection c,int order,LocalDateTime arrived) throws SQLException {
        if(arrived==null) {receiveHeader(c,order);return;}
        Sql.one(Sql.update(c,"UPDATE purchase_orders SET status='RECEIVED',arrived_datetime=? WHERE order_id=? AND status='PENDING'",arrived,order));
    }
    public void cancel(Connection c,int order) throws SQLException {
        Sql.one(Sql.update(c,"UPDATE purchase_orders SET status='CANCELLED' WHERE order_id=? AND status='PENDING'",order));
    }
    public List<Batch> available(Connection c,int item,boolean lock) throws SQLException {
        var rows=new ArrayList<Batch>();
        String sql="SELECT i.order_id,i.item_id,i.supplier_id,i.remaining_quantity,i.landed_unit_cost FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id AND o.supplier_id=i.supplier_id WHERE i.item_id=? AND o.status='RECEIVED' AND i.remaining_quantity>0 ORDER BY o.arrived_datetime,o.order_id";
        try(var p=c.prepareStatement(sql+(lock?" FOR UPDATE":""))) {
            p.setInt(1,item); try(var r=p.executeQuery()) {
                while(r.next()) rows.add(new Batch(r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getBigDecimal(5)));
            }
        } return rows;
    }
    public LocalDateTime arrival(Connection c,int order) throws SQLException {
        try(var p=c.prepareStatement("SELECT arrived_datetime FROM purchase_orders WHERE order_id=?")) {
            p.setInt(1,order);try(var r=p.executeQuery()) {
                if(!r.next() || r.getObject(1)==null) throw new IllegalArgumentException("Received purchase required.");
                return r.getObject(1,LocalDateTime.class);
            }
        }
    }
    public void consume(Connection c,Allocation a) throws SQLException {
        var b=a.batch();
        Sql.one(Sql.update(c,"UPDATE purchase_order_items SET remaining_quantity=remaining_quantity-? WHERE order_id=? AND item_id=? AND supplier_id=? AND remaining_quantity>=?",a.quantity(),b.orderId(),b.itemId(),b.supplierId(),a.quantity()));
    }
}
