package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.TerminalId;

// LEARN: RichDomainModel — isActive() belongs here, not in the service; the domain carries behaviour,
//        not just data. Services read state and call methods; they never switch on string status values.
public record Terminal(
        TerminalId terminalId,
        MerchantId merchantId,
        Status status
) {
    public Terminal {
        if (terminalId == null) throw new IllegalArgumentException("terminalId must not be null");
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public enum Status { ACTIVE, INACTIVE, SUSPENDED }
}
