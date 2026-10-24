# Verification results — 25 October 2026

These are the original implementation results, before optional demo seeding. The current application database now contains the explicitly requested demo; see [demo seed results](demo-seed-results.md) for its later verification.

## Final result

The final `./scripts/verify.sh --gui` run completed with exit code 0:

| Check group | Passed |
|---|---:|
| Pure Java validation, FIFO and pricing | 19 |
| Real MySQL integration checks in `inventory_test_20261025_001` | 56 |
| Persistence read in a separate JVM | 1 |
| Visible Swing workflow and recovery checks | 13 |
| Total assertions | 89 |

`mvn compile` and `mvn clean package` succeeded. A resolved runtime dependency-tree audit showed only `com.mysql:mysql-connector-j:26.10.0`; protobuf was absent. The latter produced `target/inventory-1.0.0.jar`. Custom harnesses are invoked explicitly by the script, not by Surefire. The final build compiled 20 application source files and 4 harness files with Java release 25. See build console (local verification log, excluded from Git) and verification console (local verification log, excluded from Git).

The database checks include all three requested price examples, FIFO arrival ordering and order-ID ties, repeated suppliers, supplier-line aggregation/shared unit prices, minimum goods subtotal, both composite foreign keys, referenced deletions, receipt exactly once, cancellation neutrality, stale-price acceptance, failure after earlier writes with complete rollback, competing sales, stock invariants and fixed historical prices after later receipt/markup changes.

Visible Swing automation used actual forms/events on the EDT. It created a pending order, merged duplicate product selections, filled and submitted landed costs, applied sale quantities, changed markup and observed automatic repricing, retained the cart after insufficient stock, saved a sale, read history, and recovered from a simulated unavailable MySQL port. No unrelated service was stopped. The deliberate unavailable-port check prints a sanitized CommunicationsException diagnostic; that is an expected passing case, not a failed run. Saved GUI images are `target/gui-sale-preview.png` and `target/gui-history.png`; both were visually reviewed. Cleaning target removes these images.

## Environment and isolation

- OpenJDK 25.0.5 (Homebrew), Maven 3.10.0, MySQL server/client 26.10.0, MySQL Connector/J 26.10.0.
- Exactly eight tables in each newly created inventory database.
- Final privilege audit: application account has SELECT/INSERT/UPDATE/DELETE on `inventory_db.*`; test account has the same permissions only on `inventory_test_20261025_001.*`. Neither has DDL/admin privileges.
- Existing `csc2131_lab` and unrelated files/services were not modified. The referenced repository's status remained identical to the initial inspection.
- Test data stays in the test database, appended with unique names. The application database has no test purchases/sales. Startup never loads sample data.
- Owner-readable, ignored local config files contain generated scoped account passwords. Temporary administrator credential file was removed after provisioning/audit. No password is recorded in these reports.

## Corrections during verification

The first schema loading attempt encountered MySQL client's SOURCE parsing of a path with spaces; applying the same schema text via input redirection completed setup without deleting anything. The initial rollback test attempted to create a test-only trigger, which MySQL refused because binary logging requires additional privileges. It was replaced with a temporary mismatch in an isolated fixture's stock counter; no trigger, SUPER privilege or global configuration change remains. A persistence check run before its marker had been produced correctly failed; the final sequential script produced the marker and passed in a separate JVM. All reported assertion counts are from the final successful script run.

## Remaining manual checks and limitations

No functional or credential blocker remains. OS keyboard focus, physical rapid double-clicks, broad accessibility/high-DPI resizing, invalid-password UI, and the stale-price dialog's human decline/accept interaction remain on the precise [manual checklist](manual-test-cases.md). The underlying changed-price/acceptance service path is automated. Do not infer these manual checks passed from the automated counts.

The normal JAR does not include Connector/J; Maven execution is the supported version-one launch path. Exact sale-to-purchase allocation history is not retained separately, as specified. There was no Git repository in this workspace, so no branch could be created here; the separate existing repository was preserved. No push, merge or deployment occurred.
