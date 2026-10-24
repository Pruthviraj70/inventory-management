# Learning notes for rebuilding this project later

This is a short map of concepts and code locations, not the complete teaching guide.

1. **Start with data rules.** Read `database/schema.sql`. Explain primary keys, composite keys, the order-supplier unique key, foreign keys, RESTRICT and CHECK. Draw the eight entities before writing forms. Product stock is a cached aggregate, so Java must update it together with supplier and purchase counters.
2. **Model plain data.** `model/Models.java` uses records, which are immutable data classes with generated constructors and accessors. `List.copyOf` prevents a quote's allocation list from being modified after creation. Records need no inherited base entity.
3. **Connect through JDBC.** `config/Database.java` uses Properties, DriverManager and Type 4 Connector/J. Maven places the driver on the runtime classpath; JDBC discovers it without `Class.forName`. Credentials are external. A configuration object is safe to share; an open Connection is not.
4. **Write one DAO at a time.** Study CatalogDao prepared statements, result mapping and Sql generated-key handling. Bind values with `?`; do not concatenate user-entered SQL. Try-with-resources closes result sets/statements. The caller, not the DAO, closes a transaction connection.
5. **Separate validation from SQL.** Rules checks names, bounds and BigDecimal values. Database constraints independently reject invalid SQL. Both layers matter: friendly feedback in Java and protection in MySQL. Use `new BigDecimal("100.10")`, never `new BigDecimal(100.10)` from a binary double.
6. **Understand a transaction.** PurchaseService.create saves header and lines together. Receive updates three stock representations and the status together. Follow Database.transaction: disable autocommit, run, commit, catch and rollback. Generated ID gaps after rollback are normal.
7. **Solve FIFO without UI.** Pricing.allocate walks an already ordered list of batches, takes `min(needed, remaining)`, and stops when enough units are allocated. The database supplies arrival order. Remaining stock must be tracked per purchase line, not per supplier's oldest purchase date.
8. **Calculate the unusual pricing rule explicitly.** Allocated costs 100/110 at 20% produce 120; 100/130 produce 130; 130 alone produces 156. The minimum normal price and maximum landed cost come only from consumed batches. Round the final unit price first. A line total is that rounded price times its integer quantity.
9. **Use collections for coordination.** A TreeMap sorts product IDs for deterministic locks, merges duplicate quantities and combines allocations by supplier. An ArrayList holds ordered lines. Read SaleService.save before changing any stock rule.
10. **Protect against stale screens.** A preview is information, not a reservation. Locks and a fresh quote at Save are necessary. Throw a revised quote, roll back/release locks, then ask for acceptance. Never leave database locks open while waiting for a human dialog.
11. **Build Swing on the EDT.** Main uses invokeLater. Layout managers size controls. JTable's model stores rows; a view row index must be converted after sorting. Tasks runs JDBC off the EDT with SwingWorker and updates controls in done. Blocking SQL on a button listener would freeze repaint and input.
12. **Verify behavior, not only compilation.** PureChecks tests arithmetic without MySQL. DatabaseChecks uses a separate schema for constraints, competing saves and rollback. PersistenceCheck starts a new JVM. GuiChecks drives real visible forms and tests a dead-port error/recovery without stopping MySQL. Read manual-test-cases for cases that require further human review.

Suggested independent rebuild order: schema → config → catalog model/DAO/service → catalog UI → pending purchases → receipt transactions → pure FIFO/pricing → sales save/rollback → sales UI → history → isolated verification → documentation.

Useful Java topics to review: encapsulation, records vs mutable classes, composition, collections/generics, exceptions, lambdas used for callbacks, try-with-resources, BigDecimal rounding, LocalDate vs LocalDateTime, Swing event listeners and worker threads. SQL topics: joins, aggregate queries, indexes, transactions, isolation and row locks. No ORM, web framework or connection pool is needed for this scope.

Presentation precision is separate from storage precision: DateTimes/DateTimeField render
and parse `dd/MM/yyyy HH:mm` without rounding existing database values. Compare full
LocalDateTime objects in FIFO, never their display strings. Identity is separate from a
record's editable name: the sample cleanup resolves history relationships before changing
names, preflights conflicts and verifies every non-name value before commit.
