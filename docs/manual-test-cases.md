# Manual and automated test cases

Run manual cases on a dedicated `inventory_test_*` database with an external config selected using `-Dinventory.config=...`. Do not clear an existing database. The automation appends unique fixtures. Prices below are rupees; all quantities are whole units.

## Automated coverage

`./scripts/verify.sh`: all three requested pricing examples, FIFO allocation, unused-cost exclusion, unit rounding before total, name/quantity/money/markup bounds, duplicate merges and overflow.

`./scripts/verify.sh --database`: catalog create/edit/delete/search, separate-connection persistence, referenced deletions, association uniqueness and both composite foreign keys, minimum goods subtotal, pending/cancel stock neutrality, landed validation, receipt once, immutable received/cancelled orders, cross-supplier and repeated-supplier FIFO, arrival ordering and ties, supplier sale aggregation, shared prices, totals, insufficient stock, stale quote acceptance, rollback after earlier writes, cart preservation, competing sales, all product/supplier invariants, historical prices, date filtering and unavailable-port connection failure. Then PersistenceCheck runs in a new JVM.

`./scripts/verify.sh --gui`: opens actual Swing windows, creates a pending order through its form, merges a duplicate selection, receives it with a manually filled landed cost field, edits markup, checks repricing, checks insufficient-stock cart retention, corrects quantity, saves the sale, reads history and verifies the unavailable-server dialog/recovery. It writes screenshots under target/. It uses component actions on the EDT; it does not verify OS keyboard focus or mouse hit targets.

## Human checklist (not claimed as performed)

| Case | Steps | Expected result |
|---|---|---|
| Catalog forms | Add Pen, supplier A (2 days, minimum 200), customer Walk-in Customer. Edit names; search names, `%`, apostrophes and Unicode. Restart app and search again. | Values persist. Search is literal substring; no SQL errors. |
| Invalid forms | Submit blank/151-character names, negative days, nonnumeric/overflow quantity, negative money and markup, markup with 3 decimals. | Clear validation; input remains available to correct. |
| Associations | Associate Pen with A and B. Add duplicate. Remove unreferenced association; attempt removing one after an order. | Duplicate blocked; unreferenced pair removable; referenced pair protected. |
| Reference deletion | Delete product/supplier/customer before and after creating references. | Only unreferenced records delete. History stays intact. |
| Order minimum | A minimum 200. Try 1 × 100. Then 2 × 100. | First rejected, second pending. Future transportation/landed costs cannot satisfy minimum. |
| Supplier filtering | Select A with only Pen associated; switch to B associated only with Book. | Product choices match supplier. Switching supplier clears the draft lines. |
| Duplicate order line | Add Pen twice at same price, then attempt different price. | Same price merges; conflicting price requires remove/re-add. |
| Receipt validation | Pending 2 × 100: enter landed 99, then 110. Try receiving twice and cancelling after receipt. | 99 rejected. 110 receives all once; second processing rejected. |
| Cancel | Create pending order, note stock, cancel twice. | First cancel leaves stock unchanged; second rejected. |
| FIFO and first pricing example | Receive 1 Pen at 100 from A, then 1 at 110 from B. Sell quantity 2 at 20%. | Shared unit 120, total 240, one row per supplier, both batches consumed. |
| Second pricing example | Receive 1 at 100 from A, 1 at 130 from B. Sell 2 at 20%. | Shared unit 130, total 260. |
| Third pricing example | Receive only cost 130, sell 1 at 20%. | Unit and total 156. |
| Repeated supplier | Receive A quantity 2 cost 100, B quantity 1 cost 110, A quantity 3 cost 130. Sell 4 at 20%. | One A row qty 3, one B row qty 1, both price 130. Remaining last A purchase qty 2. |
| Unsold cost exclusion | Receive cheap earlier stock and expensive later stock. Sell only the earlier quantity. | Later unused cost does not raise price. |
| Preview changes | Change markup; apply another quantity with Add / set quantity; select/remove a cart row. | Preview/total refresh; no stock changes before Save. |
| Stale price UI | Open two app instances on the test DB. Preview 1 from cheap batch in one; consume cheap stock from the other so next batch has a different price. Save original preview. Decline revised price, then accept and retry. | Revised per-product prices/total displayed; decline leaves cart intact and saves nothing; acceptance recomputes and commits. Service behavior is automated; dialog decline/accept by a human remains manual. |
| Full rollback | Run DatabaseChecks on the isolated schema. Its second-product counter mismatch intentionally fails after first-product writes. | No partial header/lines/stock persist; fixture restored; invariant checks pass. |
| Double clicks | Rapidly press order Save, receipt Confirm and sale Save; switch tabs during workers. | Active form disabled; one operation/record. No duplicate receipt. Physical rapid-click behavior remains manual. |
| Unavailable MySQL | Use a copied config with port 1, rather than stopping a service. Open app; retry after restoring correct config. Also test an incorrect password. | Friendly connection/authentication error, responsive UI, retained inputs, successful recovery after correct config. Dead-port recovery is automated; incorrect-password UI remains manual. |
| History | Filter from/through the same day; reverse bounds; enter invalid dd/MM/yyyy HH:mm values and switch history type. Inspect lines after later sales and renamed products. | The whole final minute included; invalid range/formats rejected; saved prices/totals unchanged; current catalog names appear. |
| Layout and input | Resize down to minimum size, navigate by keyboard, inspect long names, high-DPI scaling and dialogs. | Tables/forms usable; stock/history cells cannot be edited. Human accessibility/focus/layout review remains manual. |

## Stock audit SQL

Run as a SELECT-only audit in the intended database. Each query should return zero rows.

```sql
SELECT p.item_id, p.stock,
       COALESCE((SELECT SUM(sp.supplier_specific_stock)
                 FROM supplier_products sp WHERE sp.item_id=p.item_id),0) AS supplier_total,
       COALESCE((SELECT SUM(i.remaining_quantity)
                 FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id
                 WHERE i.item_id=p.item_id AND o.status='RECEIVED'),0) AS batch_total
FROM products p
HAVING stock <> supplier_total OR stock <> batch_total;

SELECT sp.item_id, sp.supplier_id, sp.supplier_specific_stock,
       COALESCE((SELECT SUM(i.remaining_quantity)
                 FROM purchase_order_items i JOIN purchase_orders o ON o.order_id=i.order_id
                 WHERE i.item_id=sp.item_id AND i.supplier_id=sp.supplier_id
                   AND o.status='RECEIVED'),0) AS batch_total
FROM supplier_products sp
HAVING supplier_specific_stock <> batch_total;

SELECT s.transaction_id, s.cart_total,
       COALESCE(SUM(i.quantity*i.price_per_unit),0) AS line_total
FROM sales s LEFT JOIN sale_items i ON i.transaction_id=s.transaction_id
GROUP BY s.transaction_id,s.cart_total
HAVING cart_total <> line_total;
```

Date/time presentation checks: order and sale tables show `dd/MM/yyyy HH:mm`, including
records with nonzero seconds/microseconds. History accepts this minute-level format.
Unchanged editable values retain original seconds/fractions; changing a value accepts a
new minute. Completed records remain read-only. Sample-name cleanup conflict, rollback,
repeat safety and seconds/microseconds FIFO are automated in SampleCleanupChecks; do not
create conflict fixtures in the application database by hand.
