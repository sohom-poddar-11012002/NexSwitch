package com.nexswitch.adapters.outbound.network;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.ReversalResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.vo.AuthorizationCode;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

// LEARN: UpstreamStub — in local dev the switch has no live Visa/MC TCP connection.
//        This mock approves all transactions with authCode "000000"; ticket #18 wires
//        the real Netty-based upstream TCP client once the golden path is confirmed.
//        Response code "00" = approval in ISO 8583 Field 39.
@Component
public class MockNetworkAuthAdapter implements AuthorizationPort {

    @Override
    public AuthorizationResult authorize(Transaction transaction) {
        return new AuthorizationResult.Approved(
            AuthorizationCode.of("000000"),
            Instant.now(),
            null   // ARPC added by AuthorizationService after this call
        );
    }

    @Override
    public ReversalResult reverse(Transaction transaction) {
        return new ReversalResult.Accepted(Instant.now());
    }
}
