package com.bettips.backend.config;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "tips",           // TipService.getFreeTips / getAllTips / getPremiumTips
                "valueBets",      // ValueBetService.getByCategory / getAll
                "userProfile",    // UserService.getUserProfile
                "paymentStatus",  // PayHeroService.getPaymentStatus
                "subscription"    // subscription lookups
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
        );
        return manager;
    }
}
