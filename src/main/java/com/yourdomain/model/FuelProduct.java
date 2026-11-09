package com.yourdomain.model;

/** Fuel product used by the Gasoline Station Management System. */
public record FuelProduct(int productId, String productName, double pricePerLiter,
                          double stockLiters, double reorderLevel) {
}
