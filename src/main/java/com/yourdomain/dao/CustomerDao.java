package com.yourdomain.dao;

import com.yourdomain.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDao {

    /** Returns the new customer_id, or -1 on failure. */
    public int addCustomer(String fullName, String contactNumber, String plateNumber) {
        return addCustomer(fullName, contactNumber, plateNumber, null);
    }

    /** Same as above, but links the customer to a user account (userId may be null). */
    public int addCustomer(String fullName, String contactNumber, String plateNumber, Integer userId) {
        String sql = "INSERT INTO customers (full_name, contact_number, plate_number, user_id) VALUES (?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, contactNumber);
            ps.setString(3, plateNumber);
            if (userId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, userId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("Could not add customer: " + e.getMessage());
            return -1;
        }
    }

    public List<Customer> findAll() {
        return query("SELECT * FROM customers ORDER BY customer_id", null);
    }

    /** Search by name, contact number or plate number. */
    public List<Customer> search(String keyword) {
        return query("SELECT * FROM customers WHERE full_name LIKE ? OR contact_number LIKE ? "
                + "OR plate_number LIKE ? ORDER BY customer_id", "%" + keyword + "%");
    }

    public Optional<Customer> findById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Customer> findByUserId(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean topUp(int customerId, double amount) {
        return exec("UPDATE customers SET wallet_balance = wallet_balance + ? WHERE customer_id = ?", amount, customerId);
    }

    /** Converts points into wallet credit. Fails if the customer does not have enough points. */
    public boolean redeemPoints(int customerId, int points, double credit) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE customers SET loyalty_points = loyalty_points - ?, wallet_balance = wallet_balance + ? "
                             + "WHERE customer_id = ? AND loyalty_points >= ?")) {
            ps.setInt(1, points);
            ps.setDouble(2, credit);
            ps.setInt(3, customerId);
            ps.setInt(4, points);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Redeem failed: " + e.getMessage());
            return false;
        }
    }

    public boolean updateContact(int customerId, String contact, String plate) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE customers SET contact_number = ?, plate_number = ? WHERE customer_id = ?")) {
            ps.setString(1, contact);
            ps.setString(2, plate);
            ps.setInt(3, customerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }

    private boolean exec(String sql, double value, int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, value);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }

    public boolean linkUser(int customerId, int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE customers SET user_id = ? WHERE customer_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Could not link account: " + e.getMessage());
            return false;
        }
    }

    private List<Customer> query(String sql, String like) {
        List<Customer> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (like != null) {
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return list;
    }

    private Integer linkedUser(ResultSet rs) throws SQLException {
        int v = rs.getInt("user_id");
        return rs.wasNull() ? null : v;
    }

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("customer_id"), rs.getString("full_name"),
                rs.getString("contact_number"), rs.getString("plate_number"), rs.getString("created_at"), linkedUser(rs),
                rs.getDouble("wallet_balance"), rs.getInt("loyalty_points"));
    }
}
