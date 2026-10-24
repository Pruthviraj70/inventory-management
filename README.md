# Inventory Management — Java desktop course project

A local, single-user Swing application using introductory Java, JDBC and SQL. The project now lives in the dedicated `/Users/pruthviraj/codingProjects/OOPS/inventory-management` folder. The unrelated Titan research folder remains in its original workspace; existing repositories were not modified.

## Ready to run on this machine

Java **25.0.5**, Maven **3.10.0**, Homebrew MySQL **26.10.0**, and Connector/J **26.10.0** were verified. The existing MySQL service was started. `inventory_db`, its eight tables, and a data-only `inventory_app` account have been created. External `config/database.properties` is already configured and restricted to its owner. Do not replace it with the example on this machine.

```bash
cd "/Users/pruthviraj/codingProjects/OOPS/inventory-management"
mvn compile
mvn compile exec:java
```

Close the application window to exit. If MySQL is not running, use `brew services start mysql`, then Refresh. No installer, web server or deployment is involved.

```bash
mvn clean package
```

Produces `target/inventory-1.0.0.jar`. This normal JAR **does not bundle Connector/J**. Use Maven execution above; `java -jar` alone is not the supported launch command. Build plugins have their own build-time dependencies; Connector/J is the only application dependency. Its unused Protocol Buffers dependency is excluded.

## Setup on a new machine

Use Java 25, Maven 3.6.3+ and an existing local MySQL supporting enforced CHECK constraints (8.0.16+). The actual verified environment is listed above. Do not reinstall or reset an existing server. Run from this project directory.

1. Start your existing MySQL service if necessary: `brew services start mysql`.
2. Sign in as an administrator: `mysql --host=127.0.0.1 --user=root --password`. Enter the password at the prompt.
3. For a **new database/account**, execute the following with your own password. If these names already exist, inspect them first; do not delete or overwrite another project's database/account.

```sql
CREATE DATABASE inventory_db CHARACTER SET utf8mb4;
CREATE USER 'inventory_app'@'127.0.0.1' IDENTIFIED BY 'CHOOSE_A_NEW_LOCAL_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON inventory_db.* TO 'inventory_app'@'127.0.0.1';
```

4. Exit the MySQL console. Apply the schema as administrator from the project directory:

```bash
mysql --host=127.0.0.1 --user=root --password inventory_db < database/schema.sql
cp config/database.properties.example config/database.properties
chmod 600 config/database.properties
```

5. Edit the external file with the chosen account password:

```properties
db.url=jdbc:mysql://127.0.0.1:3306/inventory_db
db.user=inventory_app
db.password=YOUR_LOCAL_PASSWORD
```

The actual file is ignored by Git. Never commit it. `Properties.load` uses Java properties escaping: escape a literal backslash as `\\`; use `\uXXXX` for non-Latin-1 characters. The locally generated password uses safe ASCII characters. A different config path can be selected with `-Dinventory.config=/absolute/path/file.properties`.

`database/schema.sql` contains only `CREATE TABLE IF NOT EXISTS`, never `DROP DATABASE`, and never runs automatically. It preserves data, but **does not migrate or certify incompatible pre-existing tables**. Use a fresh database for this project rather than applying it to another application's tables. No stock-changing triggers are installed.

Optional normal Walk-in Customer record (run explicitly, never part of startup):

```bash
mysql --host=127.0.0.1 --user=inventory_app --password inventory_db < database/sample_data.sql
```

You can also add this ordinary customer through the Customers tab. The separate opt-in demo command below creates a stationery-shop dataset with natural names; nothing is loaded automatically at startup.

## Realistic demo data (explicit, repeat-safe)

```bash
./scripts/seed-demo.sh --dry-run
./scripts/seed-demo.sh
./scripts/seed-demo.sh --verify
```

Uses the configured local application database and existing credentials without displaying them. Adds **10 products, 4 suppliers, 6 customers, 14 associations, 8 purchase orders (5 received, 2 pending, 1 cancelled), and 5 completed sales**, with natural record names. The complete dataset is prevalidated and committed in one transaction through the existing purchase/receipt/sale services. A repeat execution identifies it from dated purchase/sale line fingerprints and skips it without duplicating records or changing stock again; incomplete or ambiguous sample history stops safely. This is an explicit command, never an application-startup action.

Quick walkthrough: search `Eraser` or `Desk Stapler` in Products (Eraser stock 0, Stapler stock 1). In History, filter **01/10/2026 00:00 through 31/10/2026 23:59**. Aditi Rao's notebook sale demonstrates landed ₹100/₹110 → shared ₹120; Rahul Shah's geometry-box sale demonstrates ₹100/₹130 → shared ₹130, both at 20% markup. Meera Joshi's pen sale combines two Campus purchases into one supplier sale row. Pending/cancelled orders contribute no stock. Open the records to inspect prices, quantities and supplier breakdowns.

See [demo plan, dates, atomicity and full walkthrough](docs/demo-data.md). The demo has been seeded locally and verified; see [actual seed results](docs/demo-seed-results.md). After you use the demo to make new sales/receipts, the seeder preserves those changes; `--verify` intentionally expects the original scenario and may then report a mismatch.

## Sample-name cleanup and date/time display

```bash
./scripts/clean-sample-names.sh --dry-run
./scripts/clean-sample-names.sh
```

This explicit maintenance command identifies only the original sample catalog IDs using their purchase/sale history, checks target names with MySQL's name collation, and removes only a leading legacy `DEMO - ` prefix. It does not rename the database. All renames share one transaction; unrelated names, IDs, stock, prices, relationships and full-precision timestamps are preserved. Repeat execution changes nothing. The local sample names have already been cleaned. Future seed runs create natural names and recognize existing samples without a prefix.

