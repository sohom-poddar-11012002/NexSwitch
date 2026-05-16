package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// LEARN: CommandObject — all inputs for one use case; emvData/pinBlock as byte[] for binary ISO 8583 fields.
//        bin6 (first 6 PAN digits) is PCI-safe to store — only the full PAN requires hashing.
//        The adapter extracts bin6 from Field 2 before hashing, then passes both into the command.
public record AuthorizationCommand(
        UUID transactionId,
        MerchantId merchantId,
        TerminalId terminalId,
        String bin6,
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
        if (bin6 == null || !bin6.matches("\\d{6,8}"))
            throw new IllegalArgumentException("bin6 must be 6–8 digits: " + bin6);
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
