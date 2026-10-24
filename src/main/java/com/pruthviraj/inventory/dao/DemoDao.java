package com.pruthviraj.inventory.dao;

import com.pruthviraj.inventory.demo.DemoPlan;
import com.pruthviraj.inventory.model.Models.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import static com.pruthviraj.inventory.demo.DemoPlan.*;

/** Seed identity checks and independent audits. No schema changes or configuration writes. */
public final class DemoDao {
    public record Ids(Map<String,Integer> products,Map<String,Integer> suppliers,Map<String,Integer> customers,
                      Map<String,Integer> orders,Map<String,Integer> sales) { }
    private static final Map<String,String> KEYS=Map.of(
        "products","item_id","suppliers","supplier_id","customers","consumer_id",
        "supplier_products","item_id,supplier_id","purchase_orders","order_id",
        "purchase_order_items","order_id,item_id,supplier_id","sales","transaction_id",
        "sale_items","transaction_id,item_id,supplier_id");
    private final CatalogDao catalog=new CatalogDao();
    private final PurchaseDao purchases=new PurchaseDao();
    private final SaleDao sales=new SaleDao();
    public long scalar(Connection c,String sql,Object... args) throws SQLException {
        try(var p=c.prepareStatement(sql)) {Sql.bind(p,args);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Missing audit row.");return r.getLong(1);}}
    }
    private void bind(Map<String,Integer> ids,String role,int id) {
        Integer old=ids.putIfAbsent(role,id);
        require(old==null || old==id,"Conflicting sample history; nothing changed.");
        require(new HashSet<>(ids.values()).size()==ids.size(),"Ambiguous sample identities; nothing changed.");
    }
    private boolean plannedNamesExist(Connection c,DemoPlan plan) throws SQLException {
        for(String table:List.of("products","suppliers","customers")) {
            List<String> names=table.equals("products")?plan.products:table.equals("customers")?plan.customers:plan.suppliers.stream().map(DemoPlan.SupplierSpec::name).toList();
            for(String name:names)if(scalar(c,"SELECT COUNT(*) FROM "+table+" WHERE name=? OR name=?",name,LEGACY_PREFIX+name)>0)return true;
        }return false;
    }
    /** Identify linked catalog IDs from immutable dated line fingerprints, never from a name prefix.
     * The one unused customer has no history, so its unique known natural/legacy name is required.
     * Missing/ambiguous history stops; it is never repaired or replayed.
     */
    public Ids find(Connection c,DemoPlan plan) throws SQLException {
        var products=new LinkedHashMap<String,Integer>();var suppliers=new LinkedHashMap<String,Integer>();
        var customers=new LinkedHashMap<String,Integer>();var orders=new LinkedHashMap<String,Integer>();var saleIds=new LinkedHashMap<String,Integer>();
        boolean datedEvidence=false;
        for(var spec:plan.orders) {
            var candidates=new ArrayList<Integer>();var candidateSuppliers=new HashMap<Integer,Integer>();
            var candidateProducts=new HashMap<Integer,Map<String,Integer>>();
            try(var p=c.prepareStatement("SELECT order_id,supplier_id FROM purchase_orders WHERE order_datetime=?")) {
                p.setObject(1,spec.ordered());try(var r=p.executeQuery()){while(r.next()){
                    datedEvidence=true;int id=r.getInt(1);var rows=purchases.lines(c,id);if(rows.size()!=spec.lines().size())continue;
                    var mapping=new LinkedHashMap<String,Integer>();boolean matches=true;
                    for(var line:spec.lines()) {
                        var matching=rows.stream().filter(row->row.quantity()==line.quantity() && row.price().compareTo(line.price())==0 && (line.landed()==null || line.landed().equals(row.landed()))).toList();
                        if(matching.size()!=1){matches=false;break;}
                        mapping.put(line.product(),matching.getFirst().itemId());
                    }
                    if(matches && new HashSet<>(mapping.values()).size()==mapping.size()) {candidates.add(id);candidateProducts.put(id,mapping);candidateSuppliers.put(id,r.getInt(2));}
                }}
            }
            require(candidates.size()<=1,"Ambiguous sample purchase history; nothing changed.");
            if(candidates.size()==1) {
                int id=candidates.getFirst();orders.put(spec.key(),id);bind(suppliers,spec.supplier(),candidateSuppliers.get(id));
                candidateProducts.get(id).forEach((role,key)->bind(products,role,key));
            }
        }
        var expected=plan.validate();
        for(var spec:plan.sales) {
            var candidates=new ArrayList<Integer>();var consumers=new HashMap<Integer,Integer>();
            try(var p=c.prepareStatement("SELECT transaction_id,consumer_id,markup_percent,cart_total FROM sales WHERE sale_datetime=?")) {
                p.setObject(1,spec.date());try(var r=p.executeQuery()){while(r.next()){
                    datedEvidence=true;int id=r.getInt(1);
                    if(r.getBigDecimal(3).compareTo(spec.markup())!=0 || r.getBigDecimal(4).compareTo(spec.total())!=0)continue;
                    var rows=sales.lines(c,id);var allocations=expected.saleSupplierQuantities().get(spec.key());boolean matches=rows.size()==allocations.size();
                    for(var sold:spec.lines())for(var supplier:plan.suppliers) {
                        Integer qty=allocations.get(pair(sold.product(),supplier.name()));if(qty==null)continue;
                        long count=rows.stream().filter(row->Objects.equals(products.get(sold.product()),row.itemId()) && Objects.equals(suppliers.get(supplier.name()),row.supplierId()) && row.quantity()==qty && row.price().compareTo(sold.price())==0).count();
                        if(count!=1)matches=false;
                    }
                    if(matches){candidates.add(id);consumers.put(id,r.getInt(2));}
                }}
            }
            require(candidates.size()<=1,"Ambiguous sample sale history; nothing changed.");
            if(candidates.size()==1){int id=candidates.getFirst();saleIds.put(spec.key(),id);bind(customers,spec.customer(),consumers.get(id));}
        }
        if(orders.isEmpty() && saleIds.isEmpty() && !datedEvidence && !plannedNamesExist(c,plan))return null;
        require(orders.size()==plan.orders.size() && saleIds.size()==plan.sales.size() && products.size()==10 && suppliers.size()==4,"Incomplete or changed sample history; refusing to merge or replay it.");
        for(String unused:plan.customers)if(!customers.containsKey(unused)) {
            var matches=new ArrayList<Integer>();
            try(var p=c.prepareStatement("SELECT consumer_id FROM customers WHERE name=? OR name=?")) {
                Sql.bind(p,unused,LEGACY_PREFIX+unused);try(var r=p.executeQuery()){while(r.next())matches.add(r.getInt(1));}
            }
            require(matches.size()==1,"Missing or ambiguous unused sample customer; nothing changed.");bind(customers,unused,matches.getFirst());
        }
        return new Ids(Map.copyOf(products),Map.copyOf(suppliers),Map.copyOf(customers),Map.copyOf(orders),Map.copyOf(saleIds));
    }
    /** Snapshot all existing rows so initial seeding can prove it did not modify existing data. */
    public Map<String,Map<String,String>> snapshot(Connection c) throws SQLException {return snapshot(c,null);}
    /** Ignore only identified sample name cells; every other value and full timestamp stays exact. */
    public Map<String,Map<String,String>> snapshot(Connection c,Ids ignoredNames) throws SQLException {
        var result=new TreeMap<String,Map<String,String>>();
        for(var entry:KEYS.entrySet()) {
            var rows=new TreeMap<String,String>();
            try(var p=c.prepareStatement("SELECT * FROM "+entry.getKey()+" ORDER BY "+entry.getValue());var r=p.executeQuery()) {
                while(r.next()) {
                    StringBuilder key=new StringBuilder(),row=new StringBuilder();
                    for(String column:entry.getValue().split(","))key.append(r.getInt(column)).append('/');
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                        boolean ignore=ignoredNames!=null && r.getMetaData().getColumnName(i).equals("name") && switch(entry.getKey()) {
                            case "products"->ignoredNames.products().containsValue(r.getInt("item_id"));
                            case "suppliers"->ignoredNames.suppliers().containsValue(r.getInt("supplier_id"));
                            case "customers"->ignoredNames.customers().containsValue(r.getInt("consumer_id"));
                            default->false;
                        };
                        String value=ignore?"<identified sample name>":r.getString(i);row.append(value==null?-1:value.length()).append(':').append(value).append(';');
                    }
                    rows.put(key.toString(),row.toString());
                }
            }result.put(entry.getKey(),Map.copyOf(rows));
        }return Map.copyOf(result);
    }
    public void preserved(Map<String,Map<String,String>> before,Map<String,Map<String,String>> after) {
        for(var table:before.entrySet())for(var row:table.getValue().entrySet())
            require(row.getValue().equals(after.get(table.getKey()).get(row.getKey())),"An existing row changed during seeding; rolling back the demo.");
    }
    public void acquireLock(Connection c) throws SQLException {
        require(scalar(c,"SELECT GET_LOCK(CONCAT('inventory_demo_v1_',LEFT(SHA2(DATABASE(),256),32)),10)")==1,"Another demo seeder is running; retry later.");
    }
    public void releaseLock(Connection c) throws SQLException {scalar(c,"SELECT RELEASE_LOCK(CONCAT('inventory_demo_v1_',LEFT(SHA2(DATABASE(),256),32)))");}
    public void audit(Connection c) throws SQLException {
        require(scalar(c,"SELECT COUNT(*) FROM products p WHERE p.stock<>COALESCE((SELECT SUM(sp.supplier_specific_stock) FROM supplier_products sp WHERE sp.item_id=p.item_id),0) OR p.stock<>COALESCE((SELECT SUM(i.remaining_quantity) FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id WHERE i.item_id=p.item_id AND o.status='RECEIVED'),0)")==0,"Product stock audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM supplier_products sp WHERE sp.supplier_specific_stock<>COALESCE((SELECT SUM(i.remaining_quantity) FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id WHERE i.item_id=sp.item_id AND i.supplier_id=sp.supplier_id AND o.status='RECEIVED'),0)")==0,"Supplier stock audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM sales s WHERE s.cart_total<>COALESCE((SELECT SUM(i.quantity*i.price_per_unit) FROM sale_items i WHERE i.transaction_id=s.transaction_id),0)")==0,"Cart total audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM (SELECT transaction_id,item_id FROM sale_items GROUP BY transaction_id,item_id HAVING MIN(price_per_unit)<>MAX(price_per_unit)) differing")==0,"Shared product price audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM purchase_order_items i LEFT JOIN purchase_orders o ON o.order_id=i.order_id AND o.supplier_id=i.supplier_id LEFT JOIN supplier_products sp ON sp.item_id=i.item_id AND sp.supplier_id=i.supplier_id WHERE o.order_id IS NULL OR sp.item_id IS NULL OR i.remaining_quantity<0 OR i.remaining_quantity>i.quantity OR (o.status<>'RECEIVED' AND (i.remaining_quantity<>0 OR i.landed_unit_cost IS NOT NULL))")==0,"Purchase foreign-key/status/remaining audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM sale_items i LEFT JOIN sales s ON s.transaction_id=i.transaction_id LEFT JOIN supplier_products sp ON sp.item_id=i.item_id AND sp.supplier_id=i.supplier_id WHERE s.transaction_id IS NULL OR sp.item_id IS NULL")==0,"Sale foreign-key audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM purchase_orders o LEFT JOIN suppliers s ON s.supplier_id=o.supplier_id WHERE s.supplier_id IS NULL")==0,"Order supplier audit failed.");
        require(scalar(c,"SELECT COUNT(*) FROM sales s LEFT JOIN customers c ON c.consumer_id=s.consumer_id WHERE c.consumer_id IS NULL")==0,"Sale customer audit failed.");
    }
    /** Exact replay expectations apply immediately after initial seed, before interactive demo use. */
    public void verify(Connection c,DemoPlan plan,DemoPlan.Expected expected,Ids ids) throws SQLException {
        require(ids!=null,"No complete demo dataset found.");audit(c);
        var productRows=catalog.products(c,"");var supplierRows=catalog.suppliers(c,"");var customerRows=catalog.customers(c,"");
        for(var spec:plan.suppliers) {
            Supplier row=supplierRows.stream().filter(s->s.id()==ids.suppliers().get(spec.name())).findFirst().orElseThrow();
            require(row.days()==spec.days() && row.minimum().compareTo(spec.minimum())==0,"Demo supplier settings changed.");
        }
        for(String product:plan.products) {
            Product row=productRows.stream().filter(p->p.id()==ids.products().get(product)).findFirst().orElseThrow();
            require(row.stock()==expected.stocks().get(product),"Unexpected stock for "+name(product));
        }
        require(customerRows.stream().filter(row->ids.customers().containsValue(row.id())).count()==6,"Unexpected demo customer count.");
        var pairs=catalog.associations(c).stream().filter(p->ids.products().containsValue(p.itemId())).toList();
        require(pairs.size()==14,"Unexpected demo association count.");
        for(var supplier:plan.suppliers)for(String product:supplier.products()) {
            var pair=pairs.stream().filter(p->p.itemId()==ids.products().get(product) && p.supplierId()==ids.suppliers().get(supplier.name())).findFirst().orElseThrow();
            require(pair.stock()==expected.supplierStocks().get(pair(product,supplier.name())),"Unexpected demo supplier stock.");
        }
        for(var order:plan.orders) {
            int id=ids.orders().get(order.key());
            try(var p=c.prepareStatement("SELECT status,order_datetime,arrived_datetime FROM purchase_orders WHERE order_id=?")) {
                p.setInt(1,id);try(var r=p.executeQuery()) {require(r.next(),"Missing demo order.");require(order.status().equals(r.getString(1)) && order.ordered().equals(r.getObject(2,java.time.LocalDateTime.class)) && Objects.equals(order.arrived(),r.getObject(3,java.time.LocalDateTime.class)),"Demo order status/date changed.");}
            }
            var lines=purchases.lines(c,id);require(lines.size()==order.lines().size(),"Unexpected purchase line count.");BigDecimal subtotal=BigDecimal.ZERO;
            for(var line:order.lines()) {
                var row=lines.stream().filter(l->l.itemId()==ids.products().get(line.product())).findFirst().orElseThrow();
                require(row.supplierId()==ids.suppliers().get(order.supplier()) && row.quantity()==line.quantity() && row.price().compareTo(line.price())==0 && Objects.equals(row.landed(),line.landed()) && row.remaining()==expected.remaining().get(pair(order.key(),line.product())),"FIFO/price/purchase mismatch: "+order.key()+" / "+line.product());
                subtotal=subtotal.add(row.price().multiply(BigDecimal.valueOf(row.quantity())));
            }
            require(subtotal.compareTo(plan.supplier(order.supplier()).minimum())>=0,"Goods minimum failed.");
        }
        for(var sale:plan.sales) {
            int id=ids.sales().get(sale.key());
            try(var p=c.prepareStatement("SELECT consumer_id,sale_datetime,markup_percent,cart_total FROM sales WHERE transaction_id=?")) {
                p.setInt(1,id);try(var r=p.executeQuery()){require(r.next(),"Missing sale.");require(r.getInt(1)==ids.customers().get(sale.customer()) && sale.date().equals(r.getObject(2,java.time.LocalDateTime.class)) && r.getBigDecimal(3).compareTo(sale.markup())==0 && r.getBigDecimal(4).compareTo(sale.total())==0,"Demo sale header mismatch.");}
            }
            var lines=sales.lines(c,id);var allocations=expected.saleSupplierQuantities().get(sale.key());require(lines.size()==allocations.size(),"Supplier aggregation mismatch.");
            for(var sold:sale.lines())for(var supplier:plan.suppliers) {
                Integer qty=allocations.get(pair(sold.product(),supplier.name()));if(qty==null)continue;
                var row=lines.stream().filter(l->l.itemId()==ids.products().get(sold.product()) && l.supplierId()==ids.suppliers().get(supplier.name())).findFirst().orElseThrow();
                require(row.quantity()==qty && row.price().compareTo(sold.price())==0,"Demo FIFO allocation/saved price mismatch.");
            }
        }
    }
}
