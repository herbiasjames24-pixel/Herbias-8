package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.CustomerDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.User;

import java.util.Optional;

/** Handles registration and login. */
public class Authentication {
    private final ConsoleUtil in;
    private final UserDao userDao;
    private final ActivityLogDao logDao;
    private final CustomerDao customerDao = new CustomerDao();

    public Authentication(ConsoleUtil in, UserDao userDao, ActivityLogDao logDao) {
        this.in = in;
        this.userDao = userDao;
        this.logDao = logDao;
    }

    /** Returns the logged-in user, or null if login failed. */
    public User login() {
        System.out.println("\n--- LOGIN ---");
        String username = in.readLine("Username: ");
        String password = in.readLine("Password: ");

        Optional<User> found = userDao.authenticate(username, UserDao.hashPassword(password));
        if (found.isEmpty()) {
            System.out.println("Invalid username or password.");
            return null;
        }
        User user = found.get();
        if (!"ACTIVE".equals(user.status())) {
            System.out.println("Your account is " + user.status() + ". Please wait for administrator approval.");
            return null;
        }
        logDao.logAction(user.userId(), "USER_LOGIN",
                user.role() + " [" + user.userId() + "] " + user.username() + " logged in");
        System.out.println("Welcome, " + user.fullName() + " (" + user.role() + ")!");
        return user;
    }

    public void register() {
        System.out.println("\n--- REGISTER NEW ACCOUNT ---");
        String fullName = in.readLine("Full name: ");
        String username = in.readLine("Username: ");
        String password = in.readLine("Password: ");
        System.out.println("Role:  1) Cashier / Pump Attendant   2) Inventory Clerk   3) Customer");
        int choice = in.readInt("Choose role: ");
        String role = choice == 3 ? "CUSTOMER" : choice == 2 ? "CLERK" : "CASHIER";
        String contact = "";
        String plate = "";
        if ("CUSTOMER".equals(role)) {
            contact = in.readLine("Contact number: ");
            plate = in.readLine("Vehicle plate number (optional): ").toUpperCase();
            if (contact.isEmpty()) {
                System.out.println("Contact number is required for customers.");
                return;
            }
        }

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            System.out.println("All fields are required.");
            return;
        }
        int id = userDao.createUser(username, UserDao.hashPassword(password), fullName, role, "PENDING");
        if (id > 0) {
            logDao.logAction(id, "USER_REGISTERED",
                    "User ID " + id + " (" + username + ") registered as " + role + " - awaiting approval");
            if ("CUSTOMER".equals(role)) {
                int cid = customerDao.addCustomer(fullName, contact, plate, id);
                logDao.logAction(id, "CUSTOMER_ADDED",
                        "Customer ID " + cid + " (" + fullName + ") self-registered with User ID " + id);
            }
            System.out.println("Registration submitted. An administrator must approve your account.");
        } else {
            System.out.println("Registration failed (username may already exist).");
        }
    }

    public void logout(User user) {
        logDao.logAction(user.userId(), "USER_LOGOUT",
                user.role() + " [" + user.userId() + "] " + user.username() + " logged out");
        System.out.println("Logged out.");
    }
}