All displayed date/times use **`dd/MM/yyyy HH:mm`**. The shared table renderer retains raw full-precision timestamps in table models; standard clipboard copies use the same minute-only display text. `DateTimeField` accepts minute input and retains the original seconds/fractions when its displayed value is saved unchanged. History uses these fields; completed transactions remain read-only. MySQL still stores DATETIME(6), and FIFO still orders by the full arrival timestamp then order ID. No export feature exists in this version.

See [current update verification](docs/names-and-dates-results.md) for performed checks and preservation results.

## Using the application

- **Products:** add/edit/search/delete unreferenced records; inspect supplier stock. Stock is read-only.
- **Suppliers:** set lead time and minimum goods total; use Supplied products to associate existing products. Supplier-specific stock means stock in our shop.
- **Customers:** add/edit/search; use any ordinary customer, including Walk-in Customer.
- **Purchase Orders:** choose one supplier; only associated products appear. Add/merge quantities with the same supplier unit price. To change a line price, remove and re-add it. Save as pending; the goods subtotal must meet the supplier minimum. Receive the entire pending order with manually confirmed landed unit costs, or cancel it. Completed orders are immutable.
- **Sales:** select customer and product, enter quantity, then **Add / set quantity**. Reusing a product replaces its cart quantity. Select a cart row to load it into the quantity form. Every applied quantity change and markup change recalculates the preview. Save recalculates under locks; changed prices require explicit acceptance. Failed saves retain the cart.
- **History:** select sales or purchases and enter `dd/MM/yyyy HH:mm` bounds; the entire final minute is included. Purchase filtering uses the order date. View immutable historical lines.

Stock uses FIFO by receipt datetime, then order ID, across suppliers and individual purchases. For each product, only consumed batches determine the shared unit price:

`max(lowest allocated cost × (1 + markup / 100), highest allocated cost)`

The price is rounded to two decimals with HALF_UP before multiplying by quantity. The same price is saved on every supplier row for that product. Sale rows aggregate repeated purchases from the same supplier.

## Verification

No test framework is installed. Maven compilation/package alone does not run these custom `main` harnesses. Run:

```bash
./scripts/verify.sh               # 19 pure Java checks; no database required
./scripts/verify.sh --database    # isolated database checks + a separate-JVM persistence check
./scripts/verify.sh --gui         # additionally opens and drives real Swing windows
```

Database/GUI checks use ignored `config/test-database.properties`. They refuse schemas whose names do not match `inventory_test_*`, append uniquely named fixtures, and never clear a database. They temporarily alter **one newly created test fixture's** stock counter to force a rollback after earlier writes, then restore it in `finally`. Do not use application credentials for verification. GUI tests require a graphical desktop and close only their own JVM's windows. Screenshots are written to `target/gui-*.png`; `mvn clean` removes them.

The isolated database `inventory_test_20261025_001` and data-only account `inventory_test_runner` are already provisioned locally. For another installation, create a fresh `inventory_test_<suffix>` database, apply `schema.sql` as administrator, grant a separate account SELECT/INSERT/UPDATE/DELETE only on that database, and put those details in `config/test-database.properties`. No schema-changing test privileges are required.

See [verification results](docs/verification-results.md) and [manual test cases](docs/manual-test-cases.md) for performed checks and remaining manual checks.

## Scope and implementation

Packages: `model`, `dao`, `service`, `config`, `ui`; entry point `com.pruthviraj.inventory.Main`. All database work from the interface uses `SwingWorker`; component changes occur on the EDT. The active form is disabled during operations to prevent duplicate submission. Prepared SQL stays in concrete DAOs. Each transaction uses one fresh JDBC connection, passed to DAOs without transferring ownership.

Version one excludes authentication, roles, GST, payments, barcode scanning, returns, partial deliveries, multiple warehouses and deployment. Pending orders are cancelled and recreated instead of edited. Completed sales cannot be edited/deleted. Exact sale-to-purchase allocation history is **not retained separately**; remaining purchase quantities and supplier-level sale history are retained. Names in history show current catalog names; historical unit prices and totals remain fixed. The database account can modify data with SQL, so immutability and cross-row price equality are enforced by the application service, not a tamper-proof database audit system. The in-memory cart is lost on application exit.

Money uses DECIMAL(14,2); quantities use signed INT with bounds. Markup accepts 0–99999.99 with up to two decimals. Extra monetary input decimals round HALF_UP. The original workspace was not a Git repository, so no feature branch was possible here; the referenced repository with uncommitted changes was not modified. This repository is published privately on GitHub; local credentials, generated files and verification console logs remain excluded.

See [project decisions](docs/project-decisions.md) and [learning notes](docs/learning-notes.md).

Verified reference documentation: [Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html), [Maven dependency and protobuf exclusion](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html), [compiler plugin](https://maven.apache.org/plugins/maven-compiler-plugin/), [execution plugin](https://www.mojohaus.org/exec-maven-plugin/java-mojo.html). All pinned versions in `pom.xml` were also resolved and executed locally.

Relocation was verified on 25 October 2026; see [relocation verification](docs/relocation-verification.md) for the copy/permission audit and the successful checks from this location.

## Private GitHub repository

The versioned project includes source, SQL, tests, scripts, documentation and safe example
configuration. Real database/test configuration, environment files, generated output,
verification console logs and OS/editor files stay local through `.gitignore`. The
verification reports summarize performed checks; their raw console logs are intentionally
not published. After cloning, create your own local configuration from the example;
credentials are never included in the repository.
