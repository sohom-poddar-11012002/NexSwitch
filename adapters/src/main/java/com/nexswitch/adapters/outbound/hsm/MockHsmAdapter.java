package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.port.outbound.HsmPort;
import org.springframework.stereotype.Component;

// LEARN: MockHSM — a real HSM (Hardware Security Module) is a FIPS 140-2 Level 3 tamper-resistant
//        device that never exposes key material. In local dev we replace it with this mock that
//        approves everything. staging uses SoftHSM2 (software emulation); production uses real HSM.
//        Swapped via hsm.provider=mock|softhsm property — no domain code changes required.
@Component
public class MockHsmAdapter implements HsmPort {

    @Override
    public String dukptDecrypt(byte[] encryptedPinBlock, byte[] ksn) {
        return "mock-pin-handle";
    }

    @Override
    public byte[] reencryptUnderZpk(String plaintextPinBlockHandle, String zpkHandle) {
        return new byte[8];
    }

    @Override
    public byte[] translatePinBlock(byte[] pinBlock, String sourceKeyHandle, String destKeyHandle) {
        return new byte[8];
    }

    @Override
    public boolean verifyMac(byte[] messageBytes, byte[] mac) {
        return true;
    }

    @Override
    public boolean verifyArqc(PanHash panHash, byte[] arqc, int atc, byte[] transactionData) {
        // LEARN: ARQC — Application Request Cryptogram; EMV chip MACs the transaction
        //        using a session key derived from the card master key + ATC.
        //        Mock always returns true; SoftHSM adapter computes the actual CMAC.
        return true;
    }

    @Override
    public byte[] generateArpc(byte[] arqc, String authResponseCode) {
        return new byte[8];
    }
}
