package com.pruthviraj.inventory;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Appends uniquely named fixtures. NEVER clears a database. Requires an inventory_test_* schema. */
public final class DatabaseChecks {
    private final Database db;
    private final CatalogService catalog;
    private final PurchaseService purchases;
    private final SaleService sales;
    private int count;
    private final String tag="Test "+System.nanoTime()+" ";
    private static BigDecimal bd(String value) {return new BigDecimal(value);}
    private DatabaseChecks(Path path) {db=new Database(path);catalog=new CatalogService(db);purchases=new PurchaseService(db);sales=new SaleService(db);}
    private void check(boolean condition,String label) {if(!condition)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    @FunctionalInterface private interface Attempt {void run() throws Exception;}
    private void fails(Attempt work,String label) throws Exception {
        try {work.run();}catch(SQLException|IllegalArgumentException|ArithmeticException e){check(true,label);return;}throw new AssertionError(label);
    }
    private long scalar(String sql,Object... args) throws SQLException {
        try(var c=db.open();var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){r.next();return r.getLong(1);}}
    }
    private void sql(String sql,Object... args) throws SQLException {
        try(var c=db.open();var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}
    }
    private int order(int supplier,int item,int qty,String price,String cost) throws SQLException {
        int id=purchases.create(supplier,List.of(new PurchaseInput(item,qty,bd(price))));
        if(cost!=null)purchases.receive(id,Map.of(item,bd(cost)));return id;
    }
    private void invariant() throws SQLException {
        check(scalar("SELECT COUNT(*) FROM products p WHERE p.stock <> COALESCE((SELECT SUM(sp.supplier_specific_stock) FROM supplier_products sp WHERE sp.item_id=p.item_id),0) OR p.stock <> COALESCE((SELECT SUM(i.remaining_quantity) FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id WHERE i.item_id=p.item_id AND o.status='RECEIVED'),0)")==0,"all product stock invariants");
        check(scalar("SELECT COUNT(*) FROM supplier_products sp WHERE sp.supplier_specific_stock <> COALESCE((SELECT SUM(i.remaining_quantity) FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id WHERE i.item_id=sp.item_id AND i.supplier_id=sp.supplier_id AND o.status='RECEIVED'),0)")==0,"all supplier stock invariants");
    }
    private void run() throws Exception {
        try(var c=db.open()) {String name=c.getCatalog();if(name==null || !name.matches("inventory_test_[A-Za-z0-9_]+")) throw new IllegalArgumentException("Refusing verification outside an explicitly isolated inventory_test_* database.");check(true,"isolated test database guard");}
        int a=catalog.supplier(0,tag+"Supplier A",2,bd("0")), b=catalog.supplier(0,tag+"Supplier B",5,bd("0"));
        int customer=catalog.customer(0,tag+"Walk-in Customer"),item=catalog.product(0,tag+"FIFO product");
        catalog.product(item,tag+"FIFO product edited");catalog.supplier(a,tag+"Supplier A edited",3,bd("0"));catalog.customer(customer,tag+"Customer edited");
        check(catalog.products(tag+"FIFO product edited").size()==1,"product create/edit/search");
        check(catalog.suppliers(tag+"Supplier A edited").getFirst().days()==3,"supplier create/edit/search");
        check(catalog.customers(tag+"Customer edited").size()==1,"customer create/edit/search");
        var restarted=new CatalogService(new Database(Path.of(System.getProperty("inventory.test.config","config/test-database.properties"))));
        check(restarted.products(tag+"FIFO").getFirst().stock()==0,"persistence through fresh service and connections");
        int spareP=catalog.product(0,tag+"unused product"),spareS=catalog.supplier(0,tag+"unused supplier",0,bd("0")),spareC=catalog.customer(0,tag+"unused customer");
        catalog.associate(spareP,spareS);catalog.removeAssociation(spareP,spareS);catalog.deleteProduct(spareP);catalog.deleteSupplier(spareS);catalog.deleteCustomer(spareC);
        check(catalog.products(tag+"unused product").isEmpty(),"unreferenced records and association deleted");
        fails(()->catalog.product(0," "),"invalid product name rejected");fails(()->catalog.supplier(0,"bad",-1,bd("0")),"negative lead time rejected");
        catalog.associate(item,a);catalog.associate(item,b);
        fails(()->catalog.associate(item,a),"duplicate association rejected");fails(()->catalog.deleteProduct(item),"referenced product deletion blocked");fails(()->catalog.deleteSupplier(a),"referenced supplier deletion blocked");
        fails(()->purchases.create(a,List.of()),"empty order rejected");
        int other=catalog.product(0,tag+"not associated");
        fails(()->order(a,other,1,"10",null),"unassociated product purchase rejected");
        int minSupplier=catalog.supplier(0,tag+"Minimum supplier",1,bd("200"));catalog.associate(other,minSupplier);
        fails(()->order(minSupplier,other,1,"100","300"),"minimum considers goods only, not higher landed costs");
        int minimumOrder=order(minSupplier,other,2,"100",null);check(minimumOrder>0,"goods subtotal equal to minimum accepted");
        purchases.cancel(minimumOrder);check(scalar("SELECT stock FROM products WHERE item_id=?",other)==0,"cancellation leaves stock unchanged");
        fails(()->purchases.receive(minimumOrder,Map.of(other,bd("100"))),"cancelled order cannot be received");fails(()->purchases.cancel(minimumOrder),"order cannot be cancelled twice");
        int first=order(a,item,2,"90",null);check(scalar("SELECT stock FROM products WHERE item_id=?",item)==0,"pending creation leaves stock unchanged");
        fails(()->purchases.receive(first,Map.of(item,bd("89"))),"landed cost below supplier price rejected");
        fails(()->purchases.receive(first,Map.of()),"missing landed costs rejected");
        purchases.receive(first,Map.of(item,bd("100")));
        fails(()->purchases.receive(first,Map.of(item,bd("100"))),"receipt exactly once");fails(()->purchases.cancel(first),"received order immutable");
        int second=order(b,item,1,"100","110"),third=order(a,item,3,"120","130");
        // Make arrival priority explicitly different from order creation priority.
        sql("UPDATE purchase_orders SET order_datetime='2020-01-01',arrived_datetime='2020-01-02' WHERE order_id=?",first);
        sql("UPDATE purchase_orders SET order_datetime='2020-01-01',arrived_datetime='2020-01-03' WHERE order_id=?",second);
        sql("UPDATE purchase_orders SET order_datetime='2020-01-01',arrived_datetime='2020-01-04' WHERE order_id=?",third);
        var two=sales.preview(Map.of(item,3),bd("20"));check(two.products().getFirst().unitPrice().compareTo(bd("120"))==0,"database pricing example 100/110 → 120");
        var cross=sales.preview(Map.of(item,4),bd("20"));check(cross.products().getFirst().unitPrice().compareTo(bd("130"))==0,"database pricing example 100/130 → 130");
        int sale=sales.save(customer,Map.of(item,4),bd("20"),cross);
        var saleLines=sales.lines(sale);check(saleLines.size()==2,"same supplier purchases aggregated into one sale line");
        check(saleLines.stream().allMatch(l->l.price().compareTo(bd("130"))==0),"one shared product price across suppliers");
        check(saleLines.stream().filter(l->l.supplierId()==a).findFirst().orElseThrow().quantity()==3,"supplier A aggregated quantity");
        check(scalar("SELECT remaining_quantity FROM purchase_order_items WHERE order_id=?",first)==0 && scalar("SELECT remaining_quantity FROM purchase_order_items WHERE order_id=?",second)==0 && scalar("SELECT remaining_quantity FROM purchase_order_items WHERE order_id=?",third)==2,"individual FIFO remaining quantities updated");
        var last=sales.preview(Map.of(item,1),bd("20"));check(last.products().getFirst().unitPrice().compareTo(bd("156"))==0,"database pricing example only 130 → 156");
        check(scalar("SELECT cart_total FROM sales WHERE transaction_id=?",sale)==520,"cart total equals rounded unit price × quantity");
        fails(()->catalog.deleteCustomer(customer),"referenced customer deletion blocked");fails(()->catalog.removeAssociation(item,a),"referenced association deletion blocked");
        long before=scalar("SELECT COUNT(*) FROM sales");
        fails(()->sales.save(customer,Map.of(item,500),bd("20"),last),"save rechecks insufficient stock");
        check(scalar("SELECT COUNT(*) FROM sales")==before,"insufficient sale has no header");
        // Stale quote: another sale consumes the cheap first batch, then save must request acceptance.
        int changing=catalog.product(0,tag+"changing costs");catalog.associate(changing,a);
        order(a,changing,1,"100","100");order(a,changing,1,"130","130");
        var stale=sales.preview(Map.of(changing,1),bd("20"));sales.save(customer,Map.of(changing,1),bd("20"),stale);
        try{sales.save(customer,Map.of(changing,1),bd("20"),stale);throw new AssertionError("stale quote accepted");}
        catch(PriceChangedException e){check(e.revised().total().compareTo(bd("156"))==0,"changed price requires revised acceptance");sales.save(customer,Map.of(changing,1),bd("20"),e.revised());}
        // Direct SQL checks verify protections independently of service validation.
        fails(()->sql("INSERT INTO purchase_order_items(order_id,item_id,supplier_id,quantity,order_price) VALUES (?,?,?,?,?)",first,other,b,1,bd("1")),"SQL product-supplier and order-supplier foreign keys enforced");
        fails(()->sql("INSERT INTO purchase_order_items(order_id,item_id,supplier_id,quantity,order_price) VALUES (?,?,?,?,?)",first,item,b,1,bd("1")),"SQL order-supplier mismatch blocked for existing pair");
        fails(()->sql("INSERT INTO purchase_order_items(order_id,item_id,supplier_id,quantity,order_price) VALUES (?,?,?,?,?)",first,other,a,1,bd("1")),"SQL missing supplier-product pair blocked with matching order supplier");
        fails(()->sql("UPDATE purchase_order_items SET remaining_quantity=quantity+1 WHERE order_id=?",third),"SQL remaining quantity bounds");
        fails(()->sql("UPDATE products SET stock=-1 WHERE item_id=?",item),"SQL negative stock check");
        // Inject a counter mismatch in this isolated fixture so the SECOND product stock update fails.
        // Earlier line writes must roll back too; restore the fixture counter in finally.
        int rollbackItem=catalog.product(0,tag+"rollback second product");catalog.associate(rollbackItem,a);order(a,rollbackItem,2,"10","10");
        var rollbackCart=Map.of(item,1,rollbackItem,1);var rollbackQuote=sales.preview(rollbackCart,bd("20"));
        before=scalar("SELECT COUNT(*) FROM sales");
        sql("UPDATE products SET stock=0 WHERE item_id=?",rollbackItem);
        try {fails(()->sales.save(customer,rollbackCart,bd("20"),rollbackQuote),"failure after earlier writes rolls back sale");}
        finally {sql("UPDATE products SET stock=2 WHERE item_id=?",rollbackItem);}
        check(scalar("SELECT COUNT(*) FROM sales")==before && scalar("SELECT stock FROM products WHERE item_id=?",item)==2 && scalar("SELECT stock FROM products WHERE item_id=?",rollbackItem)==2,"rollback restores header, earlier stock writes and later product");
        check(rollbackCart.size()==2,"failed sale leaves caller cart intact");
        invariant();
        // Reverse order IDs vs arrivals and then equal arrival timestamps to verify both ORDER BY terms.
        int chronology=catalog.product(0,tag+"arrival sorting");catalog.associate(chronology,a);catalog.associate(chronology,b);
        int earlierOrder=order(a,chronology,1,"20","20"),laterOrder=order(b,chronology,1,"30","30");
        sql("UPDATE purchase_orders SET order_datetime='2020-01-01',arrived_datetime='2020-01-04' WHERE order_id=?",earlierOrder);
        sql("UPDATE purchase_orders SET order_datetime='2020-01-01',arrived_datetime='2020-01-03' WHERE order_id=?",laterOrder);
        check(sales.preview(Map.of(chronology,1),bd("0")).products().getFirst().allocations().getFirst().batch().orderId()==laterOrder,"FIFO uses arrival rather than creation date");
        sql("UPDATE purchase_orders SET arrived_datetime='2020-01-03' WHERE order_id=?",earlierOrder);
        check(sales.preview(Map.of(chronology,1),bd("0")).products().getFirst().allocations().getFirst().batch().orderId()==earlierOrder,"FIFO ties use order ID");
        // Competing sales request the same last unit. At most one may commit.
        int scarce=catalog.product(0,tag+"scarce");catalog.associate(scarce,a);order(a,scarce,1,"10","10");
        Quote scarceQuote=sales.preview(Map.of(scarce,1),bd("20"));var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task=()->{gate.await();try{sales.save(customer,Map.of(scarce,1),bd("20"),scarceQuote);return true;}catch(IllegalArgumentException e){return false;}};
            var f1=pool.submit(task);var f2=pool.submit(task);gate.countDown();check((f1.get(15,TimeUnit.SECONDS)?1:0)+(f2.get(15,TimeUnit.SECONDS)?1:0)==1,"competing sales cannot oversell last unit");
        }
        order(a,item,1,"900","900");
        var laterPrice=sales.preview(Map.of(item,1),bd("35"));
        check(laterPrice.products().getFirst().unitPrice().compareTo(bd("175.50"))==0,"new markup uses allocated old cost and excludes expensive future batch");
        sales.save(customer,Map.of(item,1),bd("35"),laterPrice);
        check(sales.lines(sale).stream().allMatch(l->l.price().compareTo(bd("130"))==0),"historical prices unchanged after later receipts/sales");
        invariant();
        check(sales.list(LocalDateTime.of(2000,1,1,0,0),LocalDateTime.of(2099,1,1,0,0)).stream().anyMatch(s->s.id()==sale),"date-filtered sale history");
        check(purchases.list(LocalDateTime.of(2020,1,1,0,0),LocalDateTime.of(2020,1,5,0,0)).stream().anyMatch(p->p.id()==first),"date-filtered purchase history");
        var props=new Properties();try(var input=Files.newInputStream(Path.of(System.getProperty("inventory.test.config","config/test-database.properties")))){props.load(input);}
        props.setProperty("db.url","jdbc:mysql://127.0.0.1:1/inventory_test_unavailable");Path missing=Files.createTempFile("inventory-unavailable", ".properties");
        try {try(var out=Files.newOutputStream(missing)){props.store(out,"Temporary unavailable-port test");}fails(()->new CatalogService(new Database(missing)).products(""),"unavailable MySQL returns connection error");}finally{Files.deleteIfExists(missing);}
        System.out.println("Database checks passed: "+count);
        // A second JVM can use this record to prove process-restart persistence without resetting data.
        Files.writeString(Path.of("target/persistence-marker.txt"),""+item);
    }
    public static void main(String[] args) throws Exception {new DatabaseChecks(Path.of(System.getProperty("inventory.test.config","config/test-database.properties"))).run();}
}
