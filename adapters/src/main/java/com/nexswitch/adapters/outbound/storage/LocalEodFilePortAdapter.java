package com.nexswitch.adapters.outbound.storage;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.EodFilePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

// LEARN: EOD files are dropped by Visa/MC SFTP daily at 23:00 IST; local adapter reads from filesystem
//        for dev/test. Production adapter downloads via SFTP with HMAC-SHA256 signature verification.
//        Same port interface — domain service never knows the difference.
@Component
@Profile("local")
public class LocalEodFilePortAdapter implements EodFilePort {

    private static final Logger log = LoggerFactory.getLogger(LocalEodFilePortAdapter.class);

    // CSV format: transactionId,rrn,amount,currency,network,responseCode
    private static final int COL_TXN_ID       = 0;
    private static final int COL_RRN          = 1;
    private static final int COL_AMOUNT       = 2;
    private static final int COL_CURRENCY     = 3;
    private static final int COL_NETWORK      = 4;
    private static final int COL_RESPONSE     = 5;

    private final String eodDir;

    public LocalEodFilePortAdapter(
            @Value("${eod.file.dir:/tmp/nexswitch/eod}") String eodDir) {
        this.eodDir = eodDir;
    }

    @Override
    public List<SettlementRecord> fetchForDate(LocalDate date, PaymentNetwork network) {
        Path file = Path.of(eodDir, network.name().toLowerCase(), date + ".csv");
        if (!Files.exists(file)) {
            log.info("eod.file.not_found path={} — returning empty list", file);
            return List.of();
        }
        List<SettlementRecord> records = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank() || line.startsWith("#")) continue; // skip header / comments
                try {
                    records.add(parseLine(line));
                } catch (Exception e) {
                    log.warn("eod.file.parse_error line={} path={} error={}", i + 1, file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("eod.file.read_error path={}", file, e);
        }
        log.info("eod.file.loaded network={} date={} records={}", network, date, records.size());
        return records;
    }

    private SettlementRecord parseLine(String line) {
        String[] cols = line.split(",", -1);
        UUID txnId        = UUID.fromString(cols[COL_TXN_ID].trim());
        String rrn        = cols[COL_RRN].trim();
        BigDecimal amount = new BigDecimal(cols[COL_AMOUNT].trim());
        Currency currency = Currency.getInstance(cols[COL_CURRENCY].trim());
        PaymentNetwork net = PaymentNetwork.valueOf(cols[COL_NETWORK].trim().toUpperCase());
        String responseCode = cols[COL_RESPONSE].trim();
        return new SettlementRecord(txnId, rrn, Money.of(amount, currency), net, responseCode);
    }
}
