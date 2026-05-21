package com.nexswitch.app.config;

import com.nexswitch.domain.port.outbound.*;
import com.nexswitch.domain.service.AuthorizationService;
import com.nexswitch.domain.service.GenerateQRService;
import com.nexswitch.domain.service.GenerateStaticQRService;
import com.nexswitch.domain.service.InitiateCollectService;
import com.nexswitch.domain.service.QRSessionManager;
import com.nexswitch.domain.service.ReversalService;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// LEARN: @ConditionalOnProperty is the Spring idiom for the Strategy pattern — swap implementations
//        (mock HSM locally, SoftHSM in staging, real HSM in prod) via a single config property
//        without touching domain code. The domain port interface stays identical across all envs.
@Configuration
@EnableScheduling
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

    @Bean
    public ReversalService reversalService(
            TransactionRepository transactionRepository,
            AuthorizationPort authorizationPort) {
        return new ReversalService(transactionRepository, authorizationPort, new TransactionStateMachine());
    }

    @Bean
    public QRSessionManager qrSessionManager(
            @Value("${qr.session.ttl-minutes:5}") long ttlMinutes) {
        return new QRSessionManager(ttlMinutes);
    }

    @Bean
    public GenerateQRService generateQRService(
            QRSessionManager qrSessionManager,
            QrSessionPort qrSessionPort,
            QrImageGeneratorPort qrImageGeneratorPort,
            MerchantRepository merchantRepository) {
        return new GenerateQRService(
                qrSessionManager, qrSessionPort, qrImageGeneratorPort, merchantRepository);
    }

    @Bean
    public GenerateStaticQRService generateStaticQRService(
            MerchantRepository merchantRepository,
            QrImageGeneratorPort qrImageGeneratorPort) {
        return new GenerateStaticQRService(merchantRepository, qrImageGeneratorPort);
    }

    @Bean
    public InitiateCollectService initiateCollectService(
            MerchantRepository merchantRepository,
            CollectRequestPort collectRequestPort,
            UpiPspNotifier upiPspNotifier) {
        return new InitiateCollectService(merchantRepository, collectRequestPort, upiPspNotifier);
    }
}
