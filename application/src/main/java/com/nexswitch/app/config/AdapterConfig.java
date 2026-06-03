package com.nexswitch.app.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexswitch.domain.port.inbound.ProcessRefundUseCase;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.outbound.*;
import com.nexswitch.domain.service.AuthorizationService;
import com.nexswitch.domain.service.GenerateQRService;
import java.time.Clock;
import com.nexswitch.domain.service.GenerateStaticQRService;
import com.nexswitch.domain.service.InitiateCollectService;
import com.nexswitch.domain.service.ProcessRefundService;
import com.nexswitch.domain.service.QRSessionManager;
import com.nexswitch.domain.service.ReconciliationService;
import com.nexswitch.domain.service.ReversalService;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// LEARN: @ConditionalOnProperty is the Spring idiom for the Strategy pattern — swap implementations
//        (mock HSM locally, SoftHSM in staging, real HSM in prod) via a single config property
//        without touching domain code. The domain port interface stays identical across all envs.
// LEARN: @EntityScan / @EnableJpaRepositories here rather than on each service's Application class:
//        all services scan com.nexswitch.app, so this one annotation covers every service that
//        uses the adapters module — eliminates per-service boilerplate and prevents drift.
@Configuration
@EnableScheduling
@EntityScan(basePackages = "com.nexswitch.adapters.outbound.persistence.entity")
@EnableJpaRepositories(basePackages = "com.nexswitch.adapters.outbound.persistence.jpa")
public class AdapterConfig {

    // LEARN: Exposing Clock as a @Bean lets any service use it via constructor injection.
    //        Tests can override with a fixed Clock (Clock.fixed()) to control the current time.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TransactionStateMachine transactionStateMachine() {
        return new TransactionStateMachine();
    }

    // LEARN: @ConditionalOnMissingBean means this only fires when Spring Boot's web auto-config
    //        hasn't already registered an ObjectMapper. Services with spring-boot-starter-web
    //        get the auto-configured one; services without it get this fallback, so CachingBinLookupAdapter
    //        always finds a bean regardless of which starters each service declares.
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

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
            TransactionRepository transactionRepository,
            AuditPort auditPort,
            AtcWatermarkPort atcWatermarkPort,
            FallbackCounterPort fallbackCounterPort,
            Clock clock,
            TransactionStateMachine transactionStateMachine) {
        return new AuthorizationService(
            binLookupPort,
            idempotencyPort,
            terminalRepository,
            merchantRepository,
            hsmPort,
            fraudScoringPort,
            authorizationPort,
            transactionRepository,
            transactionStateMachine,
            clock,
            auditPort,
            atcWatermarkPort,
            fallbackCounterPort
        );
    }

    @Bean
    public ReversalService reversalService(
            TransactionRepository transactionRepository,
            AuthorizationPort authorizationPort,
            AuditPort auditPort,
            TransactionStateMachine transactionStateMachine) {
        return new ReversalService(transactionRepository, authorizationPort,
                transactionStateMachine, auditPort);
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

    @Bean
    public ProcessRefundUseCase processRefundUseCase(
            TransactionRepository transactionRepository,
            RefundPort refundPort,
            AuditPort auditPort,
            TransactionStateMachine transactionStateMachine) {
        return new ProcessRefundService(transactionRepository, refundPort,
                transactionStateMachine, auditPort);
    }

    @Bean
    public ReconcileUseCase reconcileUseCase(TransactionRepository transactionRepository,
                                              AuditPort auditPort,
                                              TransactionStateMachine transactionStateMachine,
                                              org.springframework.beans.factory.ObjectProvider<EodFilePort> eodFilePortProvider,
                                              org.springframework.beans.factory.ObjectProvider<ReconciliationExceptionPort> exceptionPortProvider,
                                              org.springframework.beans.factory.ObjectProvider<ReconciliationRunPort> runPortProvider) {
        return new ReconciliationService(transactionRepository, transactionStateMachine,
                auditPort, eodFilePortProvider.getIfAvailable(),
                exceptionPortProvider.getIfAvailable(), runPortProvider.getIfAvailable());
    }
}
