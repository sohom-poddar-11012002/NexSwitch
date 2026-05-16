package com.nexswitch.app.config;

import com.nexswitch.domain.port.outbound.*;
import com.nexswitch.domain.service.AuthorizationService;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// LEARN: @ConditionalOnProperty is the Spring idiom for the Strategy pattern — swap implementations
//        (mock HSM locally, SoftHSM in staging, real HSM in prod) via a single config property
//        without touching domain code. The domain port interface stays identical across all envs.
@Configuration
public class AdapterConfig {

    // LEARN: CompositionRoot — AuthorizationService is a pure domain class (no @Service annotation)
    //        so Spring cannot auto-discover it. We declare a @Bean here using domain port interfaces
    //        as parameters; Spring injects whichever @Component/@Repository is on the classpath.
    //        The application module never imports concrete adapter classes — only domain types.
    @Bean
    public AuthorizationService authorizationService(
            BinLookupPort binLookupPort,
            IdempotencyPort idempotencyPort,
            TerminalRepository terminalRepository,
            MerchantRepository merchantRepository,
            HsmPort hsmPort,
            FraudScoringPort fraudScoringPort,
            AuthorizationPort authorizationPort,
            TransactionRepository transactionRepository) {
        return new AuthorizationService(
            binLookupPort,
            idempotencyPort,
            terminalRepository,
            merchantRepository,
            hsmPort,
            fraudScoringPort,
            authorizationPort,
            transactionRepository,
            new TransactionStateMachine()
        );
    }
}
