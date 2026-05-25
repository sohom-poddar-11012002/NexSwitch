package com.nexswitch.domain.service;

import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.InitiateCollectResult;
import com.nexswitch.domain.model.vo.CollectId;
import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.port.inbound.InitiateCollectCommand;
import com.nexswitch.domain.port.inbound.InitiateCollectUseCase;
import com.nexswitch.domain.port.outbound.CollectRequestPort;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.UpiPspNotifier;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// LEARN: UPI Collect (pull) vs QR (push) — here the merchant debits the customer's VPA.
//        The PSP is notified synchronously; the customer outcome (APPROVE/REJECT) arrives
//        asynchronously via a webhook from Mock NPCI to POST /upi/collect/outcome.
public class InitiateCollectService implements InitiateCollectUseCase {

    private final MerchantRepository merchantRepository;
    private final CollectRequestPort collectRequestPort;
    private final UpiPspNotifier     upiPspNotifier;

    public InitiateCollectService(MerchantRepository merchantRepository,
                                  CollectRequestPort collectRequestPort,
                                  UpiPspNotifier upiPspNotifier) {
        this.merchantRepository = merchantRepository;
        this.collectRequestPort = collectRequestPort;
        this.upiPspNotifier     = upiPspNotifier;
    }

    @Override
    public InitiateCollectResult execute(InitiateCollectCommand command) {
        Optional<MerchantProfile> maybeProfile = merchantRepository.findById(command.merchantId());
        if (maybeProfile.isEmpty()) {
            return new InitiateCollectResult.Failed("Merchant not found: " + command.merchantId().value());
        }

        MerchantProfile profile = maybeProfile.get();
        if (!profile.isActive()) {
            return new InitiateCollectResult.Failed("Merchant account is not active");
        }

        Instant now      = Instant.now();
        Instant expiresAt = now.plusSeconds(command.expirySeconds());
        CollectId collectId = CollectId.of("COL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

        CollectRequest request = CollectRequest.builder()
                .collectId(collectId)
                .merchantId(command.merchantId())
                .payerVpa(command.payerVpa())
                .amount(command.amount())
                .orderId(command.orderId())
                .status(CollectRequest.Status.PENDING)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        collectRequestPort.save(request);
        upiPspNotifier.sendCollectRequest(request);

        return new InitiateCollectResult.Initiated(collectId, expiresAt);
    }
}
