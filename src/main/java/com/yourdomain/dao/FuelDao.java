package com.yourdomain.dao;

import com.yourdomain.model.FuelProduct;
import com.yourdomain.model.FuelSale;
import com.yourdomain.model.SaleResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Domain DAO: fuel products, sales and deliveries. */
public class FuelDao {

    public List<FuelProduct> findAll() {
        List<FuelProduct> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fuel_products ORDER BY product_id")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return list;
    }

    public Optional<FuelProduct> findById(int productId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fuel_products WHERE product_id = ?")) {
            ps.setInt(1, productId);
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

    public List<FuelSale> findSalesByCustomer(int customerId) {
        List<FuelSale> list = new ArrayList<>();
        String sql = "SELECT s.sale_id, p.product_name, s.liters, s.total_amount, s.payment_method, s.sale_datetime "
                + "FROM fuel_sales s JOIN fuel_products p ON p.product_id = s.product_id "
                + "WHERE s.customer_id = ? ORDER BY s.sale_datetime DESC, s.sale_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FuelSale(rs.getInt(1), rs.getString(2), rs.getDouble(3),
                            rs.getDouble(4), rs.getString(5), rs.getString(6)));
                }
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return list;
    }

    public boolean updatePrice(int productId, double newPrice) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE fuel_products SET price_per_liter = ? WHERE product_id = ?")) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Records a fuel sale in ONE transaction: checks stock, charges the wallet (if paying by WALLET),
     * saves the sale, deducts stock and awards loyalty points.
     * cashierId is null for customer self-service; customerId is null for walk-in customers.
     */
    public SaleResult processSale(Integer cashierId, Integer customerId, int productId, double liters, String payment) {
        if (liters <= 0) {
            return SaleResult.fail("Liters must be greater than zero.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String name = null;
                double price = 0;
                double stock = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT product_name, price_per_liter, stock_liters FROM fuel_products WHERE product_id = ?")) {
                    ps.setInt(1, productId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return SaleResult.fail("Product not found.");
                        }
                        name = rs.getString(1);
                        price = rs.getDouble(2);
                        stock = rs.getDouble(3);
                    }
                }
                if (stock < liters) {
                    conn.rollback();
                    return SaleResult.fail(String.format("Insufficient stock (%.2f L of %s available).", stock, name));
                }
                double total = Math.round(price * liters * 100.0) / 100.0;

                if ("WALLET".equals(payment)) {
                    if (customerId == null) {
                        conn.rollback();
                        return SaleResult.fail("Wallet payment requires a registered customer.");
                    }
                    double balance = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT wallet_balance FROM customers WHERE customer_id = ?")) {
                        ps.setInt(1, customerId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                balance = rs.getDouble(1);
                            }
                        }
                    }
                    if (balance < total) {
                        conn.rollback();
                        return SaleResult.fail(String.format("Insufficient wallet balance (%.2f, need %.2f).", balance, total));
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE customers SET wallet_balance = wallet_balance - ? WHERE customer_id = ?")) {
                        ps.setDouble(1, total);
                        ps.setInt(2, customerId);
                        ps.executeUpdate();
                    }
                }

                int saleId;
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO fuel_sales (product_id, cashier_id, customer_id, liters, total_amount, payment_method) "
                                + "VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setInt(1, productId);
                    if (cashierId == null) {
                        ins.setNull(2, java.sql.Types.INTEGER);
                    } else {
                        ins.setInt(2, cashierId);
                    }
                    if (customerId == null) {
                        ins.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        ins.setInt(3, customerId);
                    }
                    ins.setDouble(4, liters);
                    ins.setDouble(5, total);
                    ins.setString(6, payment);
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        saleId = keys.next() ? keys.getInt(1) : 0;
                    }
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE fuel_products SET stock_liters = stock_liters - ? WHERE product_id = ?")) {
                    upd.setDouble(1, liters);
                    upd.setInt(2, productId);
                    upd.executeUpdate();
                }
                int points = 0;
                if (customerId != null) {
                    points = (int) (total / 50.0);   // 1 loyalty point per 50 pesos
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE customer_id = ?")) {
                        ps.setInt(1, points);
                        ps.setInt(2, customerId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                return new SaleResult(true, saleId, liters, total, points, "OK");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Sale failed: " + e.getMessage());
            return SaleResult.fail("Database error: " + e.getMessage());
        }
    }

    /** Records a supplier delivery and adds to stock. */
    public boolean recordDelivery(int productId, int receivedBy, String supplier, double liters) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO deliveries (product_id, supplier_name, liters_received, received_by) VALUES (?,?,?,?)");
                 PreparedStatement upd = conn.prepareStatement(
                         "UPDATE fuel_products SET stock_liters = stock_liters + ? WHERE product_id = ?")) {
                ins.setInt(1, productId);
                ins.setString(2, supplier);
                ins.setDouble(3, liters);
                ins.setInt(4, receivedBy);
                ins.executeUpdate();
                upd.setDouble(1, liters);
                upd.setInt(2, productId);
                upd.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Delivery failed: " + e.getMessage());
            return false;
        }
    }

    private FuelProduct map(ResultSet rs) throws SQLException {
        return new FuelProduct(rs.getInt("product_id"), rs.getString("product_name"),
                rs.getDouble("price_per_liter"), rs.getDouble("stock_liters"), rs.getDouble("reorder_level"));
    }
}
