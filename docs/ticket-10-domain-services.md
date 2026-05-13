# Ticket #10 — LuhnValidator, RoutingEngine, FeeWaterfallCalculator

## What
Three pure domain services completing the core payment processing logic in the domain layer.

## Why
Closes #10. Provides the validation and calculation primitives used by the authorization flow:
Luhn check fires before the HSM round-trip; routing determines which network adapter to invoke;
fee waterfall runs post-reconciliation to compute merchant payout.

## Design decisions

- **LuhnValidator is a static utility** — pure function with no state, no injection. Called at the
  acquiring-service boundary before any HSM or upstream call. Strips spaces so "4111 1111 1111 1111"
  works the same as "4111111111111111".

- **RoutingEngine has two overloads** — `route(pan, rules)` for explicit rule-based routing (rules
  loaded from DB, ordered by `priority` integer where lower = higher priority), and `route(BinInfo)`
  as a direct fallback when BIN table lookup is sufficient with no override rules needed.

- **RoutingException carries the BIN prefix** — first 6 digits of the PAN, safe to log (not the
  full PAN). Ops can see exactly which BIN range has no rule configured.

- **FeeWaterfallCalculator takes explicit percentages** — MDR and reserve are passed in from the
  `MerchantProfile`, not hardcoded. Interchange and assessment rates are hardcoded per CLAUDE.md §22
  (network-mandated, not merchant-configurable). Minimum MDR of ₹2.00 is applied via `BigDecimal.max`.

- **FeeBreakdown is an immutable record** — all seven fields (gross → interchange → assessment →
  MDR → net → reserve → payout) captured in one value object. Callers have the full audit trail.

- **Compilation target downgraded to Java 25** — JDK 26 is used to run Maven and the JVM, but
  `<release>25</release>` targets Java 25 class files (major version 69). JaCoCo 0.8.13 (released
  April 2025) tops out at class version 69; Java 26 class files (version 70) break report generation.
  No Java 26-specific language features are used in this codebase — records and sealed interfaces
  are Java 21. Will re-evaluate when JaCoCo 0.8.14+ lands.

## Test coverage

**LuhnValidator (19 tests)**
- 8 known-valid PANs across Visa/MC/Amex/Discover/JCB
- 3 invalid check-digit cases
- null, blank, empty, non-numeric inputs
- single digit and too-short PANs
- space-stripped PAN ("4111 1111 1111 1111")

**RoutingEngine (12 tests)**
- Single-rule match per network (VISA, MASTERCARD, RUPAY, UPI)
- Priority: two rules match same PAN → lowest priority number wins
- Multiple rules, only one matches
- No matching rule → RoutingException with BIN prefix in message
- Empty rule list → RoutingException
- Direct BinInfo routing (two networks)
- Null guards (pan, rules, binInfo)

**FeeWaterfallCalculator (18 tests)**
- Gross amount preserved through waterfall
- Full Visa debit waterfall with exact pence assertions
- Visa credit interchange rate (1.80% vs 0.90% debit)
- Mastercard credit and debit interchange
- RuPay credit and debit interchange
- UPI: zero interchange and assessment
- Minimum MDR ₹2.00 applied when percentage yields less
- Percentage MDR applied when above minimum
- Custom MDR percentage per merchant
- Custom reserve percentage
- Payout = net − reserve (structural invariant)
- Parameterized: all 7 network+type combinations vs spec rates
- Null guards (amount, network, cardType)

## How to verify

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home \
  mvn clean test -pl domain
# Expect: Tests run: 409, Failures: 0 — All coverage checks have been met.
```
