package com.nexswitch.adapters.outbound.persistence.mapper;

import com.nexswitch.adapters.outbound.persistence.entity.TransactionEntity;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.vo.*;
import org.mapstruct.Mapper;

import java.util.Currency;

// LEARN: MapStruct — all-default-method pattern; Transaction fluent accessors break @Mapping path resolution
// Transaction uses fluent (non-JavaBeans) accessors — both directions are hand-written
// default methods so MapStruct generates a Spring @Component impl without annotation processing errors.
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    default TransactionEntity toEntity(Transaction domain) {
        TransactionEntity e = new TransactionEntity();
        e.setId(domain.id());
        e.setMerchantId(domain.merchantId().value());
        e.setTerminalId(domain.terminalId().value());
        e.setPanHash(domain.panHash().value());
        e.setStan(domain.stan().value());
        e.setAmount(domain.amount().amount());
        e.setCurrency(domain.amount().currency().getCurrencyCode());
        e.setNetwork(domain.network().name());
        e.setPaymentMethod(domain.paymentMethod().name());
        e.setStatus(domain.status().name());
        e.setAuthorizationCode(domain.authorizationCode() != null
            ? domain.authorizationCode().value() : null);
        e.setArn(domain.arn() != null ? domain.arn().value() : null);
        e.setResponseCode(domain.responseCode());
        e.setIdempotencyKey(domain.terminalId().value() + ":" + domain.stan().value());
        e.setCardLast4(domain.cardLast4());
        e.setCreatedAt(domain.createdAt());
        e.setUpdatedAt(domain.updatedAt());
        return e;
    }

    default Transaction toDomain(TransactionEntity entity) {
        return Transaction.builder()
            .id(entity.getId())
            .merchantId(new MerchantId(entity.getMerchantId()))
            .terminalId(new TerminalId(entity.getTerminalId()))
            .amount(Money.of(entity.getAmount(), Currency.getInstance(entity.getCurrency())))
            .network(PaymentNetwork.valueOf(entity.getNetwork()))
            .paymentMethod(PaymentMethod.valueOf(entity.getPaymentMethod()))
            .panHash(PanHash.of(entity.getPanHash()))
            .stan(SystemTraceAuditNumber.of(entity.getStan()))
            .status(TransactionStatus.valueOf(entity.getStatus()))
            .authorizationCode(entity.getAuthorizationCode() != null
                ? AuthorizationCode.of(entity.getAuthorizationCode()) : null)
            .arn(entity.getArn() != null
                ? AcquirerReferenceNumber.of(entity.getArn()) : null)
            .responseCode(entity.getResponseCode())
            .cardLast4(entity.getCardLast4())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
