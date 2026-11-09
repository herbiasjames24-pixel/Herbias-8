package com.yourdomain.dao;

import com.yourdomain.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** DAO for inserting and fetching audit-trail entries. */
public class ActivityLogDao {

    public void logAction(int userId, String actionType, String description) {
        String sql = "INSERT INTO activity_logs (user_id, action_type, description) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, actionType);
            ps.setString(3, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not write activity log: " + e.getMessage());
        }
    }

    public List<ActivityLog> getAllLogs() {
        // log_id DESC as tie-breaker so entries within the same second stay newest-first
        return query("SELECT log_id, user_id, action_type, description, timestamp "
                + "FROM activity_logs ORDER BY timestamp DESC, log_id DESC", null);
    }

    public List<ActivityLog> getLogsByUserId(int userId) {
        return query("SELECT log_id, user_id, action_type, description, timestamp "
                + "FROM activity_logs WHERE user_id = ? ORDER BY timestamp DESC, log_id DESC", userId);
    }

    /** Deletes every log row. Returns the number of rows removed. */
    public int clearAllLogs() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {
            return st.executeUpdate("DELETE FROM activity_logs");
        } catch (SQLException e) {
            System.err.println("Could not clear logs: " + e.getMessage());
            return 0;
        }
    }

    private List<ActivityLog> query(String sql, Integer userId) {
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new ActivityLog(
                            rs.getInt("log_id"),
                            rs.getInt("user_id"),
                            rs.getString("action_type"),
                            rs.getString("description"),
                            rs.getString("timestamp")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not read activity logs: " + e.getMessage());
        }
        return logs;
    }
}
