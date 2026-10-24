package com.pruthviraj.inventory.demo;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static com.pruthviraj.inventory.demo.DemoPlan.*;

/** All write tests require the existing isolated inventory_test_* database. */
public final class DemoSeedChecks {
    private static int checks;
    private static void check(boolean condition,String label) {require(condition,label);checks++;System.out.println("PASS demo "+label);}
    private static final class IntentionalFailure extends RuntimeException { }
    @FunctionalInterface private interface Attempt {void run() throws Exception;}
    private static void rejects(Attempt action,String label) throws Exception {
        try {action.run();}catch(IllegalArgumentException|IllegalStateException e){check(true,label);return;}throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Database db=new Database(Path.of(System.getProperty("inventory.test.config","config/test-database.properties")));
        try(var c=db.open()){require(c.getCatalog().matches("inventory_test_[A-Za-z0-9_]+"),"Refusing demo checks outside isolated test database.");}
        DemoPlan plan=new DemoPlan();Expected expected=plan.validate();DemoDao dao=new DemoDao();
        check(expected.stocks().values().stream().mapToInt(Integer::intValue).sum()==86,"complete plan validates to 86 remaining units");
        check(expected.saleSupplierQuantities().get("S2").get(pair("A5 Notebook","Campus Stationery Wholesale"))==5 && expected.saleSupplierQuantities().get("S2").get(pair("A5 Notebook","City Paper and Office"))==3,"₹100/₹110 scenario allocates five plus three units");
        check(expected.saleSupplierQuantities().get("S3").get(pair("Geometry Box","Scholars School Supplies"))==2,"₹100/₹130 scenario spans suppliers");
        check(expected.saleSupplierQuantities().get("S4").get(pair("Blue Ball Pen","Campus Stationery Wholesale"))==50,"same supplier batches aggregate to fifty pens");
        var seeder=new DemoSeeder(db);Map<String,Map<String,String>> before;boolean fresh;
        try(var c=db.open()){before=dao.snapshot(c);fresh=dao.find(c,plan)==null;}
        if(fresh) {
            try {db.transaction(c->{var ids=seeder.populate(c,expected);dao.verify(c,plan,expected,ids);throw new IntentionalFailure();});}
            catch(IntentionalFailure expectedFailure){check(true,"failure after full population prevents commit");}
            try(var c=db.open()){check(before.equals(dao.snapshot(c)),"full catalog/order/receipt/sale transaction rolled back");}
        }else System.out.println("SKIP rollback-population setup: isolated DEMO dataset already exists; it is preserved.");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<DemoSeeder.Result> task=()->{gate.await();return new DemoSeeder(db).run(false,false);};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            check((a.created()?1:0)+(b.created()?1:0)==(fresh?1:0),"concurrent invocations create at most one demo");
        }
        Map<String,Map<String,String>> after;DemoDao.Ids ids;
        try(var c=db.open()){after=dao.snapshot(c);ids=dao.find(c,plan);dao.verify(c,plan,expected,ids);dao.preserved(before,after);check(true,"database matches complete prevalidated FIFO/pricing plan");}
        check(!seeder.run(false,false).created(),"repeat invocation skips completed demo");
        try(var c=db.open()){check(after.equals(dao.snapshot(c)),"repeat invocation changes no rows or stock");}
        check(!seeder.run(true,false).created() && !seeder.run(false,true).created(),"dry-run and verify do not reseed");
        try(var c=db.open()) {
            rejects(()->new PurchaseService(db).create(c,ids.suppliers().get("Campus Stationery Wholesale"),List.of(new PurchaseInput(ids.products().get("Blue Ball Pen"),100,new BigDecimal("8"))),null),"caller-owned overload rejects autocommit");
        }
        rejects(()->db.transaction(c->{
            var purchases=new PurchaseService(db);int id=purchases.create(c,ids.suppliers().get("Campus Stationery Wholesale"),List.of(new PurchaseInput(ids.products().get("Blue Ball Pen"),100,new BigDecimal("8"))),LocalDateTime.of(2026,10,20,9,0));
            purchases.receive(c,id,Map.of(ids.products().get("Blue Ball Pen"),new BigDecimal("10")),LocalDateTime.of(2026,10,19,9,0));return null;
        }),"arrival before order is rejected atomically");
        rejects(()->db.transaction(c->{
            var sales=new SaleService(db);var cart=Map.of(ids.products().get("Blue Ball Pen"),1);var quote=sales.preview(c,cart,new BigDecimal("20"));
            sales.save(c,ids.customers().get("Walk-in Customer"),cart,new BigDecimal("20"),quote,LocalDateTime.of(2026,10,1,9,0));return null;
        }),"sale before consumed stock arrived is rejected");
        rejects(()->db.transaction(c->{new CatalogDao().saveCustomer(c,0,"Kavya Desai");dao.find(c,plan);return null;}),"ambiguous unused customer stops instead of replaying");
        try(var c=db.open()){check(after.equals(dao.snapshot(c)),"invalid-date and identity-conflict failures preserve all rows");}
        System.out.println("Demo seed checks passed: "+checks);
    }
}
