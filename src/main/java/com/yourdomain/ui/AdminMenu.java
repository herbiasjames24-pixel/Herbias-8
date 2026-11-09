package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.FuelDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.ActivityLog;
import com.yourdomain.model.FuelProduct;
import com.yourdomain.model.User;

import java.util.List;
import java.util.Optional;

public class AdminMenu {
    private static final List<String> ROLES = List.of("ADMIN", "CASHIER", "CLERK", "CUSTOMER");
    private static final List<String> STATUSES = List.of("ACTIVE", "INACTIVE", "PENDING");

    private final ConsoleUtil in;
    private final User admin;
    private final UserDao userDao = new UserDao();
    private final FuelDao fuelDao = new FuelDao();
    private final ActivityLogDao logDao = new ActivityLogDao();

    public AdminMenu(ConsoleUtil in, User admin) {
        this.in = in;
        this.admin = admin;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== ADMIN MENU =====");
            System.out.println("1. Approve Pending Accounts");
            System.out.println("2. Update User Role / Status");
            System.out.println("3. View All Users");
            System.out.println("4. Update Fuel Price");
            System.out.println("5. View Fuel Stock");
            System.out.println("6. View System Audit Logs");
            System.out.println("7. Manage Customers");
            System.out.println("0. Logout");
            switch (in.readInt("Choice: ")) {
                case 1 -> approveAccounts();
                case 2 -> updateUser();
                case 3 -> printUsers(userDao.findAll());
                case 4 -> updatePrice();
                case 5 -> StaffMenu.printProducts(fuelDao.findAll());
                case 6 -> auditLogMenu();
                case 7 -> new CustomerMenu(in, admin).show();
                case 0 -> {
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- Deliverable 5: account approval ----------
    private void approveAccounts() {
        List<User> pending = userDao.findPending();
        if (pending.isEmpty()) {
            System.out.println("No pending accounts.");
            return;
        }
        printUsers(pending);
        int id = in.readInt("Enter User ID to approve (0 to cancel): ");
        if (id == 0) {
            return;
        }
        boolean isPending = pending.stream().anyMatch(u -> u.userId() == id);
        if (!isPending) {
            System.out.println("That user is not in the pending list.");
            return;
        }
        if (userDao.updateStatus(id, "ACTIVE")) {
            logDao.logAction(admin.userId(), "USER_APPROVED",
                    "Admin [" + admin.userId() + "] approved account for User ID " + id);
            System.out.println("Account approved.");
        }
    }

    // ---------- Deliverable 7: role / status update ----------
    private void updateUser() {
        printUsers(userDao.findAll());
        int id = in.readInt("Enter User ID to update (0 to cancel): ");
        if (id == 0) {
            return;
        }
        Optional<User> target = userDao.findById(id);
        if (target.isEmpty()) {
            System.out.println("User not found.");
            return;
        }
        System.out.println("1. Change role   2. Change status");
        int what = in.readInt("Choice: ");
        if (what == 1) {
            String role = in.readLine("New role " + ROLES + ": ").toUpperCase();
            if (!ROLES.contains(role)) {
                System.out.println("Invalid role.");
                return;
            }
            if (id == admin.userId() && !"ADMIN".equals(role)) {
                System.out.println("You cannot remove your own admin role.");
                return;
            }
            if (userDao.updateRole(id, role)) {
                logDao.logAction(admin.userId(), "USER_UPDATED",
                        "Admin [" + admin.userId() + "] changed role of User " + id + " to " + role);
                System.out.println("Role updated.");
            }
        } else if (what == 2) {
            String status = in.readLine("New status " + STATUSES + ": ").toUpperCase();
            if (!STATUSES.contains(status)) {
                System.out.println("Invalid status.");
                return;
            }
            if (id == admin.userId() && !"ACTIVE".equals(status)) {
                System.out.println("You cannot deactivate your own account.");
                return;
            }
            if (userDao.updateStatus(id, status)) {
                logDao.logAction(admin.userId(), "USER_UPDATED",
                        "Admin [" + admin.userId() + "] changed status of User " + id + " to " + status);
                System.out.println("Status updated.");
            }
        }
    }

    // ---------- Domain action: price change ----------
    private void updatePrice() {
        StaffMenu.printProducts(fuelDao.findAll());
        int id = in.readInt("Product ID (0 to cancel): ");
        if (id == 0) {
            return;
        }
        Optional<FuelProduct> product = fuelDao.findById(id);
        if (product.isEmpty()) {
            System.out.println("Product not found.");
            return;
        }
        double newPrice = in.readDouble("New price per liter: ");
        if (newPrice <= 0) {
            System.out.println("Price must be greater than zero.");
            return;
        }
        double oldPrice = product.get().pricePerLiter();
        if (fuelDao.updatePrice(id, newPrice)) {
            logDao.logAction(admin.userId(), "PRICE_CHANGED",
                    String.format("Admin [%d] changed %s price from %.2f to %.2f",
                            admin.userId(), product.get().productName(), oldPrice, newPrice));
            System.out.println("Price updated.");
        }
    }

    // ---------- Deliverable 8: audit log interface ----------
    private void auditLogMenu() {
        while (true) {
            System.out.println("\n===== SYSTEM AUDIT LOGS =====");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear All Logs");
            System.out.println("0. Back");
            switch (in.readInt("Choice: ")) {
                case 1 -> printLogs(logDao.getAllLogs());
                case 2 -> {
                    int uid = in.readInt("Enter User ID: ");
                    printLogs(logDao.getLogsByUserId(uid));
                }
                case 3 -> clearLogs();
                case 0 -> {
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void clearLogs() {
        System.out.println("WARNING: This permanently deletes ALL audit log entries.");
        String confirm = in.readLine("Type CLEAR to confirm: ");
        if (!"CLEAR".equals(confirm)) {
            System.out.println("Cancelled. No logs were deleted.");
            return;
        }
        int removed = logDao.clearAllLogs();
        // The clearing itself is recorded so the audit trail is never silently wiped.
        logDao.logAction(admin.userId(), "LOGS_CLEARED",
                "Admin [" + admin.userId() + "] cleared " + removed + " log entries");
        System.out.println(removed + " log entries cleared.");
    }

    private static final String LOG_FORMAT = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
    private static final int DESC_WIDTH = 35;

    private void printLogs(List<ActivityLog> logs) {
        if (logs.isEmpty()) {
            System.out.println("No log entries found.");
            return;
        }
        String line = "-".repeat(5 + 3 + 8 + 3 + 18 + 3 + 35 + 3 + 20);
        System.out.println(line);
        System.out.printf(LOG_FORMAT, "ID", "User ID", "Action Type", "Description", "Timestamp");
        System.out.println(line);
        for (ActivityLog log : logs) {
            String desc = log.description();
            // Wrap long descriptions across several lines so nothing is cut off
            System.out.printf(LOG_FORMAT, log.logId(), log.userId(), log.actionType(),
                    desc.substring(0, Math.min(DESC_WIDTH, desc.length())), log.timestamp());
            for (int i = DESC_WIDTH; i < desc.length(); i += DESC_WIDTH) {
                System.out.printf(LOG_FORMAT, "", "", "",
                        desc.substring(i, Math.min(i + DESC_WIDTH, desc.length())), "");
            }
        }
        System.out.println(line);
        System.out.println(logs.size() + " entries.");
    }

    private void printUsers(List<User> users) {
        System.out.printf("%-5s | %-15s | %-22s | %-8s | %-8s%n", "ID", "Username", "Full Name", "Role", "Status");
        System.out.println("-".repeat(70));
        for (User u : users) {
            System.out.printf("%-5d | %-15s | %-22s | %-8s | %-8s%n",
                    u.userId(), u.username(), u.fullName(), u.role(), u.status());
        }
    }
}
