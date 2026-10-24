# Stationery-shop demo dataset

The explicit demo command uses the configured application database through the existing `Database` Properties loader. It never reads credentials into a shell command or logs them, changes accounts, runs schema setup, or loads data at normal application startup. No dependency or table was added.

```bash
cd "/Users/pruthviraj/codingProjects/OOPS/inventory-management"
./scripts/seed-demo.sh --dry-run   # validate the whole plan and inspect sample identities; no writes
./scripts/seed-demo.sh             # seed once, or skip an existing complete demo
./scripts/seed-demo.sh --verify    # read-only strict comparison with the original demo scenario
```

All new sample records use natural names. Existing sample IDs are identified by dated purchase/sale line fingerprints and their relationships; a name prefix is not required. Database IDs come from generated keys/history lookup. Plan labels R1–C8 and S1–S5 are explanatory labels, not assumed database IDs or extra columns. The seed command prints their actual generated IDs.

## Atomicity, repeat execution and preservation

Before opening a connection, `DemoPlan.validate()` validates every natural name, association, positive quantity, supplier goods minimum, landed cost, receipt/sale date, FIFO allocation, expected selling price and final stock balance. It simulates all five sales in memory using the actual pricing algorithm and compares with explicit expected prices/totals and final stocks.

The command then opens one connection to the configured database, acquires a database-specific MySQL advisory lock, and starts one READ COMMITTED transaction. The entire catalog, associations, eight orders, five receipts, cancellation and five sales use that one connection. A failure before commit rolls back all rows and stock changes. No partial-resumption protocol is needed. Failed transactions may leave normal auto-increment gaps; IDs are never assumed or reused.

The existing services gained caller-owned-connection overloads and optional explicit datetimes. The regular UI methods still open their own transactions and use MySQL's current timestamps exactly as before. Explicit dates reject arrival before order and sales before their consumed stock arrived. DAOs do not close the shared connection. Catalog inserts reuse CatalogDao after plan validation; purchase creation, receipt, cancellation, preview and sale all reuse their normal services.

All pre-existing rows in the eight application tables are snapshotted in memory before population and compared before commit. No existing row is reused or updated. The seeder independently audits global stock/total/foreign-key consistency before and after writing. If existing data is already inconsistent, it stops; it does not repair it. Nothing is dropped, truncated or recreated.

The advisory lock remains held until after commit or rollback, preventing two seed commands from racing even though catalog names have no unique constraint. Existing samples are identified by all eight exact order datetimes and immutable purchase line quantities/prices/costs, then all five exact sale datetimes, markup/totals and supplier-level sale lines. Product, supplier and five customer IDs are derived from those relationships, independently of current names and remaining stock. Extra user-created orders are preserved and do not become sample records.

The sixth customer, Kavya Desai, has no transaction history; it must match its unique known natural name or legacy full name. If that unused customer is missing, renamed beyond recognition or ambiguous, the seeder stops safely instead of guessing or recreating it. A renamed linked product/supplier/customer still resolves through history. A new installation with no sample history also checks for existing planned natural/legacy names and refuses to merge with them. Missing/ambiguous/modified original history stops without replaying stock changes. No metadata table or hard-coded database ID is used.

For the old prefixed dataset, run `./scripts/clean-sample-names.sh --dry-run`, then `./scripts/clean-sample-names.sh`. It checks every target against existing records using the database name collation before any update, removes only the leading prefix from the 20 identified sample IDs, and verifies exact preservation of every other value in all eight tables plus FIFO sequences. Matching text in the middle of names and unrelated prefixed records are untouched. All updates commit together; conflicts/errors roll everything back. Both cleanup and seeding are harmless to repeat. Neither runs automatically at application startup.

`--verify` compares the exact initial scenario, including pending statuses and remaining quantities. It is expected to report a mismatch after you sell additional demo stock or receive one of its pending orders. Running the normal seed command still does not replenish/reset it. An ambiguous or unrecognizable unused customer causes a safe stop. Close other writers while initially seeding; the advisory lock coordinates seeder commands, not arbitrary manual SQL.

## Records

10 products, 4 suppliers, 6 customers (one unused customer for browsing), 14 supplier-product pairs, 8 purchase orders with 22 lines, and 5 completed sales with 15 supplier-level lines. The dates are fixed in October 2026; use 01/10/2026 00:00 through 31/10/2026 23:59 in History at any later date.

| Supplier (natural names) | Lead time | Minimum goods total |
|---|---:|---:|
| Campus Stationery Wholesale | 2 days | ₹500 |
| City Paper and Office | 3 days | ₹800 |
| Scholars School Supplies | 4 days | ₹600 |
| Metro Shop Essentials | 1 day | ₹300 |

| Order | Supplier | Ordered → arrived | Status | Goods subtotal | Allocated extra costs |
|---|---|---|---|---:|---:|
| R1 | Campus | 01/10/2026 09:00 → 03/10/2026 10:00 | RECEIVED | ₹1,220 | ₹210 |
| R2 | City | 02/10/2026 09:00 → 05/10/2026 10:00 | RECEIVED | ₹2,680 | ₹200 |
| R3 | Campus | 06/10/2026 09:00 → 08/10/2026 10:00 | RECEIVED | ₹765 | ₹110 |
| R4 | Scholars | 05/10/2026 09:00 → 09/10/2026 10:00 | RECEIVED | ₹960 | ₹125 |
| R5 | Metro | 09/10/2026 09:00 → 10/10/2026 10:00 | RECEIVED | ₹530 | ₹70 |
| P6 | City | 21/10/2026 09:00 → — | PENDING | ₹1,450 | Not received |
| P7 | Scholars | 22/10/2026 09:00 → — | PENDING | ₹850 | Not received |
| C8 | Metro | 20/10/2026 09:00 → — | CANCELLED | ₹650 | Not received |

