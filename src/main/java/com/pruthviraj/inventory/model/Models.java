package com.pruthviraj.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Small immutable data objects. Records generate constructors, accessors and equals. */
public final class Models {
    private Models() { }
    public record Product(int id, String name, int stock) { public String toString() { return id + " — " + name; } }
    public record Supplier(int id, String name, int days, BigDecimal minimum) { public String toString() { return id + " — " + name; } }
    public record Customer(int id, String name) { public String toString() { return id + " — " + name; } }
    public record SupplierStock(int itemId, int supplierId, String product, String supplier, int stock) { }
    public record PurchaseInput(int itemId, int quantity, BigDecimal price) { }
    public record Purchase(int id, int supplierId, String supplier, String status, LocalDateTime ordered, LocalDateTime arrived) { }
    public record PurchaseLine(int orderId, int itemId, int supplierId, String product, int quantity,
                               BigDecimal price, BigDecimal landed, int remaining) { }
    public record Batch(int orderId, int itemId, int supplierId, int remaining, BigDecimal cost) { }
    public record Allocation(Batch batch, int quantity) { }
    public record PricedProduct(int itemId, int quantity, BigDecimal unitPrice, List<Allocation> allocations) {
        public PricedProduct { allocations = List.copyOf(allocations); }
        public BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
    public record Quote(List<PricedProduct> products, BigDecimal total) {
        public Quote { products = List.copyOf(products); }
        // Compare customer-facing values. A changed FIFO source with the same price needs no acceptance.
        public boolean samePrices(Quote other) {
            if (other == null || total.compareTo(other.total) != 0 || products.size() != other.products.size()) return false;
            for (int i = 0; i < products.size(); i++) {
                var a = products.get(i); var b = other.products.get(i);
                if (a.itemId != b.itemId || a.quantity != b.quantity || a.unitPrice.compareTo(b.unitPrice) != 0) return false;
            }
            return true;
        }
    }
    public record Sale(int id, String customer, LocalDateTime date, BigDecimal markup, BigDecimal total) { }
    public record SaleLine(int itemId, int supplierId, String product, String supplier, int quantity, BigDecimal price) { }
}
