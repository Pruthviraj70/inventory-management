package com.pruthviraj.inventory.demo;

import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Fixed, readable demo specification. validate() simulates everything before database writes. */
public final class DemoPlan {
    public static final String LEGACY_PREFIX="DEMO - ";
    public record SupplierSpec(String name,int days,BigDecimal minimum,List<String> products) { }
    public record Line(String product,int quantity,BigDecimal price,BigDecimal landed) { }
    public record Order(String key,String supplier,LocalDateTime ordered,LocalDateTime arrived,String status,List<Line> lines) { }
    public record Sold(String product,int quantity,BigDecimal price) { }
    public record SaleSpec(String key,String customer,LocalDateTime date,BigDecimal markup,List<Sold> lines,BigDecimal total) { }
    public record Expected(Map<String,Integer> remaining,Map<String,Integer> stocks,Map<String,Integer> supplierStocks,
                           Map<String,Map<String,Integer>> saleSupplierQuantities) { }
    public final List<String> products=List.of("Blue Ball Pen","HB Pencil","A5 Notebook","Geometry Box","Desk Stapler","A4 Paper Ream","Glue Stick","Eraser","Highlighter","Brown Packing Tape");
    public final List<SupplierSpec> suppliers=List.of(
        supplier("Campus Stationery Wholesale",2,"500","Blue Ball Pen","HB Pencil","A5 Notebook","Geometry Box","Eraser"),
        supplier("City Paper and Office",3,"800","A5 Notebook","Desk Stapler","A4 Paper Ream","Highlighter"),
        supplier("Scholars School Supplies",4,"600","Geometry Box","Highlighter","Eraser"),
        supplier("Metro Shop Essentials",1,"300","Glue Stick","Brown Packing Tape"));
    public final List<String> customers=List.of("Walk-in Customer","Aditi Rao","Rahul Shah","Meera Joshi","Sunrise Tuition Centre","Kavya Desai");
    public final List<Order> orders=List.of(
        new Order("R1","Campus Stationery Wholesale",date(1,9),date(3,10),"RECEIVED",List.of(line("Blue Ball Pen",40,"8","10"),line("HB Pencil",30,"4","5"),line("A5 Notebook",5,"90","100"),line("Geometry Box",3,"90","100"),line("Eraser",20,"3","4"))),
        new Order("R2","City Paper and Office",date(2,9),date(5,10),"RECEIVED",List.of(line("A5 Notebook",6,"100","110"),line("Desk Stapler",8,"80","90"),line("A4 Paper Ream",6,"240","250"))),
        new Order("R3","Campus Stationery Wholesale",date(6,9),date(8,10),"RECEIVED",List.of(line("Blue Ball Pen",25,"9","11"),line("A5 Notebook",4,"110","120"),line("HB Pencil",20,"5","6"))),
        new Order("R4","Scholars School Supplies",date(5,9),date(9,10),"RECEIVED",List.of(line("Geometry Box",5,"120","130"),line("Highlighter",12,"25","30"),line("Eraser",15,"4","5"))),
        new Order("R5","Metro Shop Essentials",date(9,9),date(10,10),"RECEIVED",List.of(line("Glue Stick",10,"25","28"),line("Brown Packing Tape",8,"35","40"))),
        new Order("P6","City Paper and Office",date(21,9),null,"PENDING",List.of(line("A4 Paper Ream",5,"240",null),line("Highlighter",10,"25",null))),
        new Order("P7","Scholars School Supplies",date(22,9),null,"PENDING",List.of(line("Geometry Box",5,"120",null),line("Highlighter",10,"25",null))),
        new Order("C8","Metro Shop Essentials",date(20,9),null,"CANCELLED",List.of(line("Glue Stick",12,"25",null),line("Brown Packing Tape",10,"35",null))));
    public final List<SaleSpec> sales=List.of(
        sale("S1","Walk-in Customer",11,"174.00",sold("Blue Ball Pen",10,"12"),sold("HB Pencil",5,"6"),sold("Eraser",5,"4.80")),
        sale("S2","Aditi Rao",12,"960.00",sold("A5 Notebook",8,"120")),
        sale("S3","Rahul Shah",13,"650.00",sold("Geometry Box",5,"130")),
        sale("S4","Meera Joshi",14,"750.00",sold("Blue Ball Pen",50,"12"),sold("Eraser",30,"5")),
        sale("S5","Sunrise Tuition Centre",16,"1696.80",sold("Desk Stapler",7,"108"),sold("A4 Paper Ream",2,"300"),sold("Glue Stick",3,"33.60"),sold("Brown Packing Tape",2,"48"),sold("Highlighter",4,"36")));
    private static BigDecimal money(String s) {return Rules.money(new BigDecimal(s));}
    private static SupplierSpec supplier(String name,int days,String minimum,String... products) {return new SupplierSpec(name,days,money(minimum),List.of(products));}
    private static Line line(String product,int qty,String price,String landed) {return new Line(product,qty,money(price),landed==null?null:money(landed));}
    private static Sold sold(String product,int qty,String price) {return new Sold(product,qty,money(price));}
    private static SaleSpec sale(String key,String customer,int day,String total,Sold... lines) {return new SaleSpec(key,customer,date(day,16),money("20"),List.of(lines),money(total));}
    private static LocalDateTime date(int day,int hour) {return LocalDateTime.of(2026,10,day,hour,0);}
    public static String name(String suffix) {return suffix;}
    public static String pair(String first,String second) {return first+" / "+second;}
    public SupplierSpec supplier(String name) {return suppliers.stream().filter(s->s.name().equals(name)).findFirst().orElseThrow();}
    public static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}

    public Expected validate() {
        require(new HashSet<>(products).size()==10 && new HashSet<>(customers).size()==6,"Duplicate demo names.");
        products.forEach(p->Rules.name(name(p)));customers.forEach(c->Rules.name(name(c)));
        require(suppliers.stream().map(SupplierSpec::name).distinct().count()==4,"Duplicate suppliers.");
        var remaining=new LinkedHashMap<String,Integer>();var supplierStocks=new TreeMap<String,Integer>();
        for(var supplier:suppliers) {
            Rules.name(name(supplier.name()));Rules.money(supplier.minimum());require(supplier.days()>=0,"Invalid supply time.");
            require(new HashSet<>(supplier.products()).size()==supplier.products().size(),"Duplicate association.");
            for(String product:supplier.products()) {
                require(products.contains(product),"Unknown associated product.");supplierStocks.put(pair(product,supplier.name()),0);
            }
        }
        require(orders.stream().map(Order::key).distinct().count()==8,"Duplicate orders.");
        require(orders.stream().filter(o->o.status().equals("RECEIVED")).count()==5 && orders.stream().filter(o->o.status().equals("PENDING")).count()==2 && orders.stream().filter(o->o.status().equals("CANCELLED")).count()==1,"Invalid status mix.");
        for(var order:orders) {
            var supplier=supplier(order.supplier());Rules.optionalDate(order.ordered());require(!order.lines().isEmpty(),"Empty order.");
            require(order.lines().stream().map(Line::product).distinct().count()==order.lines().size(),"Duplicate purchase line.");
            BigDecimal subtotal=BigDecimal.ZERO;
            for(var line:order.lines()) {
                Rules.positive(line.quantity());Rules.money(line.price());require(supplier.products().contains(line.product()),"Missing supplier-product association.");
                subtotal=subtotal.add(line.price().multiply(BigDecimal.valueOf(line.quantity())));
                boolean received=order.status().equals("RECEIVED");
                require(received==(line.landed()!=null),"Invalid landed cost nullability.");
                if(received)require(Rules.money(line.landed()).compareTo(line.price())>=0,"Landed cost below goods price.");
                remaining.put(pair(order.key(),line.product()),received?line.quantity():0);
            }
            require(subtotal.compareTo(supplier.minimum())>=0,"Goods alone must meet supplier minimum.");Rules.money(subtotal);
            if(order.status().equals("RECEIVED"))require(order.arrived()!=null && !order.arrived().isBefore(order.ordered().plusDays(supplier.days())),"Invalid arrival/lead time.");
            else require(order.arrived()==null,"Unreceived order has arrival.");
        }
        var saleQuantities=new LinkedHashMap<String,Map<String,Integer>>();LocalDateTime previous=null;
        for(var sale:sales) {
            require(customers.contains(sale.customer()) && !sale.lines().isEmpty(),"Invalid sale/customer.");Rules.markup(sale.markup());
            require(previous==null || sale.date().isAfter(previous),"Sales must be chronological.");previous=sale.date();
            var quantities=new TreeMap<String,Integer>();BigDecimal total=BigDecimal.ZERO;
            require(sale.lines().stream().map(Sold::product).distinct().count()==sale.lines().size(),"Duplicate cart product.");
            for(var sold:sale.lines()) {
                require(products.contains(sold.product()),"Unknown sale product.");Rules.positive(sold.quantity());
                var eligible=orders.stream().filter(o->o.status().equals("RECEIVED") && !o.arrived().isAfter(sale.date())).sorted(Comparator.comparing(Order::arrived).thenComparing(Order::key)).toList();
                var batches=new ArrayList<Batch>();int productIndex=products.indexOf(sold.product())+1;
                for(var order:eligible)for(var line:order.lines())if(line.product().equals(sold.product()) && remaining.get(pair(order.key(),line.product()))>0)
                    batches.add(new Batch(orders.indexOf(order)+1,productIndex,suppliers.indexOf(supplier(order.supplier()))+1,remaining.get(pair(order.key(),line.product())),line.landed()));
                // These indices identify in-memory plan entries only; database IDs always come from generated keys.
                var priced=Pricing.allocate(productIndex,sold.quantity(),sale.markup(),batches);
                require(priced.unitPrice().compareTo(sold.price())==0,"Unexpected simulated price: "+sale.key()+" / "+sold.product());
                total=total.add(priced.total());
                for(var allocation:priced.allocations()) {
                    Order order=orders.get(allocation.batch().orderId()-1);String key=pair(order.key(),sold.product());
                    remaining.put(key,remaining.get(key)-allocation.quantity());
                    quantities.merge(pair(sold.product(),order.supplier()),allocation.quantity(),Integer::sum);
                }
            }
            require(total.compareTo(sale.total())==0,"Unexpected simulated cart total.");saleQuantities.put(sale.key(),Map.copyOf(quantities));
        }
        var stocks=new TreeMap<String,Integer>();products.forEach(p->stocks.put(p,0));
        for(var order:orders)for(var line:order.lines()) {
            int qty=remaining.get(pair(order.key(),line.product()));stocks.merge(line.product(),qty,Integer::sum);supplierStocks.merge(pair(line.product(),order.supplier()),qty,Integer::sum);
        }
        Map<String,Integer> expected=Map.of("Blue Ball Pen",5,"HB Pencil",45,"A5 Notebook",7,"Geometry Box",3,"Desk Stapler",1,"A4 Paper Ream",4,"Glue Stick",7,"Eraser",0,"Highlighter",8,"Brown Packing Tape",6);
        require(stocks.equals(expected),"Unexpected final demo stock.");
        return new Expected(Map.copyOf(remaining),Map.copyOf(stocks),Map.copyOf(supplierStocks),Map.copyOf(saleQuantities));
    }
}
