package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// LEARN: EodFilePort — acquirer EOD settlement files arrive as fixed-width or CSV records;
//        domain only sees typed SettlementRecord, never raw bytes. This isolates parsing
//        from reconciliation logic, letting us swap file formats without touching the service.
public interface EodFilePort {

    record SettlementRecord(
            UUID transactionId,
            String rrn,
            Money networkAmount,
            PaymentNetwork network,
            String responseCode
    ) {}

    List<SettlementRecord> fetchForDate(LocalDate date, PaymentNetwork network);
}
