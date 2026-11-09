package com.yourdomain.model;

/** Immutable user entry (Java Record). */
public record User(int userId, String username, String fullName, String role, String status) {
}
