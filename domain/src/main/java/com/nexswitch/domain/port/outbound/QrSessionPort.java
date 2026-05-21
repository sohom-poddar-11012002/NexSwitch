package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.QRSession;

import java.util.Optional;

// LEARN: PortInterface — the domain defines the contract it needs (save/load/update).
//        Redis TTL, JSON serialization, key prefix are all adapter concerns invisible here.
public interface QrSessionPort {
    void save(QRSession session);
    Optional<QRSession> findByTxnRef(String txnRef);
    void update(QRSession session);
    void delete(String txnRef);
}
