# Ticket #12 — JPA Entities + MapStruct Mappers

## What
JPA entities (`TransactionEntity`, `MerchantEntity`, `TerminalEntity`), Spring Data JPA
repository interfaces, MapStruct bidirectional mappers (`TransactionMapper`,
`MerchantMapper`), Flyway V12 migration for BIN routing columns, and unit tests for
both mappers — completing the adapters persistence layer.

## Why
Closes #12. Fulfils the repository chain required by §2.4: domain port → adapter →
mapper → JPA entity → Spring Data JPA interface. Zero Java files existed in the
adapters module before this ticket.

## Design Decisions

- **All-`default`-method mapper interfaces**: `Transaction` uses fluent accessors
  (`domain.id()`, `domain.merchantId()`) with no `get` prefix. MapStruct's
  `@Mapping(source = "merchantId.value")` path resolution requires JavaBeans-style
  `getMerchantId()`. Converting both mapper interfaces to all hand-written `default`
  methods avoids this entirely. MapStruct still generates a Spring `@Component` impl
  (empty class with no abstract methods) — `new TransactionMapperImpl()` / `Mappers.getMapper()`
  both work, and the impl is picked up correctly by Spring's component scan.

- **No `@GeneratedValue` on UUID PKs**: Domain always constructs the UUID before save.
  `@GeneratedValue` would require `gen_random_uuid()` to fire, which is already the
  Postgres default on the column — no annotation needed on the Java side.

- **`MerchantProfile` limits hardcoded to INR in `toDomain`**: The `merchants` table has
  no currency column; all stored limits are INR. The mapper hardcodes
  `Currency.getInstance("INR")` rather than adding a redundant column.

- **V12 migration rather than altering V2**: `bin_table` gained `issuer_bank_code` and
  `nfs_eligible` columns when `BinInfo` was extended in PR #29. Flyway's immutability
  rule prohibits editing V2; V12 adds them with `IF NOT EXISTS` and seeds known values.

- **`JpaSpecificationExecutor`** on `JpaTransactionRepository`: enables dynamic
  predicate-based queries for dashboard search without writing custom JPQL per filter
  combination.

## Test Coverage

`MerchantMapperTest` (10 tests):
- `toEntity` — all scalar fields, webhook fields present, webhook fields null, ignored
  entity fields remain at default, `SUSPENDED` status round-trip
- `toDomain` — all fields, limits always INR with correct amounts, webhook fields,
  `TERMINATED` status
- Round-trip: entity → domain → entity preserves all mapped fields

`TransactionMapperTest` (12 tests):
- `toEntity` — scalar fields, `authorizationCode` present/absent, `arn` present/absent,
  ignored entity fields remain at default
- `toDomain` — all fields, `authorizationCode` present, `arn` present, `responseCode` present
- Round-trips: basic fields preserved; auth code + ARN preserved through entity → domain → entity

Both test classes instantiate the mapper with `new MapperImpl()` — no Spring context,
runs in milliseconds.

## How to Verify

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home \
  mvn clean test -pl adapters --no-transfer-progress
# Expected: Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
```
