package com.pruthviraj.inventory;

import com.pruthviraj.inventory.model.Models.*;
import com.pruthviraj.inventory.service.*;
import com.pruthviraj.inventory.config.Database;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

public final class PureChecks {
    private static int passed;
    static BigDecimal bd(String text) { return new BigDecimal(text); }
    static void check(boolean condition,String label) { if(!condition) throw new AssertionError(label);passed++;System.out.println("PASS "+label); }
    static void fails(Runnable task,String label) {try {task.run();}catch(IllegalArgumentException|ArithmeticException e){check(true,label);return;}throw new AssertionError(label);}
    static Batch batch(int order,int supplier,int qty,String cost) { return new Batch(order,1,supplier,qty,bd(cost)); }
    public static void main(String[] args) {
        check(Pricing.allocate(1,2,bd("20"),List.of(batch(1,1,1,"100"),batch(2,2,1,"110"))).unitPrice().compareTo(bd("120"))==0,"₹100 / ₹110 at 20% → ₹120");
        check(Pricing.allocate(1,2,bd("20"),List.of(batch(1,1,1,"100"),batch(2,2,1,"130"))).unitPrice().compareTo(bd("130"))==0,"₹100 / ₹130 at 20% → ₹130");
        check(Pricing.allocate(1,1,bd("20"),List.of(batch(1,1,1,"130"))).unitPrice().compareTo(bd("156"))==0,"₹130 at 20% → ₹156");
        var p=Pricing.allocate(1,1,bd("20"),List.of(batch(1,1,1,"100"),batch(2,2,1,"900")));
        check(p.unitPrice().compareTo(bd("120"))==0,"unconsumed costs excluded");
        var fifo=Pricing.allocate(1,4,bd("20"),List.of(batch(1,1,2,"100"),batch(2,2,1,"110"),batch(3,1,5,"130")));
        check(fifo.allocations().size()==3 && fifo.allocations().get(2).quantity()==1,"FIFO spans suppliers and repeated supplier purchases");
        check(Pricing.allocate(1,3,bd("20"),List.of(batch(1,1,3,"1.01"))).total().compareTo(bd("3.63"))==0,"line total uses rounded unit price");
        check(Rules.money(bd("1.005")).equals(bd("1.01")),"HALF_UP money rounding");
        fails(()->Rules.name("  "),"blank name rejected");fails(()->Rules.name("x".repeat(151)),"long name rejected");
        fails(()->Rules.positive(0),"zero quantity rejected");fails(()->Rules.markup(bd("-1")),"negative markup rejected");
        fails(()->Rules.markup(bd("1.001")),"excess markup precision rejected");fails(()->Rules.money(bd("-0.001")),"negative money before rounding rejected");
        fails(()->Rules.money(bd("1000000000000")),"money overflow rejected");
        fails(()->Pricing.allocate(1,3,bd("20"),List.of(batch(1,1,2,"100"))),"insufficient FIFO stock rejected");
        var purchases=new PurchaseService(new Database(Path.of("unused")));
        var merged=purchases.merge(List.of(new PurchaseInput(1,2,bd("10")),new PurchaseInput(1,3,bd("10"))));
        check(merged.size()==1 && merged.getFirst().quantity()==5,"duplicate purchase selections merge");
        fails(()->purchases.merge(List.of(new PurchaseInput(1,1,bd("10")),new PurchaseInput(1,1,bd("11")))),"conflicting duplicate prices rejected");
        fails(()->purchases.merge(List.of()),"empty order rejected");
        fails(()->purchases.merge(List.of(new PurchaseInput(1,Integer.MAX_VALUE,bd("1")),new PurchaseInput(1,1,bd("1")))),"quantity overflow rejected");
        System.out.println("Pure checks passed: "+passed);
    }
}
