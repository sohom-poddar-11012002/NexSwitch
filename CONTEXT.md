# Sprint Context

## Current Task
Ready for #11 — Domain events

## Stack (updated May 2026)
- JDK 26.0.1 Temurin · bytecode target Java 25 · Spring Boot 4.0.6
- maven-compiler-plugin 3.15.0 · JaCoCo 0.8.13 · Testcontainers 1.21.0 (declared separately)
- JAVA_HOME must point to Temurin 26 — Homebrew Maven defaults to Java 25, set in ~/.zshrc
- All mvn commands: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home mvn <goals>`

## Ticket Progress (Week 1 — Foundation)
- [x] #5  domain value objects — DONE (PR #19)
- [x] #6  domain models (Transaction, TransactionStatus, enums) — DONE (PR #21)
- [x] #7  all ports (inbound + outbound + FraudScoringPort) — DONE (PR #22)
- [x] #8  TransactionStateMachine + InvalidStateTransitionException — DONE (PR #23)
- [x] #9  FraudEngine + FraudVelocityData — DONE (PR #24)
- [x] #10 LuhnValidator, RoutingEngine, FeeWaterfallCalculator, FeeBreakdown — DONE (PR #25)
- [ ] #11 Domain events ← **CURRENT**
- [ ] #12 acquiring-service — ISO 8583 TCP + validation chain
- [ ] #13 payment-switch — BIN lookup, HSM, timeout/reversal
- [ ] #14 Dynamic QR + Redis TTL session

## Domain State
- 409 tests passing · JaCoCo ≥90% enforced · ArchUnit rules green
- 7 VOs · 12 models · 9 ports · 5 commands · 5 use cases · 5 services · 2 exceptions

## Next Steps
1. Cut branch `feat/11-domain-events`
2. TDD: DomainEvent<T> envelope + per-transition event types (TransactionAuthorizedEvent, etc.)
3. Wire into Transaction.raiseEvent() and TransactionStateMachine
4. `mvn clean test -pl domain` GREEN · JaCoCo gate passes
5. Commit `docs/ticket-11-domain-events.md` + open PR + squash merge → closes #11

## Protocol
- Read this file at the START of every session
- Update this file immediately after every PR squash-merge
- Run `graph_continue` before any code exploration
