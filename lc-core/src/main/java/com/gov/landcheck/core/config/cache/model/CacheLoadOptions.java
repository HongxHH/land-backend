package com.gov.landcheck.core.config.cache.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheLoadOptions {
    long ttlSeconds;
    boolean useLock;
    boolean cacheNullValue;
    long lockWaitMs;
    long lockLeaseMs;
}
