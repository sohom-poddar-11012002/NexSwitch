# Sprint Context

## Current Task
Working on #6 — domain models (Transaction, TransactionStatus, enums, supporting records)

## Where We Are
- Skeleton scaffolded: all 14 Maven modules compile, `mvn install` GREEN (commit 7b7d6e7)
- GitHub Issues #5–#18 created across Week 1 (Foundation) and Week 2 (Golden Path) milestones
- Spring Boot upgraded to 3.5.14 (OSS support active until Nov 2026)
- #5 DONE — PR #19 squash-merged, branch deleted, issue auto-closed
- Value objects in `domain/model/vo/`: Money, MerchantId, TerminalId, PanHash, AuthorizationCode, ARN, STAN
- On branch: `main` — ready to cut `feat/6-domain-models`

## Ticket Order (Week 1)
- [x] #5  domain value objects — DONE (PR #19 merged)
- [ ] #6  domain models (Transaction, TransactionStatus, enums) ← **CURRENT**
- [ ] #7  all ports (inbound + outbound + FraudScoringPort)
- [ ] #8  TransactionStateMachine
- [ ] #9  FraudEngine
- [ ] #10 LuhnValidator, RoutingEngine, FeeWaterfallCalculator
- [ ] #11 domain events
- [ ] #12 JPA entities + MapStruct mappers
- [ ] #13 Flyway migrations V1–V11
- [ ] #14 test-support fixtures + Testcontainers

## Next Steps
1. Cut branch `feat/6-domain-models`
2. TDD: TransactionStatus (20 states), PaymentMethod, PaymentNetwork, Transaction record, QRSession, RoutingRule, BinInfo, MerchantProfile, ChargebackRecord, SettlementBatch, ReconciliationRun
3. `mvn test -pl domain` GREEN, JaCoCo ≥90%
4. Commit `docs/ticket-6-domain-models.md` + open PR + squash merge → closes #6
5. Move to #7
