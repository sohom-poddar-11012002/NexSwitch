package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.port.outbound.HsmPort;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// LEARN: CircuitBreaker pattern — after N% failures in the sliding window the circuit trips OPEN:
//        calls immediately throw CallNotPermittedException (load shedding) rather than queuing
//        behind a broken HSM. After waitDurationInOpenState the circuit enters HALF_OPEN; it
//        allows permittedNumberOfCallsInHalfOpenState probe calls before deciding to close or
//        re-open. This prevents cascade failure when the HSM is degraded.
@Primary
@Component
public class ResilientHsmAdapter implements HsmPort {

    private static final String CB_NAME  = "hsm";
    private static final String BH_NAME  = "hsm";

    private final HsmPort delegate;

    public ResilientHsmAdapter(@Qualifier("mockHsmAdapter") HsmPort delegate) {
        this.delegate = delegate;
    }

    // LEARN: Bulkhead (semaphore) limits max concurrent HSM calls — HSMs are expensive, shared
    //        hardware; unbounded concurrency would saturate the HSM session pool and cause
    //        every caller to time out. Semaphore variant rejects excess callers immediately.
    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public String dukptDecrypt(byte[] encryptedPinBlock, byte[] ksn) {
        return delegate.dukptDecrypt(encryptedPinBlock, ksn);
    }

    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public byte[] reencryptUnderZpk(String plaintextPinBlockHandle, String zpkHandle) {
        return delegate.reencryptUnderZpk(plaintextPinBlockHandle, zpkHandle);
    }

    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public byte[] translatePinBlock(byte[] pinBlock, String sourceKeyHandle, String destKeyHandle) {
        return delegate.translatePinBlock(pinBlock, sourceKeyHandle, destKeyHandle);
    }

    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public boolean verifyMac(byte[] messageBytes, byte[] mac) {
        return delegate.verifyMac(messageBytes, mac);
    }

    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public boolean verifyArqc(PanHash panHash, byte[] arqc, int atc, byte[] transactionData) {
        return delegate.verifyArqc(panHash, arqc, atc, transactionData);
    }

    @CircuitBreaker(name = CB_NAME)
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public byte[] generateArpc(byte[] arqc, String authResponseCode) {
        return delegate.generateArpc(arqc, authResponseCode);
    }
}
