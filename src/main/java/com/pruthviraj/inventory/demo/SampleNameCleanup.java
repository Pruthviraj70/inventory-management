package com.pruthviraj.inventory.demo;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.dao.*;
import com.pruthviraj.inventory.model.Models.Batch;
import java.sql.*;
import java.util.*;
import static com.pruthviraj.inventory.demo.DemoPlan.require;

/** Explicit, transactional migration of only the original sample catalog names. */
public final class SampleNameCleanup {
    public record Result(List<SampleNameDao.Change> changes,int matched) {public int skipped(){return matched-changes.size();}}
    private final Database db;
    private final DemoDao demo=new DemoDao();
    public SampleNameCleanup(Database db){this.db=db;}
    public Result run(boolean dryRun) throws SQLException {
        try(var c=db.open()) {
            demo.acquireLock(c);
            try {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
                try {Result result=clean(c,dryRun);if(dryRun)c.rollback();else c.commit();return result;}
                catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
            }finally{demo.releaseLock(c);}
        }
    }
    private Map<Integer,List<Batch>> fifo(Connection c,DemoDao.Ids ids) throws SQLException {
        var result=new TreeMap<Integer,List<Batch>>();var dao=new PurchaseDao();
        for(int product:new TreeSet<>(ids.products().values()))result.put(product,List.copyOf(dao.available(c,product,false)));
        return result;
    }
    Result clean(Connection c,boolean dryRun) throws SQLException {
        var ids=demo.find(c,new DemoPlan());if(ids==null)return new Result(List.of(),0);
        demo.audit(c);var before=demo.snapshot(c,ids);var fifoBefore=fifo(c,ids);
        var names=new SampleNameDao();var changes=names.plan(c,ids);
        if(!dryRun)names.apply(c,changes);
        require(before.equals(demo.snapshot(c,ids)),"A value other than an identified sample name changed; cleanup rolled back.");
        require(fifoBefore.equals(fifo(c,ids)),"FIFO changed; cleanup rolled back.");demo.audit(c);
        return new Result(changes,ids.products().size()+ids.suppliers().size()+ids.customers().size());
    }
    public static void main(String[] args) {
        if(args.length>1 || (args.length==1 && !args[0].equals("--dry-run")))throw new IllegalArgumentException("Use no arguments or --dry-run.");
        boolean dry=args.length==1;
        try {
            var result=new SampleNameCleanup(new Database()).run(dry);
            System.out.println((dry?"Would rename ":"Renamed ")+result.changes().size()+" identified sample names; "+result.skipped()+" already have no leading prefix.");
            if(!dry)System.out.println("PASS: IDs, relationships, timestamps, quantities, prices, stock, history and FIFO unchanged. Repeat execution is harmless.");
        }catch(SQLException e){System.err.println("Cleanup failed; uncommitted changes rolled back. SQLState="+e.getSQLState()+", code="+e.getErrorCode()+". Credentials are not logged.");System.exit(1);}
        catch(RuntimeException e){System.err.println("Cleanup stopped: "+e.getMessage());System.exit(1);}
    }
}
