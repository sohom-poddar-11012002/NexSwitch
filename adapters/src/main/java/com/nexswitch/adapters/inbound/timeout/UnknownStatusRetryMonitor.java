package com.nexswitch.adapters.inbound.timeout;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// LEARN: UNKNOWN resolution — ISO 8583 Field 39 "91" (switch inoperative) produces UNKNOWN status
//        when the network result was ambiguous. A 0120 status inquiry resolves it; we poll every
//        60s to keep the window of unresolved transactions short without hammering the network.
@Component
public class UnknownStatusRetryMonitor {

    private static final Logger log = LoggerFactory.getLogger(UnknownStatusRetryMonitor.class);

    private final TransactionRepository transactionRepository;
    private final AuthorizationPort     authorizationPort;
    private final TransactionStateMachine stateMachine;

    public UnknownStatusRetryMonitor(TransactionRepository transactionRepository,
                                      AuthorizationPort authorizationPort,
                                      TransactionStateMachine stateMachine) {
        this.transactionRepository = transactionRepository;
        this.authorizationPort     = authorizationPort;
        this.stateMachine          = stateMachine;
    }

    @Scheduled(fixedDelay = 60_000)
    public void resolveUnknownTransactions() {
        List<Transaction> unknowns = transactionRepository.findByStatus(TransactionStatus.UNKNOWN);
        if (unknowns.isEmpty()) return;

        log.info("unknown.retry.scan count={}", unknowns.size());
        for (Transaction txn : unknowns) {
            try {
                TransactionStatus resolved = authorizationPort.inquireStatus(txn);
                if (resolved != null && resolved != TransactionStatus.UNKNOWN) {
                    Transaction updated = stateMachine.transition(txn, resolved);
                    transactionRepository.save(updated);
                    log.info("unknown.retry.resolved txnId={} newStatus={}", txn.id(), resolved);
                }
            } catch (Exception e) {
                log.warn("unknown.retry.failed txnId={}", txn.id(), e);
            }
        }
    }
}
