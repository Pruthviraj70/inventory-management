# Natural names and minute display — 25 October 2026

## Applied changes

The configured local database remains `inventory_db`. The original sample seeder's
20 catalog records were identified using their dated purchase/sale fingerprints and
linked IDs. Their leading legacy prefix was removed in one transaction:
10 products, 4 suppliers and 6 customers now have natural names. All target names were
checked before the first update using MySQL name-column collations. No conflicts
were found in the live database.

Before commit, exact snapshots of all eight tables were compared with only the 20
identified name cells excluded. Every other value was identical, including IDs,
relationships, full stored timestamps, remaining quantities, prices, stock, and
transaction history. FIFO sequences for sample products were also identical. The
live database still contains **9 purchase orders: 8 original samples and the 1
additional user order**. It was neither deleted nor recreated. No database, account,
permission or credential was reset/changed.

A second cleanup renamed **0** records and skipped all 20 already-clean names.
A seeder rerun was compared before/after across every row in all eight tables and
changed **0** rows. Future seeds create natural names. Linked sample IDs are recovered
from immutable history, independently of their names. The one unused customer must
have its unique known natural/legacy name; ambiguity or missing identity stops safely
rather than replaying stock changes. No metadata table or dependency was added.

Date/time rendering now uses **dd/MM/yyyy HH:mm** for all application timestamps.
The shared JTable renderer covers purchase/history/detail tables while raw temporal
objects remain in the model. Standard table clipboard exports use the same display
format. History's editable fields accept minute-level bounds and include the entire
last minute. DateTimeField retains original seconds/fractions when unchanged text
is read for saving. Completed transactions remain read-only. There is no separate
file-export feature in this application.

MySQL still stores DATETIME(6), and the FIFO DAO code is unchanged: full arrival
precision then order ID determines allocation. Formatting is never used for SQL
storage or stock comparisons. Console build logs below retain Maven's own diagnostic
timestamps; they are build-tool output, not application date/time exports.

## Checks performed

| Group | Passed |
|---|---:|
| Existing full regression (19 pure + 56 MySQL + 1 separate-JVM + 13 GUI) | 89 |
| Date/time formatting, strict minute input, unchanged precision, table/clipboard | 16 |
| Cleanup conflicts, rollback, leading-only removal, identity and precise FIFO | 22 |
| Updated seed safety checks | 14 |
| Live Swing sample visibility and date-rendering checks | 6 |

- Java 25 compile and final `mvn clean package`: success.
- Cleanup preflight found 20 exact sample names and no target conflicts.
- Isolated conflict test used a differently cased natural customer name; cleanup
  stopped before any name update. An intentional late failure rolled back all
  twenty renamed cells.
- Controlled isolated fixtures confirmed middle-of-name `DEMO -` text and unrelated
  prefixed records were retained. Temporary name/conflict fixtures were rolled back.
- New received-stock fixtures in the existing isolated test database have arrivals
  within the same displayed minute, including different seconds and microseconds.
  FIFO selects the true earliest microsecond even with reverse order-ID order.
  Stored values round-trip exactly; the preview uses that batch's cost. No database
  was cleared or reset. These small valid stock fixtures remain in the test database.
- Updated seed checks confirmed concurrent/repeated runs, date validation and
  identity ambiguity handling without prefixes. Two initial-population checks in
  that legacy harness were explicitly skipped because its sample set already
  exists; the new cleanup harness separately tested full temporary natural-name
  population and rollback.
- The live app's Products, Suppliers, Customers, Purchase Orders, Sales choices
  and October History loaded the same sample IDs under natural names. All rendered
  temporal cells were checked against the shared minute formatter. The screenshots
  `target/demo-products.png` and `target/demo-history.png` were regenerated and the
  History screenshot was visually reviewed. The longer date fields initially crowded
  the action row; a two-row filter layout was added and the final screenshot confirms
  both Filter / Refresh and View details remain visible. The visibility harness saved no extra
  purchase or sale.
- Credential/configuration contents and permissions, schema.sql, Maven dependencies
  and the FIFO DAO source matched pre-change hashes. Credentials were not displayed.

Build (local verification log, excluded from Git), regression (local verification log, excluded from Git),
date/time (local verification log, excluded from Git), cleanup/FIFO (local verification log, excluded from Git),
seed safety (local verification log, excluded from Git), preflight (local verification log, excluded from Git),
applied cleanup (local verification log, excluded from Git), repeat cleanup (local verification log, excluded from Git),
repeat seed (local verification log, excluded from Git), visibility (local verification log, excluded from Git),
live audit (local verification log, excluded from Git).

## Commands

```bash
cd "/Users/pruthviraj/codingProjects/OOPS/inventory-management"
mvn compile exec:java
./scripts/clean-sample-names.sh --dry-run
./scripts/clean-sample-names.sh
./scripts/seed-demo.sh
./scripts/verify.sh --gui
```

Cleanup and seeding are explicit commands and never run at app startup. The cleanup
and seed commands above now skip the completed dataset. No blocker remains. Existing
human-only accessibility/physical-click checks are not claimed as newly performed.
The project has no Git repository; no Git initialization, commit, push, merge or
deployment occurred.
