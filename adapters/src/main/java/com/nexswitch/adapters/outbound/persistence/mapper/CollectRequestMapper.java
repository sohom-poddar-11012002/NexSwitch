package com.nexswitch.adapters.outbound.persistence.mapper;

import com.nexswitch.adapters.outbound.persistence.entity.CollectRequestEntity;
import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.vo.CollectId;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.NpciTxnId;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.Currency;

@Mapper(componentModel = "spring")
public interface CollectRequestMapper {

    default CollectRequestEntity toEntity(CollectRequest domain) {
        CollectRequestEntity e = new CollectRequestEntity();
        e.setCollectId(domain.collectId().value());
        e.setMerchantId(domain.merchantId().value());
        e.setPayerVpa(domain.payerVpa());
        e.setAmount(domain.amount().amount());
        e.setCurrency(domain.amount().currency().getCurrencyCode());
        e.setOrderId(domain.orderId());
        e.setStatus(domain.status().name());
        e.setNpciTxnId(domain.npciTxnId() != null ? domain.npciTxnId().value() : null);
        e.setCreatedAt(domain.createdAt());
        e.setExpiresAt(domain.expiresAt());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    default CollectRequest toDomain(CollectRequestEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return CollectRequest.builder()
                .collectId(new CollectId(entity.getCollectId()))
                .merchantId(new MerchantId(entity.getMerchantId()))
                .payerVpa(entity.getPayerVpa())
                .amount(Money.of(entity.getAmount(), currency))
                .orderId(entity.getOrderId())
                .status(CollectRequest.Status.valueOf(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .npciTxnId(entity.getNpciTxnId() != null ? new NpciTxnId(entity.getNpciTxnId()) : null)
                .build();
    }
}
