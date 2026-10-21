package com.gov.landcheck.file.bundle;

/**
 * 一次性下载票据。浏览器跳转带不上登录头，用它代替会话令牌。
 */
public record SourceBundleTicket(long userId, String userName, long projectId, long expiresAtEpochMs) {
}
