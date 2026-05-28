package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.NetworkToken;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.PanHash;

import java.util.Optional;

// LEARN: TokenizationPort — domain calls tokenize(panHash) without knowing whether VTS or MDES is
//        used. The adapter picks the correct network API based on BIN routing. Domain stays protocol-agnostic.
public interface TokenizationPort {

    NetworkToken tokenize(PanHash panHash, PaymentNetwork network, MerchantId merchantId);

    Optional<NetworkToken> lookup(String networkTokenValue);

    void suspend(String networkTokenValue);
}
