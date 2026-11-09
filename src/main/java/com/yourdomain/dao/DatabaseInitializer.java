package com.yourdomain.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    full_name     TEXT NOT NULL,
                    role          TEXT NOT NULL,
                    status        TEXT NOT NULL DEFAULT 'PENDING',
                    created_at    DATETIME DEFAULT (datetime('now','localtime'))
                )""");

            // [NEW] Deliverable 8 - audit trail table
            st.execute("""
                CREATE TABLE IF NOT EXISTS activity_logs (
                    log_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     INTEGER NOT NULL,
                    action_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    timestamp   DATETIME DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (user_id) REFERENCES users(user_id)
                )""");

            // Domain tables (Gasoline Station Management System)
            st.execute("""
                CREATE TABLE IF NOT EXISTS fuel_products (
                    product_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name    TEXT NOT NULL UNIQUE,
                    price_per_liter REAL NOT NULL,
                    stock_liters    REAL NOT NULL DEFAULT 0,
                    reorder_level   REAL NOT NULL DEFAULT 500
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    customer_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name      TEXT NOT NULL,
                    contact_number TEXT NOT NULL,
                    plate_number   TEXT,
                    user_id        INTEGER UNIQUE,
                    wallet_balance REAL NOT NULL DEFAULT 0,
                    loyalty_points INTEGER NOT NULL DEFAULT 0,
                    created_at     DATETIME DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (user_id) REFERENCES users(user_id)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS fuel_sales (
                    sale_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id     INTEGER NOT NULL,
                    cashier_id     INTEGER,
                    customer_id    INTEGER,
                    liters         REAL NOT NULL,
                    total_amount   REAL NOT NULL,
                    payment_method TEXT NOT NULL,
                    sale_datetime  DATETIME DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (product_id) REFERENCES fuel_products(product_id),
                    FOREIGN KEY (cashier_id) REFERENCES users(user_id)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                    delivery_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id      INTEGER NOT NULL,
                    supplier_name   TEXT NOT NULL,
                    liters_received REAL NOT NULL,
                    delivery_date   DATETIME DEFAULT (datetime('now','localtime')),
                    received_by     INTEGER NOT NULL,
                    FOREIGN KEY (product_id) REFERENCES fuel_products(product_id),
                    FOREIGN KEY (received_by) REFERENCES users(user_id)
                )""");

            // Seed default administrator (admin / admin123).
            // Read the count first and close the ResultSet BEFORE writing, otherwise SQLite reports "database is locked".
            int adminCount;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'")) {
                adminCount = rs.next() ? rs.getInt(1) : 0;
            }
            int productCount;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fuel_products")) {
                productCount = rs.next() ? rs.getInt(1) : 0;
            }
            if (productCount == 0) {
                st.execute("INSERT INTO fuel_products(product_name, price_per_liter, stock_liters, reorder_level) VALUES "
                        + "('Unleaded', 58.50, 2000, 500),"
                        + "('Premium', 64.20, 1500, 500),"
                        + "('Diesel', 55.75, 2500, 500)");
            }
            if (adminCount == 0) {
                st.execute("INSERT INTO users (username, password_hash, full_name, role, status) VALUES ('admin', '"
                        + UserDao.hashPassword("admin123") + "', 'System Administrator', 'ADMIN', 'ACTIVE')");
            }
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }
}
