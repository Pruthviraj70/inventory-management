package com.pruthviraj.inventory.service;

import com.pruthviraj.inventory.model.Models.*;
import java.math.*;
import java.util.*;

/** Pure FIFO allocation and price calculation; no database or UI dependencies. */
public final class Pricing {
    private Pricing() { }
    public static PricedProduct allocate(int item,int quantity,BigDecimal markup,List<Batch> fifo) {
        Rules.positive(item); Rules.positive(quantity); markup=Rules.markup(markup);
        int needed=quantity; var allocations=new ArrayList<Allocation>();
        BigDecimal lowestNormal=null, highestCost=BigDecimal.ZERO;
        BigDecimal multiplier=BigDecimal.ONE.add(markup.movePointLeft(2));
        for(var batch:fifo) {
            if(needed==0) break;
            if(batch.itemId()!=item || batch.remaining()<=0 || batch.cost()==null) throw new IllegalArgumentException("Invalid FIFO stock data.");
            int take=Math.min(needed,batch.remaining());
            allocations.add(new Allocation(batch,take)); needed-=take;
            BigDecimal normal=batch.cost().multiply(multiplier);
            lowestNormal=lowestNormal==null?normal:lowestNormal.min(normal);
            highestCost=highestCost.max(batch.cost());
        }
        if(needed>0) throw new IllegalArgumentException("Insufficient stock for product #"+item+". Requested "+quantity+", available "+(quantity-needed)+".");
        // Only consumed batches influence the shared product price. Round once, then multiply.
        return new PricedProduct(item,quantity,Rules.money(lowestNormal.max(highestCost)),allocations);
    }
}
