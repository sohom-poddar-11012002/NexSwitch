package com.nexswitch.adapters.outbound.tokenization;

import com.nexswitch.domain.model.NetworkToken;
import com.nexswitch.domain.model.NetworkToken.TokenStatus;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.port.outbound.TokenizationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// LEARN: Stub adapter — domain port is fully modelled (tokenize/lookup/suspend) but the real
//        network API (VTS or MDES) requires a production certificate and IP whitelisting.
//        The stub logs intent and returns a deterministic placeholder so auth flows compile and run.
@Component
public class StubTokenizationAdapter implements TokenizationPort {

    private static final Logger log = LoggerFactory.getLogger(StubTokenizationAdapter.class);

    @Override
    public NetworkToken tokenize(PanHash panHash, PaymentNetwork network, MerchantId merchantId) {
        log.warn("tokenization.stub — network token NOT wired to real VTS/MDES; returning placeholder. panHash_prefix={} network={}",
                panHash.value().substring(0, 8), network);
        return new NetworkToken(
                UUID.randomUUID(),
                merchantId,
                "STUB-TOKEN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                "0000",
                network,
                LocalDate.now().plusYears(4),
                TokenStatus.ACTIVE
        );
    }

    @Override
    public Optional<NetworkToken> lookup(String networkTokenValue) {
        log.warn("tokenization.stub.lookup — returning empty; networkTokenValue={}", networkTokenValue);
        return Optional.empty();
    }

    @Override
    public void suspend(String networkTokenValue) {
        log.warn("tokenization.stub.suspend — no-op; networkTokenValue={}", networkTokenValue);
    }
}
