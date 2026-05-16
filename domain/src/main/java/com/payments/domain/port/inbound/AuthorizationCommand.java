package com.payments.domain.port.inbound;

import com.payments.domain.model.PaymentMethod;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.vo.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// LEARN: CommandObject — all inputs for one use case; emvData/pinBlock as byte[] for binary ISO 8583 fields
public record AuthorizationCommand(
        UUID transactionId,
        MerchantId merchantId,
        TerminalId terminalId,
        PanHash panHash,
        Money amount,
        PaymentNetwork network,
        PaymentMethod paymentMethod,
        SystemTraceAuditNumber stan,
        byte[] emvData,
        byte[] pinBlock,
        String posEntryMode
) {
    public AuthorizationCommand {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(terminalId, "terminalId must not be null");
        Objects.requireNonNull(panHash, "panHash must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        Objects.requireNonNull(stan, "stan must not be null");
        Objects.requireNonNull(posEntryMode, "posEntryMode must not be null");
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
