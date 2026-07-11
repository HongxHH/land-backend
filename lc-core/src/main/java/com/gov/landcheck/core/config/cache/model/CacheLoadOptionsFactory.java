package com.gov.landcheck.core.config.cache.model;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.cache.config.CacheProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CacheLoadOptionsFactory {

    private final CacheProperties cacheProperties;

    public CacheLoadOptions byIdOptions() {
        return CacheLoadOptions.builder()
                .ttlSeconds(cacheProperties.getTtl().getProject().getById())
                .useLock(true)
                .cacheNullValue(false)
                .lockWaitMs(cacheProperties.getLock().getWaitMs())
                .lockLeaseMs(cacheProperties.getLock().getLeaseMs())
                .build();
    }

    public CacheLoadOptions byRelationOptions() {
        return CacheLoadOptions.builder()
                .ttlSeconds(cacheProperties.getTtl().getProject().getByRelation())
                .useLock(true)
                .cacheNullValue(false)
                .lockWaitMs(cacheProperties.getLock().getWaitMs())
                .lockLeaseMs(cacheProperties.getLock().getLeaseMs())
                .build();
    }

    public CacheLoadOptions queryOptions() {
        return CacheLoadOptions.builder()
                .ttlSeconds(cacheProperties.getTtl().getProject().getQuery())
                .useLock(true)
                .cacheNullValue(false)
                .lockWaitMs(cacheProperties.getLock().getWaitMs())
                .lockLeaseMs(cacheProperties.getLock().getLeaseMs())
                .build();
    }
}
