package com.pruthviraj.inventory;

import com.pruthviraj.inventory.config.Database;
import com.pruthviraj.inventory.service.CatalogService;
import java.nio.file.*;

/** Run in a fresh JVM after DatabaseChecks. */
public final class PersistenceCheck {
    public static void main(String[] args) throws Exception {
        var db=new Database(Path.of(System.getProperty("inventory.test.config","config/test-database.properties")));
        try(var c=db.open()) {if(!c.getCatalog().startsWith("inventory_test_")) throw new IllegalArgumentException("Test database required.");}
        int id=Integer.parseInt(Files.readString(Path.of("target/persistence-marker.txt")).strip());
        if(new CatalogService(db).products("").stream().noneMatch(p->p.id()==id && p.stock()==2)) throw new AssertionError("Restart persistence failed.");
        System.out.println("PASS persistence verified in a separate JVM");
    }
}
