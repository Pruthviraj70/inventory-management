# Relocation verification — 25 October 2026

The inventory application was copied from `/Users/pruthviraj/Documents/ChatGPT/OOPS Project` into the dedicated
`/Users/pruthviraj/codingProjects/OOPS/inventory-management` folder. The destination did not exist at inspection.
No existing repository was overwritten or merged, and no Git operation was performed.

Before changing anything, all 95 project files were copied and verified by SHA-256
contents and permission modes. Both ignored configuration files were included and
remain byte-for-byte identical with mode 0600. Credentials were not displayed or changed.

From the destination, Java 25.0.5 compilation (`mvn compile`) and clean packaging
(`mvn clean package`) both passed. `./scripts/verify.sh --gui` completed successfully:
19 pure checks, 56 existing isolated-database checks, 1 separate-JVM persistence check,
and 13 visible Swing checks, for 89 assertions. This command includes the pure and
`--database` verification paths. Existing accounts/databases were reused; no database
was reset, no account was recreated, and no credential changed. Only normal verification
fixtures were appended to the existing isolated test database. No production test data
was loaded. The expected dead-port error/recovery check passed.

README launch paths and saved logs now use the destination; the verification script
already uses its own relative project root. Application source, Maven configuration,
scripts, database scripts and local configuration were otherwise preserved exactly.

The complete original project manifest and unrelated files were checked again after
verification, before removal. Only the project entries were removed from the source:
`.gitignore`, `README.md`, `pom.xml`, `config`, `database`, `docs`, `scripts`, `src`, `target`.
The unrelated `Titan_Management_Research` directory and `.DS_Store` remain unchanged.
No commit, push, merge, deployment, database reset, or credential rotation occurred.

Launch:

```bash
cd "/Users/pruthviraj/codingProjects/OOPS/inventory-management"
mvn compile exec:java
```

See build log (local verification log, excluded from Git), verification log (local verification log, excluded from Git), and
compilation log (local verification log, excluded from Git). Existing human-only checks remain in
[manual-test-cases.md](manual-test-cases.md); relocation did not claim them as performed.
