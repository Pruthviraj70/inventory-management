package com.pruthviraj.inventory.demo;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import com.pruthviraj.inventory.ui.DateTimes;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.*;
import java.util.*;

/** Never resets data; all write/failure tests require the existing isolated database. */
public final class SampleCleanupChecks {
    private static int count;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;System.out.println("PASS cleanup "+label);}
    private static final class Rollback extends RuntimeException { }
    private static void rollback(Database db,Database.Work<Void> work) throws SQLException {
        try{db.transaction(c->{work.run(c);throw new Rollback();});}catch(Rollback expected){ }
    }
    private static void legacy(Connection c,DemoDao.Ids ids) throws SQLException {
        var catalog=new CatalogDao();var plan=new DemoPlan();
        for(String p:plan.products)catalog.saveProduct(c,ids.products().get(p),DemoPlan.LEGACY_PREFIX+p);
        for(var s:plan.suppliers)catalog.saveSupplier(c,ids.suppliers().get(s.name()),DemoPlan.LEGACY_PREFIX+s.name(),s.days(),s.minimum());
        for(String name:plan.customers)catalog.saveCustomer(c,ids.customers().get(name),DemoPlan.LEGACY_PREFIX+name);
    }
    private static String name(Connection c,String table,String key,int id) throws SQLException {
        try(var p=c.prepareStatement("SELECT name FROM "+table+" WHERE "+key+"=?")){p.setInt(1,id);try(var r=p.executeQuery()){r.next();return r.getString(1);}}
    }
    public static void main(String[] args) throws Exception {
        var db=new Database(Path.of(System.getProperty("inventory.test.config","config/test-database.properties")));
        try(var c=db.open()){DemoPlan.require(c.getCatalog().matches("inventory_test_[A-Za-z0-9_]+"),"Isolated database required.");}
        new DemoSeeder(db).run(false,false);
        var demo=new DemoDao();var cleanup=new SampleNameCleanup(db);DemoDao.Ids ids;
        Map<String,Map<String,String>> original;
        try(var c=db.open()){ids=demo.find(c,new DemoPlan());original=demo.snapshot(c);}
        rollback(db,c->{
            legacy(c,ids);var before=demo.snapshot(c,ids);var result=cleanup.clean(c,false);
            check(result.changes().size()==20,"all twenty legacy sample names cleaned together");
            check(before.equals(demo.snapshot(c,ids)),"every non-name value remains exact");
            check(ids.equals(demo.find(c,new DemoPlan())),"history identity survives natural names");return null;
        });
        try(var c=db.open()){check(original.equals(demo.snapshot(c)),"late failure rolls back all twenty name changes");}
        rollback(db,c->{
            legacy(c,ids);new CatalogDao().saveCustomer(c,0,"walk-in customer");var before=demo.snapshot(c);
            boolean rejected=false;try{cleanup.clean(c,false);}catch(IllegalStateException conflict){rejected=true;}
            check(rejected,"collation-aware natural-name conflict stops cleanup");
            check(before.equals(demo.snapshot(c)),"conflict detected before the first rename");return null;
        });
        rollback(db,c->{
            legacy(c,ids);var catalog=new CatalogDao();int pen=ids.products().get("Blue Ball Pen");
            catalog.saveProduct(c,pen,"DEMO - Pen DEMO - stays");int unrelated=catalog.saveProduct(c,0,"DEMO - Unrelated "+System.nanoTime());
            cleanup.clean(c,false);
            check(name(c,"products","item_id",pen).equals("Pen DEMO - stays"),"only leading prefix removed; middle text retained");
            check(name(c,"products","item_id",unrelated).startsWith("DEMO - "),"unrelated prefixed record untouched");
            check(cleanup.clean(c,false).changes().isEmpty(),"repeat cleanup is a no-op");
            catalog.saveProduct(c,pen,"Renamed sample pen");check(ids.equals(demo.find(c,new DemoPlan())),"sample lookup ignores linked catalog names");return null;
        });
        rollback(db,c->{
            var newIds=new DemoSeeder(db).populate(c,new DemoPlan().validate());
            new DemoDao().verify(c,new DemoPlan(),new DemoPlan().validate(),newIds);
            check(newIds.products().entrySet().stream().allMatch(e->{try{return name(c,"products","item_id",e.getValue()).equals(e.getKey());}catch(SQLException ex){throw new RuntimeException(ex);}}),"fresh seeder creates natural product names");return null;
        });
        try(var c=db.open()){check(original.equals(demo.snapshot(c)),"all temporary rollback fixtures removed by rollback");}
        cleanup.run(false);Map<String,Map<String,String>> clean;
        try(var c=db.open()){clean=demo.snapshot(c);demo.audit(c);}
        check(cleanup.run(false).changes().isEmpty(),"committed cleanup repeat changes nothing");
        check(!new DemoSeeder(db).run(false,false).created(),"seeder skips cleaned samples without a prefix");
        try(var c=db.open()){check(clean.equals(demo.snapshot(c)),"cleanup and seeder repeats preserve all rows");}
        // Two timestamp components hidden by the UI still control the FIFO sequence.
        var moments=List.of(LocalDateTime.of(2026,10,24,12,0,45,123456000),LocalDateTime.of(2026,10,24,12,0,10,654321000),LocalDateTime.of(2026,10,24,12,0,10,100001000));
        var fixture=db.transaction(c->{
            var catalog=new CatalogDao();String tag="Precision "+System.nanoTime();int supplier=catalog.saveSupplier(c,0,tag,0,BigDecimal.ZERO),product=catalog.saveProduct(c,0,tag);catalog.associate(c,product,supplier);
            var orders=new ArrayList<Integer>();var service=new PurchaseService(db);
            for(int i=0;i<moments.size();i++) {
                BigDecimal cost=new BigDecimal(new String[]{"100","130","110"}[i]);
                int order=service.create(c,supplier,List.of(new PurchaseInput(product,1,cost)),LocalDateTime.of(2026,10,24,11,0));service.receive(c,order,Map.of(product,cost),moments.get(i));orders.add(order);
            }
            orders.add(product);return orders;
        });
        var purchases=new PurchaseDao();List<Batch> fifo;
        try(var c=db.open()){
            fifo=purchases.available(c,fixture.get(3),false);
            check(fifo.stream().map(Batch::orderId).toList().equals(List.of(fixture.get(2),fixture.get(1),fixture.get(0))),"FIFO uses seconds/microseconds before order-ID tie-break");
            for(int i=0;i<3;i++)check(purchases.arrival(c,fixture.get(i)).equals(moments.get(i)),"stored arrival precision retained for batch "+(i+1));
        }
        check(moments.stream().map(DateTimes::format).distinct().count()==1,"different FIFO timestamps display as the same minute");
        var quote=new SaleService(db).preview(Map.of(fixture.get(3),1),BigDecimal.ZERO);
        check(quote.products().getFirst().unitPrice().compareTo(new BigDecimal("110"))==0,"FIFO price uses the microsecond-earliest purchase");
        cleanup.run(false);
        try(var c=db.open()){check(fifo.equals(purchases.available(c,fixture.get(3),false)),"cleanup leaves unrelated precision-fixture FIFO unchanged");demo.audit(c);}
        System.out.println("Sample cleanup checks passed: "+count);
    }
}
