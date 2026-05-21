package com.nexswitch.domain.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.QRGenerationResult;
import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.port.inbound.GenerateQRUseCase;
import com.nexswitch.domain.port.inbound.QRGenerationCommand;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.QrImageGeneratorPort;
import com.nexswitch.domain.port.outbound.QrSessionPort;

import java.util.Optional;

// LEARN: OrchestratingService — pure orchestration; all behaviour is in domain objects and ports.
//        No Spring annotations — this is wired as a @Bean in AdapterConfig (composition root).
public class GenerateQRService implements GenerateQRUseCase {

    private static final int QR_SIZE_PX = 300;

    private final QRSessionManager    sessionManager;
    private final QrSessionPort       qrSessionPort;
    private final QrImageGeneratorPort imageGenerator;
    private final MerchantRepository  merchantRepository;

    public GenerateQRService(
            QRSessionManager sessionManager,
            QrSessionPort qrSessionPort,
            QrImageGeneratorPort imageGenerator,
            MerchantRepository merchantRepository) {
        this.sessionManager      = sessionManager;
        this.qrSessionPort       = qrSessionPort;
        this.imageGenerator      = imageGenerator;
        this.merchantRepository  = merchantRepository;
    }

    @Override
    public QRGenerationResult execute(QRGenerationCommand command) {
        Optional<MerchantProfile> maybeProfile =
                merchantRepository.findById(command.merchantId());
        if (maybeProfile.isEmpty()) {
            return new QRGenerationResult.Failed(
                    "Unknown merchant: " + command.merchantId().value());
        }
        MerchantProfile profile = maybeProfile.get();
        if (!profile.isActive()) {
            return new QRGenerationResult.Failed(
                    "Merchant " + command.merchantId().value() + " is not active");
        }

        QRSession session = sessionManager.create(
                command.merchantId(), command.amount(), command.orderId());

        String vpa = (profile.vpa() != null && !profile.vpa().isBlank())
                ? profile.vpa()
                : command.merchantId().value() + "@payswiff";

        String upiString = sessionManager.buildUpiString(session, vpa, profile.name());
        String qrBase64  = imageGenerator.generateBase64Png(upiString, QR_SIZE_PX, QR_SIZE_PX);

        qrSessionPort.save(session);

        return new QRGenerationResult.Generated(
                session.txnRef(), qrBase64, session.expiresAt());
    }
}
