# Project decisions

Recorded on 25 October 2026 for a later teaching guide.

## Workspace and tools

The requested current workspace contained an unrelated Titan research folder and no Git metadata. The separately mentioned `/Users/pruthviraj/codingProjects/OOPS/oops-project` had a Hello World entry point, an empty DB class, Java 25 configuration and uncommitted changes. It was inspected, not copied over or modified. The project was initially built at that workspace root and later relocated by copy–verify–remove to `/Users/pruthviraj/codingProjects/OOPS/inventory-management`. The unrelated research folder remains at the original location. No Git repository was initialized merely to fabricate a branch; no push or merge occurred.

Kept Java 25 and existing Homebrew MySQL 26.10.0. Connector/J 26.10.0 was present locally, checked against official current documentation, resolved by Maven and exercised against the server. Plugin versions are pinned in the POM and verified by compile/package/execution. Excluded protobuf because only classic JDBC is used. No added application dependency or test framework.

## Structure and OOP

`Models` contains small immutable records: Java-generated constructors/accessors/equality keep data objects short. DAOs own prepared SQL and mapping. Services own validation and business transactions. `Database` owns connection creation and transaction boundaries. UI panels depend on services, never SQL. There is one shared database configuration object, but every operation opens a fresh connection; connections are never global.

Three concrete DAOs and three services match catalog, purchase and sale operations. `Sql` only removes repeated JDBC binding/update/key handling. It is not a generic repository. `Database.Work` is one small callback to avoid duplicating rollback code. `CatalogPanel.Kind` shares the nearly identical catalog forms without artificial entity inheritance. `Tasks` centralizes SwingWorker, temporary form disabling and error reporting.

## Eight-table schema

Kept exactly the requested tables. IDs and quantities use INT; monetary values use DECIMAL(14,2); markup uses DECIMAL(7,2). Explicit composite unique `(order_id,supplier_id)` permits a matching composite child foreign key. The purchase child also references `(item_id,supplier_id)`. Both prevent a valid product-supplier pair from being inserted under the wrong order supplier. Foreign keys use RESTRICT; no history cascades.

CHECK constraints cover names, nonnegative stock/money, positive quantities, remaining bounds, statuses and valid arrival/nullability. Cross-table invariants (received lines vs totals) and one shared price across sale rows are enforced transactionally in services. MySQL CHECK cannot query other rows/tables. No business triggers duplicate service stock changes. Schema setup is explicit and non-destructive; it is not a migration engine.

No extra allocation table: FIFO consumes purchase line remaining quantities. Exact historical sale-to-purchase links are deliberately unavailable. Saved supplier-level sale rows still preserve quantity and actual price.

## Transactions and locks

Transactions use READ COMMITTED so reads after waiting for locks see committed stock. Receipt locks its pending header, validates all landed costs, then locks **all product rows in ascending ID**, all association rows by product/supplier ID, and updates lines and totals. Sale locks its customer for existence, all product rows ascending, association rows ascending, then eligible received purchase lines. Pending orders are not candidates for sale allocation. An order header lock prevents duplicate receipt/cancellation. Receipt and sale serialize through product locks; competing sales recheck after waiting.

FIFO line queries sort by `arrived_datetime, order_id`, not supplier or order creation time. Only received, positive-remaining lines qualify. The same supplier can appear several times in allocations; a TreeMap combines its quantities into one sale row. All DAOs receive and leave open the transaction's connection. Any validation/SQL/runtime failure rolls back before connection closure. INT/DECIMAL overflow fails the operation rather than wrapping stock.

## Prices and acceptance

Allocate first, then calculate a shared product price from only the batches consumed by this sale. Compute normal prices with exact BigDecimal multiplication, choose the lower normal price vs highest landed cost using MAX, round the result HALF_UP to two decimals, then multiply by quantity. Zero prices/costs are allowed because the specification requires nonnegative amounts.

Purchase duplicate selections merge only when their rounded supplier unit prices agree; conflicting duplicates ask the user to remove/re-add the line rather than silently change agreed prices. Supplier minimum uses goods alone. Landed unit costs are manually allocated/confirmed at receipt and cannot be lower than rounded supplier prices.

A Quote contains the displayed per-product quantities/prices and total. Save recomputes after acquiring locks. A mismatch throws `PriceChangedException` before writes. Transaction rollback releases locks before the UI opens an acceptance dialog. Acceptance retries the entire save; another cost change will request acceptance again. Supplier-source changes with identical displayed product prices need no new approval.

## UI and errors

One JFrame has six tabs; modal JDialogs provide explicit forms/details. All tables are read-only, including stock/history. Quantity edits are applied with Add / set quantity and refresh the preview; markup edits use a short Swing Timer to avoid querying for every keystroke. SwingWorker performs all GUI database work. Active form controls are disabled and guarded by a busy flag. Unrelated tabs may still be used; service locks preserve correctness.

Customer cart stays in memory, never written before Save, and is cleared only after successful commit (or explicit Clear). Failed operations retain forms/cart. SQL errors show safe explanations; console diagnostics print exception type, SQLState, vendor code and application stack frames, never driver messages that could contain credentials/URLs.

## Provisioning and verification

Created only `inventory_db` and `inventory_test_20261025_001` with separate local accounts. Existing `csc2131_lab` was left alone. Both accounts ultimately have SELECT/INSERT/UPDATE/DELETE on their own database only. Administrator credentials were used only for provisioning and removed from temporary files afterward. Application/test passwords live only in ignored owner-readable config files.

An early test-only trigger approach was rejected by MySQL's binary-logging privilege requirements. Replaced it with a temporary mismatch in a newly created isolated fixture counter; its second-product update fails after first-product writes, proving full rollback. No SUPER grant or global server change was made. The harness restores that fixture counter in finally. Test schema privileges used during initial provisioning were revoked.

The application account can issue SQL directly, so completed-record immutability is an application rule, not protection against database administrators or manual SQL. Catalog name changes appear in historical views, while stored monetary prices remain immutable through application operations.

## Explicit demo seeding

Added an optional DEMO-prefixed stationery dataset with fixed October 2026 dates. Services now expose overloads taking a caller-owned transaction connection and optional explicit datetimes, while existing Swing wrappers retain their previous transaction ownership and database-current timestamps. This allows one atomic seed transaction without duplicating purchase/FIFO/sale logic or adding metadata tables. Backdated receipt/sale validation rejects impossible chronology. Catalog inserts reuse the concrete DAO after full plan validation.

The full plan is simulated before writes, then independently checked against persisted rows before commit. A MySQL advisory lock is held until commit/rollback. Exact reserved names plus dated order/sale identities detect repeat execution without assuming IDs. Partial/conflicting namespaces stop rather than merge or repair. Existing rows are compared before commit; accounts, permissions, credentials and configuration are untouched. See demo-data.md for commands and the reproducible walkthrough.

## Natural sample names and minute display (current behavior)

The earlier prefix-based identity description above is historical. Sample IDs now come
from exact dated purchase/sale line fingerprints and their linked IDs; names are not the
identity of linked records. The unused sixth customer requires its unique known natural
or legacy name because no history references it. Missing/ambiguous identity stops safely.
No new table, dependency or assumed database ID was added. An explicit transactional
cleanup removes only leading legacy prefixes from proven sample IDs, checks MySQL name
collations before updates, compares every non-name value and preserves unrelated names.

DateTimes is a presentation utility and DateTimeField preserves its original full value
when unchanged minute text is read. A shared JTable renderer paints the requested format
while table models, JDBC values and FIFO retain full precision. History now accepts minute
bounds, including the entire final minute. No timestamp-changing SQL was introduced.
