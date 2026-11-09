package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.CustomerDao;
import com.yourdomain.dao.FuelDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.Customer;
import com.yourdomain.model.FuelProduct;
import com.yourdomain.model.FuelSale;
import com.yourdomain.model.SaleResult;
import com.yourdomain.model.User;

import java.util.List;

/** Self-service menu shown to users whose role is CUSTOMER. */
public class CustomerPortal {
    private static final double PESOS_PER_POINT = 50.0;   // 1 point per 50 pesos spent
    private static final int POINTS_PER_REDEEM = 100;     // 100 points ...
    private static final double REDEEM_VALUE = 10.0;      // ... = 10 pesos wallet credit
    private static final double MAX_TOPUP = 50000.0;

    private final ConsoleUtil in;
    private final User user;
    private final CustomerDao customerDao = new CustomerDao();
    private final FuelDao fuelDao = new FuelDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final UserDao userDao = new UserDao();

    public CustomerPortal(ConsoleUtil in, User user) {
        this.in = in;
        this.user = user;
    }

    public void show() {
        // Make sure this user has a linked customer profile (e.g. when an admin changed their role to CUSTOMER)
        if (customerDao.findByUserId(user.userId()).isEmpty()) {
            customerDao.addCustomer(user.fullName(), "N/A", "", user.userId());
        }
        while (true) {
            Customer me = customerDao.findByUserId(user.userId()).orElse(null);
            if (me == null) {
                System.out.println("Could not load your customer profile.");
                return;
            }
            System.out.println("\n===== CUSTOMER MENU =====");
            System.out.printf("Wallet: %.2f   |   Loyalty points: %d%n", me.walletBalance(), me.loyaltyPoints());
            System.out.println("1. View My Profile");
            System.out.println("2. View Fuel Prices");
            System.out.println("3. Buy Fuel");
            System.out.println("4. My Purchase History");
            System.out.println("5. Top Up Wallet");
            System.out.println("6. Redeem Loyalty Points");
            System.out.println("7. Update Contact / Plate Number");
            System.out.println("8. Change Password");
            System.out.println("0. Logout");
            switch (in.readInt("Choice: ")) {
                case 1 -> showProfile(me);
                case 2 -> StaffMenu.printProducts(fuelDao.findAll());
                case 3 -> buyFuel(me);
                case 4 -> printHistory(fuelDao.findSalesByCustomer(me.customerId()));
                case 5 -> topUp(me);
                case 6 -> redeemPoints(me);
                case 7 -> updateProfile(me);
                case 8 -> changePassword();
                case 0 -> {
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void showProfile(Customer me) {
        System.out.println("Customer ID    : " + me.customerId());
        System.out.println("Name           : " + me.fullName());
        System.out.println("Contact        : " + me.contactNumber());
        System.out.println("Plate No.      : " + me.plateNumber());
        System.out.println("Username       : " + user.username());
        System.out.printf("Wallet balance : %.2f%n", me.walletBalance());
        System.out.println("Loyalty points : " + me.loyaltyPoints());
    }

    // ---------- Buy fuel ----------
    private void buyFuel(Customer me) {
        List<FuelProduct> products = fuelDao.findAll();
        StaffMenu.printProducts(products);
        int pid = in.readInt("Product ID (0 to cancel): ");
        if (pid == 0) {
            return;
        }
        FuelProduct product = fuelDao.findById(pid).orElse(null);
        if (product == null) {
            System.out.println("Product not found.");
            return;
        }
        System.out.println("Buy by:  1) Liters   2) Peso amount");
        double liters;
        if (in.readInt("Choice: ") == 2) {
            double amount = in.readDouble("Amount in pesos: ");
            liters = Math.floor(amount / product.pricePerLiter() * 100.0) / 100.0;
        } else {
            liters = in.readDouble("Liters: ");
        }
        if (liters <= 0) {
            System.out.println("Amount is too small.");
            return;
        }
        if (liters > product.stockLiters()) {
            System.out.printf("Sorry, only %.2f L of %s is available.%n", product.stockLiters(), product.productName());
            return;
        }
        double total = Math.round(product.pricePerLiter() * liters * 100.0) / 100.0;

        System.out.println("Pay with:  1) Wallet (" + String.format("%.2f", me.walletBalance()) + ")   2) GCash   3) Card   4) Cash at pump");
        String payment = switch (in.readInt("Choice: ")) {
            case 1 -> "WALLET";
            case 2 -> "GCASH";
            case 3 -> "CARD";
            default -> "CASH";
        };

        System.out.printf("%nOrder: %.2f L of %s @ %.2f = %.2f (%s)%n",
                liters, product.productName(), product.pricePerLiter(), total, payment);
        if (!in.readLine("Confirm purchase? (Y/N): ").equalsIgnoreCase("Y")) {
            System.out.println("Purchase cancelled.");
            return;
        }
        SaleResult r = fuelDao.processSale(null, me.customerId(), pid, liters, payment);
        if (!r.success()) {
            System.out.println("Purchase failed: " + r.message());
            return;
        }
        logDao.logAction(user.userId(), "FUEL_PURCHASED",
                String.format("Customer [%d] bought %.2fL %s, total %.2f (%s)",
                        me.customerId(), r.liters(), product.productName(), r.total(), payment));
        System.out.println("\n------ RECEIPT ------");
        System.out.println("Receipt No. : " + r.saleId());
        System.out.println("Product     : " + product.productName());
        System.out.printf("Liters      : %.2f%n", r.liters());
        System.out.printf("Total       : %.2f%n", r.total());
        System.out.println("Payment     : " + payment);
        System.out.println("Points earned: " + r.pointsEarned());
        System.out.println("---------------------");
    }

    private void printHistory(List<FuelSale> sales) {
        if (sales.isEmpty()) {
            System.out.println("No purchases yet.");
            return;
        }
        System.out.printf("%-5s | %-12s | %-10s | %-10s | %-8s | %-19s%n",
                "ID", "Product", "Liters", "Total", "Payment", "Date");
        System.out.println("-".repeat(78));
        double sum = 0;
        for (FuelSale s : sales) {
            sum += s.totalAmount();
            System.out.printf("%-5d | %-12s | %-10.2f | %-10.2f | %-8s | %-19s%n",
                    s.saleId(), s.productName(), s.liters(), s.totalAmount(), s.paymentMethod(), s.saleDatetime());
        }
        System.out.printf("Total spent: %.2f%n", sum);
    }

    // ---------- Wallet ----------
    private void topUp(Customer me) {
        double amount = in.readDouble("Top-up amount (max " + (int) MAX_TOPUP + "): ");
        if (amount <= 0 || amount > MAX_TOPUP) {
            System.out.println("Invalid amount.");
            return;
        }
        System.out.println("Pay with:  1) GCash   2) Card   3) Cash at counter");
        String method = switch (in.readInt("Choice: ")) {
            case 1 -> "GCASH";
            case 2 -> "CARD";
            default -> "CASH";
        };
        if (customerDao.topUp(me.customerId(), amount)) {
            logDao.logAction(user.userId(), "WALLET_TOPUP",
                    String.format("Customer [%d] topped up wallet by %.2f via %s", me.customerId(), amount, method));
            System.out.printf("Wallet topped up by %.2f.%n", amount);
        }
    }

    private void redeemPoints(Customer me) {
        System.out.printf("Every %d points = %.2f wallet credit. You have %d points.%n",
                POINTS_PER_REDEEM, REDEEM_VALUE, me.loyaltyPoints());
        if (me.loyaltyPoints() < POINTS_PER_REDEEM) {
            System.out.println("You need at least " + POINTS_PER_REDEEM + " points to redeem.");
            return;
        }
        int blocks = in.readInt("How many sets of " + POINTS_PER_REDEEM + " points to redeem (0 to cancel)? ");
        if (blocks <= 0) {
            return;
        }
        int points = blocks * POINTS_PER_REDEEM;
        double credit = blocks * REDEEM_VALUE;
        if (customerDao.redeemPoints(me.customerId(), points, credit)) {
            logDao.logAction(user.userId(), "POINTS_REDEEMED",
                    String.format("Customer [%d] redeemed %d points for %.2f wallet credit", me.customerId(), points, credit));
            System.out.printf("Redeemed %d points for %.2f wallet credit.%n", points, credit);
        } else {
            System.out.println("Not enough points.");
        }
    }

    // ---------- Account ----------
    private void updateProfile(Customer me) {
        String contact = in.readLine("New contact number (blank = keep '" + me.contactNumber() + "'): ");
        String plate = in.readLine("New plate number (blank = keep '" + me.plateNumber() + "'): ").toUpperCase();
        if (contact.isEmpty()) {
            contact = me.contactNumber();
        }
        if (plate.isEmpty()) {
            plate = me.plateNumber();
        }
        if (customerDao.updateContact(me.customerId(), contact, plate)) {
            logDao.logAction(user.userId(), "CUSTOMER_UPDATED",
                    "Customer [" + me.customerId() + "] updated contact/plate details");
            System.out.println("Profile updated.");
        }
    }

    private void changePassword() {
        String oldPw = in.readLine("Current password: ");
        String newPw = in.readLine("New password: ");
        if (newPw.length() < 4) {
            System.out.println("New password must be at least 4 characters.");
            return;
        }
        if (userDao.changePassword(user.userId(), UserDao.hashPassword(oldPw), UserDao.hashPassword(newPw))) {
            logDao.logAction(user.userId(), "PASSWORD_CHANGED", "User [" + user.userId() + "] changed their password");
            System.out.println("Password changed.");
        } else {
            System.out.println("Current password is incorrect.");
        }
    }
}
