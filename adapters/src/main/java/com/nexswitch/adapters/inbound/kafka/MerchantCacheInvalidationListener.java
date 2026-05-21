package com.nexswitch.adapters.inbound.kafka;

import com.nexswitch.adapters.outbound.cache.CachingMerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// LEARN: EventDrivenInvalidation — instead of relying solely on TTL (which means stale data
//        for up to 5 minutes after a merchant config change), we proactively evict the cache
//        entry when a merchant.config.updated event arrives. The TTL is a safety net only.
//        This gives near-real-time consistency for config changes (webhook URL, status, limits)
//        without sacrificing cache hit rate on the hot path.
@Component
public class MerchantCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantCacheInvalidationListener.class);

    private final CachingMerchantRepository cachingMerchantRepository;

    public MerchantCacheInvalidationListener(CachingMerchantRepository cachingMerchantRepository) {
        this.cachingMerchantRepository = cachingMerchantRepository;
    }

    @KafkaListener(topics = "merchant.config.updated", groupId = "acquiring-service-cache-invalidation")
    public void onMerchantConfigUpdated(String merchantId) {
        log.info("merchant.cache.invalidating_on_event merchantId={}", merchantId);
        cachingMerchantRepository.invalidate(merchantId);
    }
}
