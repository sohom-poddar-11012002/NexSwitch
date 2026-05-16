package com.payments.adapters.outbound.persistence.mapper;

import com.payments.adapters.outbound.persistence.entity.MerchantEntity;
import com.payments.domain.model.MerchantProfile;
import com.payments.domain.model.vo.MerchantId;
import com.payments.domain.model.vo.Money;
import org.mapstruct.Mapper;

import java.util.Currency;

// LEARN: MapStruct — MapStruct generates implementation at compile time; zero reflection at runtime
// MerchantProfile limits are Money VOs — both directions are hand-written default methods
// so MapStruct generates a Spring @Component impl without needing nested path resolution.
@Mapper(componentModel = "spring")
public interface MerchantMapper {

    default MerchantEntity toEntity(MerchantProfile domain) {
        MerchantEntity e = new MerchantEntity();
        e.setId(domain.merchantId().value());
        e.setName(domain.name());
        e.setMcc(domain.mcc());
        e.setStatus(domain.status().name());
        e.setMdrPercentage(domain.mdrPercentage());
        e.setPerTxnLimit(domain.perTransactionLimit().amount());
        e.setDailyLimit(domain.dailyLimit().amount());
        e.setReservePercentage(domain.reservePercentage());
        e.setWebhookUrl(domain.webhookUrl());
        e.setWebhookSecret(domain.webhookSecret());
        return e;
    }

    default MerchantProfile toDomain(MerchantEntity entity) {
        Currency inr = Currency.getInstance("INR");
        return new MerchantProfile(
            new MerchantId(entity.getId()),
            entity.getName(),
            entity.getMcc(),
            MerchantProfile.Status.valueOf(entity.getStatus()),
            Money.of(entity.getPerTxnLimit(), inr),
            Money.of(entity.getDailyLimit(), inr),
            entity.getMdrPercentage(),
            entity.getReservePercentage(),
            entity.getWebhookUrl(),
            entity.getWebhookSecret()
        );
    }
}