Allocated extra costs equal `SUM((landed_unit_cost-order_price) × quantity)`. They are already included in each received line's landed unit cost, never counted toward supplier minimums. The five received orders total ₹6,155 of goods plus ₹715 of extra costs. Pending/cancelled lines retain zero remaining quantity and null landed cost.

| Sale / date / customer (natural names) | Contents at saved selling unit prices | Total |
|---|---|---:|
| S1 / 11/10/2026 16:00 / Walk-in Customer | 10 pens × ₹12, 5 pencils × ₹6, 5 erasers × ₹4.80 | ₹174.00 |
| S2 / 12/10/2026 16:00 / Aditi Rao | 8 A5 notebooks × ₹120 | ₹960.00 |
| S3 / 13/10/2026 16:00 / Rahul Shah | 5 geometry boxes × ₹130 | ₹650.00 |
| S4 / 14/10/2026 16:00 / Meera Joshi | 50 pens × ₹12, 30 erasers × ₹5 | ₹750.00 |
| S5 / 16/10/2026 16:00 / Sunrise Tuition Centre | 7 staplers × ₹108, 2 paper reams × ₹300, 3 glue sticks × ₹33.60, 2 tape rolls × ₹48, 4 highlighters × ₹36 | ₹1,696.80 |

Every sale has one 20% markup. All five receipts precede the first sale. Kavya Desai is the sixth customer, with no historical sale.

## FIFO and pricing walkthrough

1. In Products, search familiar natural names such as `Eraser`, `Stapler` or `Notebook`. Eraser is sold out; Desk Stapler has 1 unit, Blue Ball Pen has 5. Supplier stock breakdown shows stock held in our shop.
2. In Suppliers, select Campus and inspect Supplied products. The notebook is also supplied by City; geometry boxes/erasers also come from Scholars; highlighters are associated with City and Scholars.
3. In Purchase Orders, identify R1–R5 by printed seed IDs, dates and supplier. Open the lines: R1 and R3 are repeated Campus purchases of pens/pencils/notebooks. The earlier R4 creation date does not put it before R3's earlier arrival in FIFO.
4. In History, filter October 2026 and open S2: five Campus notebooks at landed ₹100 plus three City notebooks at landed ₹110 produce a shared ₹120 price, stored on two supplier rows. Remaining notebook quantities are R1=0, R2=3, R3=4.
5. Open S3: three Campus geometry boxes at landed ₹100 plus two Scholars boxes at landed ₹130 produce the shared ₹130 price. R1=0 and R4=3 remain.
6. Open S4: 30 pens from R1 and 20 from R3 become **one Campus sale row of 50 pens** at ₹12. Only R3's final 5 pens remain. The 30 erasers span two suppliers at a shared ₹5 and finish both batches.
7. Pending P6/P7 and cancelled C8 add no available stock. Inspect them without receiving pending orders if you want strict demo verification to keep passing.
8. Open Sales to browse the natural customer/product names. Save nothing to retain the original scenario. If you choose to save a new sale, it uses the ordinary application behavior and permanently changes the demo balances; rerunning the seed will not reset them.

Final stocks: Pen 5; Pencil 45; Notebook 7; Geometry Box 3; Stapler 1; Paper Ream 4; Glue Stick 7; Eraser 0; Highlighter 8; Packing Tape 6. Total **86 units**. Historical sale totals sum to **₹4,230.80**.

## Verification commands

```bash
./scripts/seed-demo.sh --verify
./scripts/verify.sh --gui
mvn -q test-compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.demo.DemoSeedChecks -Dexec.classpathScope=test
mvn -q test-compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.ui.DemoVisibilityChecks -Dexec.classpathScope=test
```

The existing verification script and DemoSeedChecks write only to the existing isolated `inventory_test_*` database. They never reset it. DemoSeedChecks verifies full rollback, concurrent/repeated seed safety and invalid-date/identity-conflict failures. The full-population rollback setup is skipped on subsequent checks if that isolated demo already exists; its committed test records are preserved. DemoVisibilityChecks uses the configured application database read-only, opens the actual Swing panels, and confirms identified sample rows and October history. It writes `target/demo-products.png` and `target/demo-history.png`. No additional purchase or sale is saved by this visibility check. Displayed timestamps use dd/MM/yyyy HH:mm; the underlying DATETIME(6) values and FIFO calculations retain full precision.

Additional checks for this update:

```bash
mvn -q test-compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.ui.DateTimeChecks -Dexec.classpathScope=test
mvn -q test-compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.demo.SampleCleanupChecks -Dexec.classpathScope=test
```

The cleanup harness requires the existing isolated test database. Its controlled conflict,
legacy-name, unrelated-prefix and middle-prefix fixtures are rolled back. It also appends
small valid stock fixtures proving that arrival seconds/microseconds still determine FIFO
when timestamps render as the same minute. It never resets a database.
