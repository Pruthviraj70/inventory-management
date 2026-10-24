# Demo seeding results — 25 October 2026

Historical initial-seed results. The sample names were subsequently cleaned to natural names; see [current name/date update results](names-and-dates-results.md). Earlier console logs retain their original output.

Seeded the existing configured `inventory_db` locally with `./scripts/seed-demo.sh`.
Initial live inspection found the eight expected tables with zero rows. Configuration,
credentials, account grants and schema were not changed. No startup seeding was added.
The whole dataset was validated before writing and committed in one transaction.

| Table | Added rows |
|---|---:|
| products | 10 |
| suppliers | 4 |
| customers | 6 |
| supplier_products | 14 |
| purchase_orders | 8 (5 RECEIVED, 2 PENDING, 1 CANCELLED) |
| purchase_order_items | 22 |
| sales | 5 |
| sale_items | 15 |
| Total | 84 |

All catalog names use `DEMO - `. Actual IDs were obtained from generated keys and are
listed in seed output (local verification log, excluded from Git), rather than assumed by the implementation.
All received stock arrives before any demo sale. Final remaining inventory is 86 units;
stored historical sales total ₹4,230.80. The notebook sale stores ₹120 per unit for
allocated landed costs ₹100/₹110; the geometry-box sale stores ₹130 for ₹100/₹130.
The stapler has one unit left; erasers are sold out. Pending/cancelled orders contribute
zero remaining stock. [Full scenario and walkthrough](demo-data.md).

## Performed checks

- `mvn compile` and `mvn clean package`: Java release 25 build/package passed.
- `./scripts/seed-demo.sh --dry-run`: full plan, existing data and reserved namespace
  validated before the initial write; no rows changed.
- Isolated `DemoSeedChecks`: **16 assertions passed**, including full population rollback,
  simultaneous seed serialization, repeated execution with identical row snapshots,
  exact FIFO/pricing replay, invalid temporal inputs and conflicting names. No test
  database was reset or cleared. Log (local verification log, excluded from Git).
- Existing `./scripts/verify.sh --gui`: **89 assertions passed** (19 pure, 56 isolated
  MySQL, one separate-JVM persistence and 13 Swing checks). This confirms normal UI
  service wrappers retain their behavior. All write tests remained in the existing
  isolated test database. Log (local verification log, excluded from Git).
- `./scripts/seed-demo.sh --verify`: every expected demo order/header/line/status/date,
  supplier relationship, saved selling price, sale total, supplier stock and product
  stock matched the prevalidated simulation. All received purchase remaining quantities
  matched FIFO, including repeated suppliers. Global foreign-key/stock/cart audits
  passed. Log (local verification log, excluded from Git).
- Repeated the same seeder implementation against configured `inventory_db`; it returned
  skip. Every row of all eight tables was compared before/after and was **identical**.
  Log (local verification log, excluded from Git).
- Real Swing panels using the configured application database: **6 visibility checks
  passed**, covering products/stocks, suppliers, customers, purchase orders, sale choices
  and October sale history. This read-only walkthrough saved no extra order or sale.
  Log (local verification log, excluded from Git). `target/demo-products.png` and
  `target/demo-history.png` were visually inspected.
- Final configuration, credential-file permissions, Maven dependency configuration,
  schema script and gitignore hashes matched their pre-change snapshots. No dependency,
  table, account, permission or credential was added/changed.

Initial execution skipped **0** records. Repeat execution skipped the complete existing
demo and changed **0** rows; all 84 application rows remained intact. Partial/conflicting
reserved namespaces stop without merging. No blocker remains. Human keyboard-focus,
rapid physical click and accessibility checks from the original checklist are not
claimed as newly performed.

This project has no Git repository; no branch initialization, commit, push, merge or
deployment occurred. Existing unrelated files were not changed.
