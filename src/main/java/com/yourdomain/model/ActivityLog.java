package com.yourdomain.model;

/** Immutable audit-log entry (Java Record). */
public record ActivityLog(int logId, int userId, String actionType, String description, String timestamp) {
}
