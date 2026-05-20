package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.domain.port.outbound.HsmPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares the "hsmDelegate" bean used by ResilientHsmAdapter. Kept here in the adapters module
// so the application module (AdapterConfig) never needs to import concrete adapter classes.
@Configuration
class HsmAdapterConfig {

    // Active when hsm.provider=softhsm — wires the real PKCS#11-backed adapter as the delegate.
    @Bean("hsmDelegate")
    @ConditionalOnProperty(name = "hsm.provider", havingValue = "softhsm")
    public HsmPort softHsmDelegate(SoftHsm2HsmAdapter softHsm) {
        return softHsm;
    }

    // Active when hsm.provider=mock (or when the property is absent) — wires the mock adapter.
    @Bean("hsmDelegate")
    @ConditionalOnProperty(name = "hsm.provider", havingValue = "mock", matchIfMissing = true)
    public HsmPort mockHsmDelegate(MockHsmAdapter mock) {
        return mock;
    }
}
