package com.pruthviraj.inventory.demo;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import java.sql.*;
import java.util.*;
import static com.pruthviraj.inventory.demo.DemoPlan.*;

/** Explicit command-line utility. Main never calls it. All seed writes share one transaction. */
public final class DemoSeeder {
    public record Result(boolean created,DemoDao.Ids ids) { }
    private final Database db;
    private final DemoPlan plan=new DemoPlan();
    private final DemoDao dao=new DemoDao();
    public DemoSeeder(Database db) {this.db=db;}
    public Result run(boolean dryRun,boolean verifyOnly) throws SQLException {
        Expected expected=plan.validate(); // Complete in-memory validation precedes even opening a connection.
        try(var c=db.open()) {
            dao.acquireLock(c);
            try {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
                try {
                    var before=dao.snapshot(c);dao.audit(c);var existing=dao.find(c,plan);
                    if(verifyOnly) {
                        dao.verify(c,plan,expected,existing);c.rollback();return new Result(false,existing);
                    }
                    if(dryRun) {c.rollback();return new Result(false,existing);}
                    if(existing!=null) {
                        // Never replay receipts/sales, even if the user has since consumed demo inventory.
                        // Dated purchase/sale line fingerprints identify the same catalog IDs, independent of prefixes.
                        dao.preserved(before,dao.snapshot(c));c.commit();return new Result(false,existing);
                    }
                    var ids=populate(c,expected);
                    dao.verify(c,plan,expected,ids);dao.preserved(before,dao.snapshot(c));
                    c.commit();return new Result(true,ids);
                } catch(SQLException|RuntimeException e) {
                    try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;
                }
            } finally {dao.releaseLock(c);}
        }
    }
    /** Package-visible so the isolated harness can prove failure rolls back the full population. */
    DemoDao.Ids populate(Connection c,Expected expected) throws SQLException {
        Rules.transaction(c);
        var catalog=new CatalogDao();var purchases=new PurchaseService(db);var sales=new SaleService(db);
        var products=new LinkedHashMap<String,Integer>();var suppliers=new LinkedHashMap<String,Integer>();var customers=new LinkedHashMap<String,Integer>();
        var orders=new LinkedHashMap<String,Integer>();var saleIds=new LinkedHashMap<String,Integer>();
        for(String product:plan.products)products.put(product,catalog.saveProduct(c,0,Rules.name(name(product))));
        for(var supplier:plan.suppliers)suppliers.put(supplier.name(),catalog.saveSupplier(c,0,Rules.name(name(supplier.name())),supplier.days(),Rules.money(supplier.minimum())));
        for(String customer:plan.customers)customers.put(customer,catalog.saveCustomer(c,0,Rules.name(name(customer))));
        for(var supplier:plan.suppliers)for(String product:supplier.products())catalog.associate(c,products.get(product),suppliers.get(supplier.name()));
        for(var order:plan.orders) {
            var inputs=order.lines().stream().map(l->new PurchaseInput(products.get(l.product()),l.quantity(),l.price())).toList();
            int id=purchases.create(c,suppliers.get(order.supplier()),inputs,order.ordered());orders.put(order.key(),id);
            if(order.status().equals("RECEIVED")) {
                var costs=new TreeMap<Integer,java.math.BigDecimal>();for(var line:order.lines())costs.put(products.get(line.product()),line.landed());
                purchases.receive(c,id,costs,order.arrived());
            }else if(order.status().equals("CANCELLED"))purchases.cancel(c,id);
        }
        for(var sale:plan.sales) {
            var cart=new TreeMap<Integer,Integer>();for(var line:sale.lines())cart.put(products.get(line.product()),line.quantity());
            Quote quote=sales.preview(c,cart,sale.markup());
            require(quote.total().compareTo(sale.total())==0,"Database quote differs from the validated plan.");
            for(var line:sale.lines())require(quote.products().stream().filter(p->p.itemId()==products.get(line.product())).findFirst().orElseThrow().unitPrice().compareTo(line.price())==0,"Database selling price differs from the validated plan.");
            saleIds.put(sale.key(),sales.save(c,customers.get(sale.customer()),cart,sale.markup(),quote,sale.date()));
        }
        return new DemoDao.Ids(Map.copyOf(products),Map.copyOf(suppliers),Map.copyOf(customers),Map.copyOf(orders),Map.copyOf(saleIds));
    }
    public static void main(String[] args) {
        if(args.length>1 || (args.length==1 && !Set.of("--dry-run","--verify").contains(args[0])))throw new IllegalArgumentException("Use no arguments, --dry-run or --verify.");
        boolean dry=args.length==1 && args[0].equals("--dry-run"),verify=args.length==1 && args[0].equals("--verify");
        try {
            Result result=new DemoSeeder(new Database()).run(dry,verify);
            if(verify)System.out.println("PASS: all demo relationships, dates, FIFO remaining quantities, stocks, statuses, prices and totals match the validated plan.");
            else if(dry)System.out.println("PASS: complete plan and existing database validated; no writes. "+(result.ids()==null?"Ready to add the demo.":"Complete demo already exists; seed execution will skip it."));
            else if(result.created()) {
                System.out.println("Added: 10 products, 4 suppliers, 6 customers, 14 associations, 8 orders (5 received / 2 pending / 1 cancelled), 22 purchase lines, 5 sales, 15 sale lines.");
                System.out.println("PASS: pre-existing rows preserved; atomic commit; 86 units remain; demo sales total ₹4230.80. Skipped: 0.");
            }else System.out.println("Skipped complete existing demo: 10 products, 4 suppliers, 6 customers, 14 associations, 8 orders and 5 sales. No stock or record changes.");
            if(result.ids()!=null) {
                result.ids().orders().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->System.out.println(e.getKey()+" → order #"+e.getValue()));
                result.ids().sales().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->System.out.println(e.getKey()+" → sale #"+e.getValue()));
            }
        }catch(SQLException e) {
            System.err.println("Demo operation failed; transaction rolled back if uncommitted. SQLState="+e.getSQLState()+", vendorCode="+e.getErrorCode()+". Check local configuration/server; credentials are not logged.");
            System.exit(1);
        }catch(RuntimeException e) {
            System.err.println("Demo operation stopped without replaying records: "+e.getMessage());System.exit(1);
        }
    }
}
