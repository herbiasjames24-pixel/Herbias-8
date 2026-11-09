package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.CustomerDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.Customer;
import com.yourdomain.model.User;

import java.util.List;

/** Add / view / search customers. Used by Admin and Cashier. */
public class CustomerMenu {
    private final ConsoleUtil in;
    private final User user;
    private final CustomerDao customerDao = new CustomerDao();
    private final ActivityLogDao logDao = new ActivityLogDao();

    public CustomerMenu(ConsoleUtil in, User user) {
        this.in = in;
        this.user = user;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== CUSTOMERS =====");
            System.out.println("1. Add Customer");
            System.out.println("2. View All Customers");
            System.out.println("3. Search Customers");
            System.out.println("4. Create Login Account for Customer");
            System.out.println("0. Back");
            switch (in.readInt("Choice: ")) {
                case 1 -> addCustomer();
                case 2 -> print(customerDao.findAll());
                case 3 -> print(customerDao.search(in.readLine("Search (name / contact / plate): ")));
                case 4 -> createLogin();
                case 0 -> {
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private void addCustomer() {
        String name = in.readLine("Full name: ");
        String contact = in.readLine("Contact number: ");
        String plate = in.readLine("Vehicle plate number (optional): ").toUpperCase();
        if (name.isEmpty() || contact.isEmpty()) {
            System.out.println("Name and contact number are required.");
            return;
        }
        int id = customerDao.addCustomer(name, contact, plate);
        if (id > 0) {
            logDao.logAction(user.userId(), "CUSTOMER_ADDED",
                    user.role() + " [" + user.userId() + "] added customer ID " + id + " (" + name + ")");
            System.out.println("Customer added with ID " + id + ".");
        }
    }

    /** Gives an existing customer a user account (role CUSTOMER). The account stays PENDING until an admin approves it. */
    private void createLogin() {
        int cid = in.readInt("Customer ID (0 to cancel): ");
        if (cid == 0) {
            return;
        }
        Customer c = customerDao.findById(cid).orElse(null);
        if (c == null) {
            System.out.println("Customer not found.");
            return;
        }
        if (c.userId() != null) {
            System.out.println("This customer already has a login (User ID " + c.userId() + ").");
            return;
        }
        String username = in.readLine("Username for customer: ");
        String password = in.readLine("Temporary password: ");
        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Username and password are required.");
            return;
        }
        int uid = new UserDao().createUser(username, UserDao.hashPassword(password), c.fullName(), "CUSTOMER", "PENDING");
        if (uid > 0 && customerDao.linkUser(cid, uid)) {
            logDao.logAction(user.userId(), "USER_REGISTERED",
                    user.role() + " [" + user.userId() + "] created login User ID " + uid + " for Customer ID " + cid);
            System.out.println("Account created (User ID " + uid + "). An administrator must approve it before login.");
        } else {
            System.out.println("Could not create the account (username may already exist).");
        }
    }

    static void print(List<Customer> customers) {
        if (customers.isEmpty()) {
            System.out.println("No customers found.");
            return;
        }
        System.out.printf("%-5s | %-22s | %-15s | %-10s | %-19s | %-7s | %-9s | %-6s%n",
                "ID", "Name", "Contact", "Plate", "Added", "User ID", "Wallet", "Points");
        System.out.println("-".repeat(112));
        for (Customer c : customers) {
            System.out.printf("%-5d | %-22s | %-15s | %-10s | %-19s | %-7s | %-9.2f | %-6d%n",
                    c.customerId(), c.fullName(), c.contactNumber(), c.plateNumber(), c.createdAt(),
                    c.userId() == null ? "-" : c.userId().toString(), c.walletBalance(), c.loyaltyPoints());
        }
    }
}
