package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.dao.UserDAO;
import com.usamis.model.Models.UserDTO;
import com.usamis.util.JsonUtil;
import com.usamis.util.PasswordUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

/**
 * REST API for System Users (admin-only for write operations):
 *   GET    /api/users         → list all
 *   GET    /api/users/{id}    → single user
 *   POST   /api/users         → create user
 *   PUT    /api/users/{id}    → update (status, role)
 *   DELETE /api/users/{id}    → deactivate
 *   POST   /api/users/{id}/password → change password
 */
@WebServlet(urlPatterns = {"/api/users", "/api/users/*"})
public class UserServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(UserServlet.class);
    private final UserDAO     userDAO     = new UserDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!"admin".equals(user.roleName)) { JsonUtil.forbidden(resp); return; }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.length() > 1) {
            int id = ValidationUtil.parseInt(pathInfo.substring(1), -1);
            if (id < 0) { JsonUtil.badRequest(resp, "Invalid user ID"); return; }
            userDAO.findById(id)
                .ifPresentOrElse(
                    u -> { try { JsonUtil.success(resp, u); } catch (IOException e) { throw new RuntimeException(e); } },
                    () -> { try { JsonUtil.notFound(resp, "User"); } catch (IOException e) { throw new RuntimeException(e); } }
                );
            return;
        }
        JsonUtil.success(resp, userDAO.findAll());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        // POST /api/users/{id}/password — change password (own or admin)
        if (pathInfo != null && pathInfo.matches("/\\d+/password")) {
            handlePasswordChange(req, resp, user);
            return;
        }

        // Create new user — admin only
        if (!"admin".equals(user.roleName)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        String username  = j.has("username")  ? j.get("username").getAsString().trim().toLowerCase() : null;
        String password  = j.has("password")  ? j.get("password").getAsString() : null;
        String firstName = j.has("firstName") ? j.get("firstName").getAsString().trim() : null;
        String lastName  = j.has("lastName")  ? j.get("lastName").getAsString().trim() : null;
        String email     = j.has("email")     ? j.get("email").getAsString().trim().toLowerCase() : null;
        int    roleId    = j.has("roleId")    ? j.get("roleId").getAsInt() : 5;

        // Validate
        if (ValidationUtil.isBlank(username) || ValidationUtil.isBlank(password)
                || ValidationUtil.isBlank(firstName) || ValidationUtil.isBlank(email)) {
            JsonUtil.badRequest(resp, "username, password, firstName, email are required."); return;
        }
        if (!ValidationUtil.isValidEmail(email)) { JsonUtil.badRequest(resp, "Invalid email format."); return; }
        if (!PasswordUtil.isStrong(password)) {
            JsonUtil.badRequest(resp, "Password must be ≥8 characters with an uppercase letter and number."); return;
        }
        if (userDAO.findByUsername(username).isPresent()) {
            JsonUtil.conflict(resp, "Username '" + username + "' already exists."); return;
        }

        try {
            int newId = userDAO.create(username, password, firstName,
                lastName != null ? lastName : "", email, roleId);
            academicDAO.log(user.id, user.username, user.roleName, "CREATE", "Users",
                "Created user '" + username + "' with role_id=" + roleId, getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("id", newId);
            result.addProperty("message", "User '" + username + "' created.");
            JsonUtil.ok(resp, result);
        } catch (SQLException e) {
            log.error("Create user", e);
            // Check for unique constraint violation
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                JsonUtil.conflict(resp, "Username or email already exists.");
            } else {
                JsonUtil.serverError(resp, e.getMessage());
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!"admin".equals(user.roleName)) { JsonUtil.forbidden(resp); return; }

        int id = parseId(req);
        if (id < 0) { JsonUtil.badRequest(resp, "User ID required"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        // Only status update for PUT (role changes require more logic)
        if (j.has("status")) {
            String status = j.get("status").getAsString();
            if (!status.matches("active|inactive|blocked")) {
                JsonUtil.badRequest(resp, "status must be: active, inactive, or blocked."); return;
            }
            // Prevent locking out last admin
            if (id == 1 && !"active".equals(status)) {
                JsonUtil.badRequest(resp, "Cannot deactivate the primary system administrator."); return;
            }
            boolean ok = userDAO.updateStatus(id, status);
            if (ok) {
                academicDAO.log(user.id, user.username, user.roleName, "UPDATE", "Users",
                    "Set user #" + id + " status to " + status, getIp(req), null);
                JsonUtil.success(resp, "User status updated to " + status + ".");
            } else { JsonUtil.notFound(resp, "User"); }
        } else {
            JsonUtil.badRequest(resp, "No updatable fields provided. Supported: status");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!"admin".equals(user.roleName)) { JsonUtil.forbidden(resp); return; }

        int id = parseId(req);
        if (id < 0) { JsonUtil.badRequest(resp, "User ID required"); return; }
        if (id == user.id) { JsonUtil.badRequest(resp, "Cannot delete your own account."); return; }

        boolean ok = userDAO.delete(id); // soft delete: sets status=inactive
        if (ok) {
            academicDAO.log(user.id, user.username, user.roleName, "DELETE", "Users",
                "Deactivated user #" + id, getIp(req), null);
            JsonUtil.success(resp, "User deactivated.");
        } else { JsonUtil.notFound(resp, "User"); }
    }

    // ─── PASSWORD CHANGE ────────────────────────────────────
    private void handlePasswordChange(HttpServletRequest req, HttpServletResponse resp, UserDTO currentUser)
            throws IOException {
        String pi    = req.getPathInfo();
        int targetId = ValidationUtil.parseInt(pi.replaceAll("[^\\d]", ""), -1);
        if (targetId < 0) { JsonUtil.badRequest(resp, "Invalid user ID"); return; }

        // Users can change their own password; admin can change anyone's
        if (targetId != currentUser.id && !"admin".equals(currentUser.roleName)) {
            JsonUtil.forbidden(resp); return;
        }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null || !j.has("newPassword")) {
            JsonUtil.badRequest(resp, "newPassword required"); return;
        }
        String newPass = j.get("newPassword").getAsString();
        if (!PasswordUtil.isStrong(newPass)) {
            JsonUtil.badRequest(resp, "Password must be ≥8 chars with uppercase + digit."); return;
        }

        boolean ok = userDAO.changePassword(targetId, newPass);
        if (ok) {
            academicDAO.log(currentUser.id, currentUser.username, currentUser.roleName,
                "UPDATE", "Users", "Password changed for user #" + targetId, getIp(req), null);
            JsonUtil.success(resp, "Password changed successfully.");
        } else { JsonUtil.serverError(resp, "Password change failed."); }
    }

    // ─── HELPERS ────────────────────────────────────────────
    private int parseId(HttpServletRequest req) {
        String pi = req.getPathInfo();
        return (pi != null && pi.length() > 1) ? ValidationUtil.parseInt(pi.substring(1), -1) : -1;
    }

    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}
