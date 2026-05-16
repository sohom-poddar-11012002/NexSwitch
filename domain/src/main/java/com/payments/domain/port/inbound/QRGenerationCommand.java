package com.payments.domain.port.inbound;

import com.payments.domain.model.vo.MerchantId;
import com.payments.domain.model.vo.Money;
import com.payments.domain.model.vo.TerminalId;

import java.math.BigDecimal;
import java.util.Objects;

// LEARN: CommandObject — validated at boundary (amount > 0, orderId not blank) before hitting domain
public record QRGenerationCommand(
        MerchantId merchantId,
        TerminalId terminalId,
        Money amount,
        String orderId
) {
    public QRGenerationCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(terminalId, "terminalId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
