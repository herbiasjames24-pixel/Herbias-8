package com.yourdomain.model;

/** One fuel purchase, as shown in a customer's purchase history. */
public record FuelSale(int saleId, String productName, double liters, double totalAmount,
                       String paymentMethod, String saleDatetime) {
}
