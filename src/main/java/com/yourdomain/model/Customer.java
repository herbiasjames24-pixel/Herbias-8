package com.yourdomain.model;

/** Gas station customer. */
public record Customer(int customerId, String fullName, String contactNumber, String plateNumber, String createdAt, Integer userId,
                       double walletBalance, int loyaltyPoints) {
}
