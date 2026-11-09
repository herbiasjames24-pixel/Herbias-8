package com.yourdomain.model;

/** Outcome of a fuel sale attempt (cashier sale or customer self-service purchase). */
public record SaleResult(boolean success, int saleId, double liters, double total, int pointsEarned, String message) {
    public static SaleResult fail(String message) {
        return new SaleResult(false, 0, 0, 0, 0, message);
    }
}
