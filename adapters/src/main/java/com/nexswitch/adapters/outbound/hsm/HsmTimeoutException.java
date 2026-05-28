package com.nexswitch.adapters.outbound.hsm;

public class HsmTimeoutException extends RuntimeException {
    public HsmTimeoutException(String message) {
        super(message);
    }
}
