package com.yourdomain.dao;

import com.yourdomain.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class UserDao {

    public static String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns the new user_id, or -1 on failure (e.g. duplicate username). */
    public int createUser(String username, String passwordHash, String fullName, String role, String status) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, role);
            ps.setString(5, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("Could not create user: " + e.getMessage());
            return -1;
        }
    }

    public Optional<User> authenticate(String username, String passwordHash) {
        String sql = "SELECT user_id, username, full_name, role, status FROM users "
                + "WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Login query failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<User> findById(int userId) {
        String sql = "SELECT user_id, username, full_name, role, status FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lookup failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        return list("SELECT user_id, username, full_name, role, status FROM users ORDER BY user_id");
    }

    public List<User> findPending() {
        return list("SELECT user_id, username, full_name, role, status FROM users "
                + "WHERE status = 'PENDING' ORDER BY user_id");
    }

    public boolean updateStatus(int userId, String status) {
        return update("UPDATE users SET status = ? WHERE user_id = ?", status, userId);
    }

    public boolean updateRole(int userId, String role) {
        return update("UPDATE users SET role = ? WHERE user_id = ?", role, userId);
    }

    /** Changes the password only if the old hash matches. */
    public boolean changePassword(int userId, String oldHash, String newHash) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET password_hash = ? WHERE user_id = ? AND password_hash = ?")) {
            ps.setString(1, newHash);
            ps.setInt(2, userId);
            ps.setString(3, oldHash);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Password change failed: " + e.getMessage());
            return false;
        }
    }

    private boolean update(String sql, String value, int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }

    private List<User> list(String sql) {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                users.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return users;
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(rs.getInt("user_id"), rs.getString("username"),
                rs.getString("full_name"), rs.getString("role"), rs.getString("status"));
    }
}
