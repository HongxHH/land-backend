package com.gov.landcheck.file.bundle;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 内存中的一次性下载票据。过期或项目不匹配都会作废，不写进日志。
 */
@Component
public class SourceBundleTicketStore {

    static final long TTL_MILLIS = 90_000;

    private final ConcurrentHashMap<String, SourceBundleTicket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(long userId, String userName, long projectId) {
        return issue(userId, userName, projectId, TTL_MILLIS);
    }

    String issue(long userId, String userName, long projectId, long ttlMillis) {
        purgeExpired();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        long expiresAt = System.currentTimeMillis() + Math.max(ttlMillis, 0);
        tickets.put(token, new SourceBundleTicket(userId, userName, projectId, expiresAt));
        return token;
    }

    public SourceBundleTicket peek(String token, long projectId) {
        SourceBundleTicket ticket = lookup(token, projectId, false);
        return ticket;
    }

    public SourceBundleTicket consume(String token, long projectId) {
        return lookup(token, projectId, true);
    }

    private SourceBundleTicket lookup(String token, long projectId, boolean remove) {
        if (token == null || token.isBlank()) {
            return null;
        }
        purgeExpired();
        SourceBundleTicket ticket = remove ? tickets.remove(token) : tickets.get(token);
        if (ticket == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now >= ticket.expiresAtEpochMs() || ticket.projectId() != projectId) {
            tickets.remove(token, ticket);
            return null;
        }
        return ticket;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtEpochMs());
    }
}
