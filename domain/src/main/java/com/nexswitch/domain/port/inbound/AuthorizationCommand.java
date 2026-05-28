package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// LEARN: CommandObject — all inputs for one use case; emvData carries parsed Field 55 (ARQC + ATC + CDOL1 data).
//        bin6 (first 6 PAN digits) is PCI-safe to store — only the full PAN requires hashing.
//        ksn = Field 53 (10-byte Key Serial Number) carries the DUKPT counter; null for non-chip flows.
//        emvData = null for non-chip flows (contactless, QR); present for EMV chip (posEntryMode 05x).
//        cavv = 3DS 2.x Cardholder Authentication Verification Value; eci = Electronic Commerce Indicator.
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
        EmvData emvData,
        byte[] pinBlock,
        byte[] ksn,
        String posEntryMode,
        String cardLast4,
        String cavv,
        String eci
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
