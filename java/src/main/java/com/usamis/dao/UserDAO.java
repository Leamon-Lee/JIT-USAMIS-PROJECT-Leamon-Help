package com.usamis.dao;

import com.usamis.model.Models.*;
import com.usamis.util.DatabaseConnection;
import com.usamis.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * WHY parameterised statements everywhere:
 * SQL injection is the #1 web vulnerability (OWASP Top 10).
 * PreparedStatement ensures user input is always treated as data,
 * never as SQL syntax — regardless of what the user types.
 */
public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    private static final String SELECT_WITH_ROLE =
        "SELECT u.id, u.username, u.password_hash, u.first_name, u.last_name, " +
        "       u.email, u.role_id, r.name AS role_name, u.status, " +
        "       u.created_at, u.last_login " +
        "FROM users u JOIN roles r ON u.role_id = r.id ";

    // ─── AUTHENTICATION ──────────────────────────────────────
    /**
     * Authenticate a user.
     * Returns Optional.empty() on any failure — never reveals
     * whether the username or password was wrong (prevents enumeration).
     */
    public Optional<User> authenticate(String username, String rawPassword) {
        String sql = SELECT_WITH_ROLE + "WHERE u.username = ? AND u.status = 'active'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                User user = mapUser(rs);
                if (!PasswordUtil.verify(rawPassword, user.passwordHash)) {
                    log.warn("Failed login attempt for username: {}", username);
                    return Optional.empty();
                }
                // Update last_login timestamp
                updateLastLogin(conn, user.id);
                user.passwordHash = null; // scrub before returning
                return Optional.of(user);
            }
        } catch (SQLException e) {
            log.error("Authentication error", e);
            return Optional.empty();
        }
    }

    private void updateLastLogin(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE users SET last_login = NOW() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // ─── CRUD ────────────────────────────────────────────────
    public List<UserDTO> findAll() {
        List<UserDTO> list = new ArrayList<>();
        String sql = SELECT_WITH_ROLE + "ORDER BY u.id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapUser(rs).toDTO());
        } catch (SQLException e) {
            log.error("findAll users", e);
        }
        return list;
    }

    public Optional<UserDTO> findById(int id) {
        String sql = SELECT_WITH_ROLE + "WHERE u.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapUser(rs).toDTO());
            }
        } catch (SQLException e) {
            log.error("findById user {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<UserDTO> findByUsername(String username) {
        String sql = SELECT_WITH_ROLE + "WHERE u.username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapUser(rs).toDTO());
            }
        } catch (SQLException e) {
            log.error("findByUsername {}", username, e);
        }
        return Optional.empty();
    }

    public int create(String username, String rawPassword, String firstName,
                      String lastName, String email, int roleId) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, first_name, last_name, email, role_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            ps.setString(2, PasswordUtil.hash(rawPassword));
            ps.setString(3, firstName.trim());
            ps.setString(4, lastName.trim());
            ps.setString(5, email.trim().toLowerCase());
            ps.setInt(6, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int newId = rs.getInt(1);
                log.info("Created user id={} username={}", newId, username);
                return newId;
            }
        }
    }

    public boolean updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("updateStatus user {}", userId, e);
            return false;
        }
    }

    public boolean changePassword(int userId, String newRawPassword) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hash(newRawPassword));
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("changePassword user {}", userId, e);
            return false;
        }
    }

    public boolean delete(int userId) {
        // Soft-delete: set status=inactive (preserve audit trail)
        return updateStatus(userId, "inactive");
    }

    // ─── PERMISSION CHECK ────────────────────────────────────
    /**
     * Single query to check if a role has a permission.
     * Called on EVERY protected action — must be fast.
     * WHY: Business logic must never assume client-side checks ran.
     */
    public boolean hasPermission(int roleId, String permissionName) {
        String sql = "SELECT 1 FROM role_permissions rp " +
                     "JOIN permissions p ON rp.permission_id = p.id " +
                     "WHERE rp.role_id = ? AND p.name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setString(2, permissionName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("hasPermission check failed", e);
            return false; // Fail-closed: deny on error
        }
    }

    // ─── MAPPER ──────────────────────────────────────────────
    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.id           = rs.getInt("id");
        u.username     = rs.getString("username");
        u.passwordHash = rs.getString("password_hash");
        u.firstName    = rs.getString("first_name");
        u.lastName     = rs.getString("last_name");
        u.email        = rs.getString("email");
        u.roleId       = rs.getInt("role_id");
        u.roleName     = rs.getString("role_name");
        u.status       = rs.getString("status");
        Timestamp ca   = rs.getTimestamp("created_at");
        Timestamp ll   = rs.getTimestamp("last_login");
        u.createdAt    = ca != null ? ca.toLocalDateTime() : null;
        u.lastLogin    = ll != null ? ll.toLocalDateTime() : null;
        return u;
    }
}
