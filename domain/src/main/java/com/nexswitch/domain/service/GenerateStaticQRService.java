package com.nexswitch.domain.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.StaticQRResult;
import com.nexswitch.domain.port.inbound.GenerateStaticQRCommand;
import com.nexswitch.domain.port.inbound.GenerateStaticQRUseCase;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.QrImageGeneratorPort;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// LEARN: StaticQR — no amount or txnRef in UPI string; payer enters amount manually.
//        The QR is permanent (not Redis-backed). Reconciliation is harder since two
//        payments of the same amount from the same merchant are indistinguishable without
//        a txnRef — this is the documented limitation of static vs dynamic QR.
public class GenerateStaticQRService implements GenerateStaticQRUseCase {

    private static final int QR_SIZE_PX = 300;

    private final MerchantRepository   merchantRepository;
    private final QrImageGeneratorPort qrImageGeneratorPort;

    public GenerateStaticQRService(MerchantRepository merchantRepository,
                                   QrImageGeneratorPort qrImageGeneratorPort) {
        this.merchantRepository   = merchantRepository;
        this.qrImageGeneratorPort = qrImageGeneratorPort;
    }

    @Override
    public StaticQRResult execute(GenerateStaticQRCommand command) {
        Optional<MerchantProfile> maybeProfile = merchantRepository.findById(command.merchantId());
        if (maybeProfile.isEmpty()) {
                return new StaticQRResult.Failed("Merchant not found: " + command.merchantId().value());
        }

        MerchantProfile profile = maybeProfile.get();
        if (!profile.isActive()) {
                return new StaticQRResult.Failed("Merchant account is not active");
        }

        String vpa = resolveVpa(profile);
        String upiString = buildStaticUpiString(vpa, profile);
        String qrImageBase64 = qrImageGeneratorPort.generateBase64Png(upiString, QR_SIZE_PX, QR_SIZE_PX);

        return new StaticQRResult.Generated(qrImageBase64, vpa, upiString);
    }

    private String resolveVpa(MerchantProfile profile) {
        return (profile.vpa() != null && !profile.vpa().isBlank())
                ? profile.vpa()
                : profile.merchantId().value() + "@payswiff";
    }

    private String buildStaticUpiString(String vpa, MerchantProfile profile) {
        return "upi://pay?pa=" + encode(vpa)
             + "&pn=" + encode(profile.name())
             + "&mc=" + profile.mcc()
             + "&mode=02&purpose=00";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
