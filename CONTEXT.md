# Sprint Context

## Current Task
Working on #5 — domain value objects (Money, MerchantId, TerminalId, PanHash, AuthorizationCode, ARN, STAN)

## Where We Are
- Skeleton scaffolded: all 14 Maven modules compile, `mvn install` GREEN (commit 7b7d6e7)
- GitHub Issues #5–#18 created across Week 1 (Foundation) and Week 2 (Golden Path) milestones
- Branch: `feat/5-domain-value-objects`
- No implementation written yet — starting TDD on domain value objects

## Ticket Order (Week 1)
- [ ] #5  domain value objects ← **CURRENT**
- [ ] #6  domain models (Transaction, TransactionStatus, enums)
- [ ] #7  all ports (inbound + outbound + FraudScoringPort)
- [ ] #8  TransactionStateMachine
- [ ] #9  FraudEngine
- [ ] #10 LuhnValidator, RoutingEngine, FeeWaterfallCalculator
- [ ] #11 domain events
- [ ] #12 JPA entities + MapStruct mappers
- [ ] #13 Flyway migrations V1–V11
- [ ] #14 test-support fixtures + Testcontainers

## Next Steps
1. Write failing tests for Money, MerchantId, TerminalId, PanHash, AuthorizationCode, ARN, STAN
2. Implement value objects to make tests pass
3. `mvn test -pl domain` GREEN, JaCoCo ≥90%
4. Open PR → squash merge → closes #5
5. Move to #6
