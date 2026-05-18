package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// LEARN: JpaSpecificationExecutor — type-safe dynamic query composition; avoids string-concatenated JPQL
public interface JpaTransactionRepository
        extends JpaRepository<TransactionEntity, UUID>,
                JpaSpecificationExecutor<TransactionEntity> {

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<TransactionEntity> findByArn(String arn);

    Optional<TransactionEntity> findByQrTxnRef(String qrTxnRef);

    @Query("SELECT t FROM TransactionEntity t WHERE t.merchantId = :merchantId AND t.status IN :statuses ORDER BY t.createdAt DESC")
    List<TransactionEntity> findByMerchantIdAndStatusIn(
            @Param("merchantId") String merchantId,
            @Param("statuses") List<String> statuses);

    List<TransactionEntity> findByStatus(String status);

    @Query("SELECT t FROM TransactionEntity t WHERE t.status IN ('CAPTURED', 'SETTLEMENT_PENDING') AND t.createdAt >= :since")
    List<TransactionEntity> findSettlementEligible(@Param("since") Instant since);

    @Query("SELECT t FROM TransactionEntity t WHERE t.status = 'CAPTURED' AND t.createdAt >= :since")
    List<TransactionEntity> findCapturedSince(@Param("since") Instant since);

    @Query("SELECT t FROM TransactionEntity t WHERE t.status = 'AUTHORIZATION_PENDING' AND t.createdAt < :threshold")
    List<TransactionEntity> findAuthorizationPendingOlderThan(@Param("threshold") Instant threshold);
}
