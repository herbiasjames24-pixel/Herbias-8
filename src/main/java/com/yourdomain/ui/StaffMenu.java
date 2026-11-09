package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.CustomerDao;
import com.yourdomain.dao.FuelDao;
import com.yourdomain.model.FuelProduct;
import com.yourdomain.model.SaleResult;
import com.yourdomain.model.User;

import java.util.List;
import java.util.Optional;

/** Menu for Cashier (fuel sales) and Inventory Clerk (deliveries). */
public class StaffMenu {
    private final ConsoleUtil in;
    private final User user;
    private final FuelDao fuelDao = new FuelDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final CustomerDao customerDao = new CustomerDao();

    public StaffMenu(ConsoleUtil in, User user) {
        this.in = in;
        this.user = user;
    }

    public void show() {
        boolean cashier = "CASHIER".equals(user.role());
        while (true) {
            System.out.println("\n===== " + (cashier ? "CASHIER" : "INVENTORY CLERK") + " MENU =====");
            System.out.println("1. View Fuel Prices & Stock");
            System.out.println(cashier ? "2. Record Fuel Sale" : "2. Record Fuel Delivery");
            if (cashier) {
                System.out.println("3. Manage Customers");
            }
            System.out.println("0. Logout");
            switch (in.readInt("Choice: ")) {
                case 1 -> printProducts(fuelDao.findAll());
                case 2 -> {
                    if (cashier) {
                        recordSale();
                    } else {
                        recordDelivery();
                    }
                }
                case 3 -> {
                    if (cashier) {
                        new CustomerMenu(in, user).show();
                    } else {
                        System.out.println("Invalid choice.");
                    }
                }
                case 0 -> {
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void recordSale() {
        printProducts(fuelDao.findAll());
        int pid = in.readInt("Product ID (0 to cancel): ");
        if (pid == 0) {
            return;
        }
        Optional<FuelProduct> p = fuelDao.findById(pid);
        if (p.isEmpty()) {
            System.out.println("Product not found.");
            return;
        }
        Integer customerId = null;
        String customerLabel = "walk-in";
        int cid = in.readInt("Customer ID (0 = walk-in customer): ");
        if (cid > 0) {
            var customer = customerDao.findById(cid);
            if (customer.isEmpty()) {
                System.out.println("Customer not found. Add the customer first.");
                return;
            }
            customerId = cid;
            customerLabel = "customer " + cid + " " + customer.get().fullName();
            System.out.printf("Customer wallet balance: %.2f%n", customer.get().walletBalance());
        }
        double liters = in.readDouble("Liters: ");
        String payment = in.readLine("Payment method (CASH/GCASH/CARD/WALLET): ").toUpperCase();
        if (payment.isEmpty()) {
            payment = "CASH";
        }
        if (!List.of("CASH", "GCASH", "CARD", "WALLET").contains(payment)) {
            System.out.println("Invalid payment method.");
            return;
        }
        SaleResult r = fuelDao.processSale(user.userId(), customerId, pid, liters, payment);
        if (!r.success()) {
            System.out.println("Sale failed: " + r.message());
            return;
        }
        logDao.logAction(user.userId(), "FUEL_SALE_RECORDED",
                String.format("Cashier [%d] sold %.2fL %s to %s, total %.2f (%s)",
                        user.userId(), liters, p.get().productName(), customerLabel, r.total(), payment));
        System.out.printf("Sale recorded. Total: %.2f%n", r.total());
        if (r.pointsEarned() > 0) {
            System.out.println("Loyalty points earned: " + r.pointsEarned());
        }

        fuelDao.findById(pid).ifPresent(updated -> {
            if (updated.stockLiters() <= updated.reorderLevel()) {
                System.out.println("LOW STOCK ALERT: " + updated.productName() + " is at or below reorder level.");
            }
        });
    }

    private void recordDelivery() {
        printProducts(fuelDao.findAll());
        int pid = in.readInt("Product ID (0 to cancel): ");
        if (pid == 0) {
            return;
        }
        Optional<FuelProduct> p = fuelDao.findById(pid);
        if (p.isEmpty()) {
            System.out.println("Product not found.");
            return;
        }
        String supplier = in.readLine("Supplier name: ");
        double liters = in.readDouble("Liters received: ");
        if (supplier.isEmpty() || liters <= 0) {
            System.out.println("Supplier and a positive amount are required.");
            return;
        }
        if (fuelDao.recordDelivery(pid, user.userId(), supplier, liters)) {
            logDao.logAction(user.userId(), "DELIVERY_RECEIVED",
                    String.format("Clerk [%d] received %.2fL %s from %s",
                            user.userId(), liters, p.get().productName(), supplier));
            System.out.println("Delivery recorded and stock updated.");
        }
    }

    static void printProducts(List<FuelProduct> products) {
        System.out.printf("%-5s | %-12s | %-10s | %-12s | %-8s%n", "ID", "Product", "Price/L", "Stock (L)", "Reorder");
        System.out.println("-".repeat(58));
        for (FuelProduct p : products) {
            System.out.printf("%-5d | %-12s | %-10.2f | %-12.2f | %-8.0f%n",
                    p.productId(), p.productName(), p.pricePerLiter(), p.stockLiters(), p.reorderLevel());
        }
    }
}
