# Sprint Context

## Current Task
Working on #6 — domain models (Transaction record, TransactionStatus enum, PaymentMethod, PaymentNetwork, and supporting records)

## Where We Are
- Skeleton scaffolded: all 14 Maven modules compile, `mvn install` GREEN (commit 7b7d6e7)
- GitHub Issues #5–#18 created across Week 1 (Foundation) and Week 2 (Golden Path) milestones
- Branch: `feat/5-domain-value-objects` → PR #19 open, ready to merge → closes #5
- Value objects fully implemented and tested (88 tests, JaCoCo ≥90%)

## Ticket Order (Week 1)
- [x] #5  domain value objects ← **DONE — PR #19**
- [ ] #6  domain models (Transaction, TransactionStatus, enums) ← **NEXT**
- [ ] #7  all ports (inbound + outbound + FraudScoringPort)
- [ ] #8  TransactionStateMachine
- [ ] #9  FraudEngine
- [ ] #10 LuhnValidator, RoutingEngine, FeeWaterfallCalculator
- [ ] #11 domain events
- [ ] #12 JPA entities + MapStruct mappers
- [ ] #13 Flyway migrations V1–V11
- [ ] #14 test-support fixtures + Testcontainers

## Next Steps
1. Squash-merge PR #19 → closes #5
2. Create branch `feat/6-domain-models`
3. TDD: Transaction record, TransactionStatus (20 states), PaymentMethod, PaymentNetwork, QRSession, RoutingRule, BinInfo, MerchantProfile, ChargebackRecord, SettlementBatch, ReconciliationRun
4. `mvn test -pl domain` GREEN
5. Open PR → squash merge → closes #6
