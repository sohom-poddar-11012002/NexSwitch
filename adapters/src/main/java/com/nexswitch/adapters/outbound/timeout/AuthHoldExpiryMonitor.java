package com.nexswitch.adapters.outbound.timeout;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// LEARN: Authorization hold expiry — Visa/MC rules require merchants to capture within 7 days
//        (31 days for hotels MCC 7011 and car rentals MCC 7512). An expired hold must be reversed
//        to release the cardholder's funds; failing to do so is a scheme violation.
@Component
public class AuthHoldExpiryMonitor {

    private static final Logger log = LoggerFactory.getLogger(AuthHoldExpiryMonitor.class);

    private static final String FIND_EXPIRED_SQL =
            """
            SELECT id FROM transactions
            WHERE status = 'AUTHORIZED'
              AND authorization_expiry IS NOT NULL
              AND authorization_expiry < NOW()
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    public AuthHoldExpiryMonitor(NamedParameterJdbcTemplate jdbc,
                                  TransactionRepository transactionRepository) {
        this.jdbc                  = jdbc;
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void expireStaleAuthHolds() {
        log.info("auth_hold_expiry.start");

        List<UUID> expiredIds = jdbc.query(
                FIND_EXPIRED_SQL,
                new MapSqlParameterSource(),
                (rs, rowNum) -> UUID.fromString(rs.getString("id")));

        log.info("auth_hold_expiry.found count={}", expiredIds.size());

        int expired = 0;
        for (UUID id : expiredIds) {
            transactionRepository.findById(id).ifPresent(txn -> {
                try {
                    Transaction expiredTxn = stateMachine.transition(txn, TransactionStatus.EXPIRED);
                    transactionRepository.save(expiredTxn);
                    log.info("auth_hold_expiry.expired transactionId={} merchantId={}",
                            txn.id(), txn.merchantId().value());
                } catch (Exception e) {
                    log.error("auth_hold_expiry.failed transactionId={} error={}", id, e.getMessage());
                }
            });
            expired++;
        }

        log.info("auth_hold_expiry.complete expired={}", expired);
    }
}
