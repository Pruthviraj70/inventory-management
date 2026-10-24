package com.pruthviraj.inventory.dao;

import java.sql.*;
import java.util.*;
import com.pruthviraj.inventory.service.Rules;
import static com.pruthviraj.inventory.demo.DemoPlan.*;

/** Changes names only for IDs proven to belong to the original sample history. */
public final class SampleNameDao {
    public record Change(String table,int id,String before,String after) { }
    private static final Map<String,String> KEYS=Map.of("products","item_id","suppliers","supplier_id","customers","consumer_id");
    public List<Change> plan(Connection c,DemoDao.Ids ids) throws SQLException {
        Rules.transaction(c);var changes=new ArrayList<Change>();
        Map<String,Map<String,Integer>> groups=Map.of("products",ids.products(),"suppliers",ids.suppliers(),"customers",ids.customers());
        for(String table:List.of("products","suppliers","customers"))for(int id:new TreeSet<>(groups.get(table).values())) {
            try(var p=c.prepareStatement("SELECT name FROM "+table+" WHERE "+KEYS.get(table)+"=? FOR UPDATE")) {
                p.setInt(1,id);try(var r=p.executeQuery()) {
                    require(r.next(),"An identified sample record is missing.");String old=r.getString(1);
                    if(old.startsWith(LEGACY_PREFIX)) {
                        String natural=old.substring(LEGACY_PREFIX.length());
                        require(!natural.isBlank(),"Removing the prefix would leave an empty name.");
                        changes.add(new Change(table,id,old,natural));
                    }
                }
            }
        }
        // Check every target before the FIRST update; MySQL uses each name column's collation.
        for(var change:changes) {
            try(var p=c.prepareStatement("SELECT "+KEYS.get(change.table())+" FROM "+change.table()+" WHERE name=? AND "+KEYS.get(change.table())+"<>? FOR UPDATE")) {
                Sql.bind(p,change.after(),change.id());try(var r=p.executeQuery()){require(!r.next(),"Name conflict in "+change.table()+": "+change.after()+". Nothing was renamed.");}
            }
        }
        for(int i=0;i<changes.size();i++)for(int j=i+1;j<changes.size();j++) {
            var a=changes.get(i);var b=changes.get(j);
            if(a.table().equals(b.table()))require(!equalNames(c,a.table(),a.after(),b.after()),"Two sample names would conflict. Nothing was renamed.");
        }
        return List.copyOf(changes);
    }
    private boolean equalNames(Connection c,String table,String a,String b) throws SQLException {
        String collation;
        try(var p=c.prepareStatement("SELECT collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name='name'")) {
            p.setString(1,table);try(var r=p.executeQuery()){require(r.next(),"Name collation is unavailable.");collation=r.getString(1);}
        }
        require(collation!=null && collation.matches("[a-zA-Z0-9_]+"),"Unsupported name collation.");
        String expression="CONVERT(? USING utf8mb4) COLLATE "+collation;
        try(var p=c.prepareStatement("SELECT "+expression+" = "+expression)) {Sql.bind(p,a,b);try(var r=p.executeQuery()){r.next();return r.getBoolean(1);}}
    }
    public void apply(Connection c,List<Change> changes) throws SQLException {
        Rules.transaction(c);
        for(var change:changes) {
            require(KEYS.containsKey(change.table()),"Invalid cleanup table.");
            Sql.one(Sql.update(c,"UPDATE "+change.table()+" SET name=? WHERE "+KEYS.get(change.table())+"=? AND CAST(name AS BINARY)=CAST(? AS BINARY)",change.after(),change.id(),change.before()));
        }
    }
}
