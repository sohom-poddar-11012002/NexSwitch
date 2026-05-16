package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;

// LEARN: @ConditionalOnProperty is the Spring idiom for the Strategy pattern — swap implementations
//        (mock HSM locally, SoftHSM in staging, real HSM in prod) via a single config property
//        without touching domain code. The domain port interface stays identical across all envs.
/**
 * Wires domain ports to their adapter implementations via @ConditionalOnProperty.
 *
 * Key properties:
 *   hsm.provider: mock | softhsm       (default: mock for local dev)
 *   upstream.provider: wiremock | razorpay | npci  (default: wiremock)
 *   storage.provider: local | s3       (default: local)
 *
 * Implementation added in Week 1 Step 2 as adapters are created.
 */
@Configuration
public class AdapterConfig {
    // Populated as adapters are implemented
}
