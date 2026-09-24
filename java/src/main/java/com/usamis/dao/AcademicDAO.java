package com.usamis.dao;

import com.usamis.model.Models;
import com.usamis.model.Models.*;
import com.usamis.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Covers Course, Enrollment, Grade, FeeRecord, AuditLog DAOs.
 * Grouped because they share the same DB and are always deployed together.
 */
public class AcademicDAO {

    private static final Logger log = LoggerFactory.getLogger(AcademicDAO.class);

    /* ═══════════════════════════════════════════
       COURSE
    ═══════════════════════════════════════════ */
    public List<Course> findAllCourses() {
        String sql =
            "SELECT c.*, d.name AS dept_name, " +
            "       u.first_name || ' ' || u.last_name AS instructor_name, " +
            "       COALESCE(ec.cnt, 0) AS enrolled_count " +
            "FROM courses c " +
            "JOIN departments d ON c.department_id = d.id " +
            "LEFT JOIN instructors i ON c.instructor_id = i.id " +
            "LEFT JOIN users u ON i.user_id = u.id " +
            "LEFT JOIN (SELECT course_id, COUNT(*) AS cnt FROM enrollments " +
            "           WHERE status = 'Active' GROUP BY course_id) ec " +
            "       ON ec.course_id = c.id " +
            "ORDER BY c.code";
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapCourse(rs));
        } catch (SQLException e) { log.error("findAllCourses", e); }
        return list;
    }

    public Optional<Course> findCourseByCode(String code) {
        String sql = "SELECT c.*, d.name AS dept_name, " +
            "       u.first_name || ' ' || u.last_name AS instructor_name, " +
            "       COALESCE(ec.cnt,0) AS enrolled_count " +
            "FROM courses c JOIN departments d ON c.department_id=d.id " +
            "LEFT JOIN instructors i ON c.instructor_id=i.id " +
            "LEFT JOIN users u ON i.user_id=u.id " +
            "LEFT JOIN (SELECT course_id, COUNT(*) cnt FROM enrollments WHERE status='Active' GROUP BY course_id) ec ON ec.course_id=c.id " +
            "WHERE c.code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapCourse(rs));
            }
        } catch (SQLException e) { log.error("findCourseByCode {}", code, e); }
        return Optional.empty();
    }

    public int createCourse(Course c) throws SQLException {
        String sql = "INSERT INTO courses (code, name, department_id, instructor_id, credits, " +
                     "max_enrollment, semester, status, description) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.code);
            ps.setString(2, c.name);
            ps.setInt(3, c.departmentId);
            if (c.instructorId != null) ps.setInt(4, c.instructorId); else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, c.credits);
            ps.setInt(6, c.maxEnrollment > 0 ? c.maxEnrollment : 50);
            ps.setString(7, c.semester);
            ps.setString(8, c.status != null ? c.status : "Active");
            ps.setString(9, c.description);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public boolean updateCourse(Course c) {
        String sql = "UPDATE courses SET name=?, department_id=?, credits=?, max_enrollment=?, " +
                     "semester=?, status=?, description=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.name); ps.setInt(2, c.departmentId); ps.setInt(3, c.credits);
            ps.setInt(4, c.maxEnrollment); ps.setString(5, c.semester);
            ps.setString(6, c.status); ps.setString(7, c.description); ps.setInt(8, c.id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { log.error("updateCourse {}", c.id, e); return false; }
    }

    public boolean deleteCourse(int id) {
        String sql = "UPDATE courses SET status='Cancelled' WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException e) { log.error("deleteCourse {}", id, e); return false; }
    }

    /* ═══════════════════════════════════════════
       ENROLLMENT
    ═══════════════════════════════════════════ */
    public List<Enrollment> findAllEnrollments() {
        String sql =
            "SELECT e.*, s.student_id AS student_no, " +
            "       s.first_name || ' ' || s.last_name AS student_name, " +
            "       c.code AS course_code, c.name AS course_name, " +
            "       g.score, g.letter_grade, g.gpa_points " +
            "FROM enrollments e " +
            "JOIN students s ON e.student_id = s.id " +
            "JOIN courses  c ON e.course_id  = c.id " +
            "LEFT JOIN grades g ON g.enrollment_id = e.id " +
            "ORDER BY e.enrolled_date DESC";
        List<Enrollment> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapEnrollment(rs));
        } catch (SQLException e) { log.error("findAllEnrollments", e); }
        return list;
    }

    public List<Enrollment> findEnrollmentsByStudent(int studentId) {
        String sql =
            "SELECT e.*, s.student_id AS student_no, " +
            "       s.first_name || ' ' || s.last_name AS student_name, " +
            "       c.code AS course_code, c.name AS course_name, " +
            "       g.score, g.letter_grade, g.gpa_points " +
            "FROM enrollments e " +
            "JOIN students s ON e.student_id = s.id " +
            "JOIN courses  c ON e.course_id  = c.id " +
            "LEFT JOIN grades g ON g.enrollment_id = e.id " +
            "WHERE e.student_id = ? ORDER BY e.semester";
        List<Enrollment> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapEnrollment(rs));
            }
        } catch (SQLException e) { log.error("findEnrollmentsByStudent", e); }
        return list;
    }

    /** Duplicate check before enrolling */
    public boolean isDuplicateEnrollment(int studentId, int courseId, String semester) {
        String sql = "SELECT 1 FROM enrollments WHERE student_id=? AND course_id=? AND semester=? AND status != 'Dropped'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId); ps.setInt(2, courseId); ps.setString(3, semester);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    /** Check course capacity */
    public boolean isCourseAtCapacity(int courseId) {
        String sql = "SELECT c.max_enrollment, COUNT(e.id) AS enrolled " +
                     "FROM courses c LEFT JOIN enrollments e ON e.course_id=c.id AND e.status='Active' " +
                     "WHERE c.id=? GROUP BY c.max_enrollment";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("enrolled") >= rs.getInt("max_enrollment");
            }
        } catch (SQLException e) { log.error("isCourseAtCapacity", e); }
        return false;
    }

    public int createEnrollment(int studentId, int courseId, String semester) throws SQLException {
        String sql = "INSERT INTO enrollments (student_id, course_id, semester) VALUES (?,?,?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId); ps.setInt(2, courseId); ps.setString(3, semester);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public boolean dropEnrollment(int enrollmentId) {
        String sql = "UPDATE enrollments SET status='Dropped' WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { log.error("dropEnrollment {}", enrollmentId, e); return false; }
    }

    /* ═══════════════════════════════════════════
       GRADES
    ═══════════════════════════════════════════ */
    public List<Grade> findAllGrades() {
        String sql =
            "SELECT g.*, e.semester, " +
            "       s.student_id AS st_no, s.first_name || ' ' || s.last_name AS student_name, " +
            "       c.code AS course_code, c.name AS course_name, " +
            "       u.first_name || ' ' || u.last_name AS entered_by_name " +
            "FROM grades g " +
            "JOIN enrollments e ON g.enrollment_id = e.id " +
            "JOIN students s    ON e.student_id = s.id " +
            "JOIN courses c     ON e.course_id  = c.id " +
            "LEFT JOIN users u  ON g.entered_by = u.id " +
            "ORDER BY g.entered_at DESC";
        List<Grade> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapGrade(rs));
        } catch (SQLException e) { log.error("findAllGrades", e); }
        return list;
    }

    public List<Grade> findGradesByStudent(int studentId) {
        String sql =
            "SELECT g.*, e.semester, " +
            "       s.student_id AS st_no, s.first_name || ' ' || s.last_name AS student_name, " +
            "       c.code AS course_code, c.name AS course_name, " +
            "       u.first_name || ' ' || u.last_name AS entered_by_name " +
            "FROM grades g " +
            "JOIN enrollments e ON g.enrollment_id = e.id " +
            "JOIN students s    ON e.student_id = s.id " +
            "JOIN courses c     ON e.course_id  = c.id " +
            "LEFT JOIN users u  ON g.entered_by = u.id " +
            "WHERE e.student_id = ? ORDER BY e.semester";
        List<Grade> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapGrade(rs));
            }
        } catch (SQLException e) { log.error("findGradesByStudent", e); }
        return list;
    }

    public boolean existsGradeForEnrollment(int enrollmentId) {
        String sql = "SELECT 1 FROM grades WHERE enrollment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public int createGrade(int enrollmentId, double score, int enteredBy) throws SQLException {
        GradeInfo info = Models.computeGrade(score);
        String sql = "INSERT INTO grades (enrollment_id, score, letter_grade, gpa_points, entered_by) " +
                     "VALUES (?,?,?,?,?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setDouble(2, score);
            ps.setString(3, info.letterGrade());
            ps.setDouble(4, info.gpaPoints());
            ps.setInt(5, enteredBy);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public boolean updateGrade(int gradeId, double score, int updatedBy) {
        GradeInfo info = Models.computeGrade(score);
        String sql = "UPDATE grades SET score=?, letter_grade=?, gpa_points=?, entered_by=?, updated_at=NOW() WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, score); ps.setString(2, info.letterGrade());
            ps.setDouble(3, info.gpaPoints()); ps.setInt(4, updatedBy); ps.setInt(5, gradeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { log.error("updateGrade {}", gradeId, e); return false; }
    }

    /* ═══════════════════════════════════════════
       FEE RECORDS
    ═══════════════════════════════════════════ */
    public List<FeeRecord> findAllFees() {
        String sql =
            "SELECT f.*, s.student_id AS st_no, s.first_name || ' ' || s.last_name AS student_name " +
            "FROM fee_records f " +
            "JOIN students s ON f.student_id = s.id " +
            "ORDER BY f.created_at DESC";
        List<FeeRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapFee(rs));
        } catch (SQLException e) { log.error("findAllFees", e); }
        return list;
    }

    public List<FeeRecord> findFeesByStudent(int studentId) {
        String sql =
            "SELECT f.*, s.student_id AS st_no, s.first_name || ' ' || s.last_name AS student_name " +
            "FROM fee_records f JOIN students s ON f.student_id = s.id " +
            "WHERE f.student_id = ? ORDER BY f.created_at DESC";
        List<FeeRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapFee(rs));
            }
        } catch (SQLException e) { log.error("findFeesByStudent", e); }
        return list;
    }

    public List<FeeRecord> findFeeDefaulters() {
        String sql =
            "SELECT f.*, s.student_id AS st_no, s.first_name || ' ' || s.last_name AS student_name " +
            "FROM fee_records f JOIN students s ON f.student_id = s.id " +
            "WHERE f.status IN ('Unpaid','Partial') ORDER BY f.total_amount - f.paid_amount DESC";
        List<FeeRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapFee(rs));
        } catch (SQLException e) { log.error("findFeeDefaulters", e); }
        return list;
    }

    public int createFeeRecord(FeeRecord f) throws SQLException {
        String sql = "INSERT INTO fee_records (receipt_no, student_id, fee_type, semester, " +
                     "total_amount, paid_amount, payment_method, payment_date, status, created_by) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, f.receiptNo);
            ps.setInt(2, f.studentId);
            ps.setString(3, f.feeType);
            ps.setString(4, f.semester);
            ps.setDouble(5, f.totalAmount);
            ps.setDouble(6, f.paidAmount);
            ps.setString(7, f.paymentMethod);
            if (f.paymentDate != null) ps.setDate(8, Date.valueOf(f.paymentDate)); else ps.setNull(8, Types.DATE);
            String status = f.paidAmount >= f.totalAmount ? "Paid" : f.paidAmount > 0 ? "Partial" : "Unpaid";
            ps.setString(9, status);
            if (f.createdBy != null) ps.setInt(10, f.createdBy); else ps.setNull(10, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public boolean recordPayment(int feeId, double payment, String method) {
        String sql = "UPDATE fee_records SET paid_amount = LEAST(total_amount, paid_amount + ?), " +
                     "payment_method=?, payment_date=CURRENT_DATE, " +
                     "status = CASE WHEN paid_amount + ? >= total_amount THEN 'Paid' ELSE 'Partial' END " +
                     "WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, payment); ps.setString(2, method);
            ps.setDouble(3, payment); ps.setInt(4, feeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { log.error("recordPayment {}", feeId, e); return false; }
    }

    /** Generate unique receipt number */
    public String generateReceiptNo() {
        String sql = "SELECT 'RCP' || TO_CHAR(NOW(),'YYYY') || LPAD(nextval('fee_receipt_seq')::TEXT, 4, '0')";
        // Simplified: use timestamp-based approach if sequence doesn't exist
        return "RCP" + System.currentTimeMillis();
    }

    /* ═══════════════════════════════════════════
       AUDIT LOG
    ═══════════════════════════════════════════ */
    public void log(int userId, String username, String roleName,
                    String action, String module, String description, String ip, String ua) {
        String sql = "INSERT INTO audit_log (user_id, username, role_name, action, module, description, ip_address, user_agent) " +
                     "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setString(2, username); ps.setString(3, roleName);
            ps.setString(4, action); ps.setString(5, module);
            ps.setString(6, description); ps.setString(7, ip); ps.setString(8, ua);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Audit log insert failed — action={} module={} user={}", action, module, username, e);
            // Never throw — audit failure must not disrupt the operation
        }
    }

    public List<AuditLog> findAuditLog(int limit, int offset) {
        String sql = "SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit); ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapAudit(rs));
            }
        } catch (SQLException e) { log.error("findAuditLog", e); }
        return list;
    }

    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Students
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM students WHERE status='Active'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.totalStudents = rs.getInt(1);
            }
            // Active enrollments
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE status='Active'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.activeEnrollments = rs.getInt(1);
            }
            // Average GPA
            try (PreparedStatement ps = conn.prepareStatement("SELECT ROUND(AVG(gpa_points)::NUMERIC,2) FROM grades");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { double v = rs.getDouble(1); stats.averageGpa = rs.wasNull() ? 0 : v; }
            }
            // At risk
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT e.student_id) FROM enrollments e " +
                    "JOIN grades g ON g.enrollment_id=e.id GROUP BY e.student_id HAVING AVG(g.gpa_points) < 2.5");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) stats.studentsAtRisk++;
            }
            // Fee stats
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(total_amount), SUM(paid_amount) FROM fee_records");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.totalFeesBilled   = rs.getDouble(1);
                    stats.totalFeesPaid     = rs.getDouble(2);
                    stats.totalFeesOutstanding = stats.totalFeesBilled - stats.totalFeesPaid;
                    stats.feeCollectionRate = stats.totalFeesBilled > 0
                        ? Math.round((stats.totalFeesPaid / stats.totalFeesBilled) * 100.0) : 0;
                }
            }
            // Courses and users
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM courses WHERE status != 'Cancelled'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.totalCourses = rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE status='active'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.totalUsers = rs.getInt(1);
            }
        } catch (SQLException e) { log.error("getDashboardStats", e); }
        return stats;
    }

    /* ═══════════════════════════════════════════
       MAPPERS
    ═══════════════════════════════════════════ */
    private Course mapCourse(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.id             = rs.getInt("id");
        c.code           = rs.getString("code");
        c.name           = rs.getString("name");
        c.departmentId   = rs.getInt("department_id");
        c.departmentName = rs.getString("dept_name");
        int iid = rs.getInt("instructor_id"); c.instructorId = rs.wasNull() ? null : iid;
        c.instructorName = rs.getString("instructor_name");
        c.credits        = rs.getInt("credits");
        c.maxEnrollment  = rs.getInt("max_enrollment");
        c.enrolledCount  = rs.getInt("enrolled_count");
        c.semester       = rs.getString("semester");
        c.status         = rs.getString("status");
        c.description    = rs.getString("description");
        Timestamp ca = rs.getTimestamp("created_at");
        c.createdAt = ca != null ? ca.toLocalDateTime() : null;
        return c;
    }

    private Enrollment mapEnrollment(ResultSet rs) throws SQLException {
        Enrollment e = new Enrollment();
        e.id          = rs.getInt("id");
        e.studentId   = rs.getInt("student_id");
        e.studentNo   = rs.getString("student_no");
        e.studentName = rs.getString("student_name");
        e.courseId    = rs.getInt("course_id");
        e.courseCode  = rs.getString("course_code");
        e.courseName  = rs.getString("course_name");
        e.semester    = rs.getString("semester");
        Date ed = rs.getDate("enrolled_date");
        e.enrolledDate = ed != null ? ed.toLocalDate() : null;
        e.status      = rs.getString("status");
        double score = rs.getDouble("score"); e.score = rs.wasNull() ? null : score;
        e.letterGrade = rs.getString("letter_grade");
        double gpa = rs.getDouble("gpa_points"); e.gpaPoints = rs.wasNull() ? null : gpa;
        return e;
    }

    private Grade mapGrade(ResultSet rs) throws SQLException {
        Grade g = new Grade();
        g.id           = rs.getInt("id");
        g.enrollmentId = rs.getInt("enrollment_id");
        g.studentId    = rs.getString("st_no");
        g.studentName  = rs.getString("student_name");
        g.courseCode   = rs.getString("course_code");
        g.courseName   = rs.getString("course_name");
        double sc = rs.getDouble("score"); g.score = rs.wasNull() ? null : sc;
        g.letterGrade  = rs.getString("letter_grade");
        double gpa = rs.getDouble("gpa_points"); g.gpaPoints = rs.wasNull() ? null : gpa;
        g.remarks      = rs.getString("remarks");
        g.enteredBy    = rs.getInt("entered_by");
        g.enteredByName= rs.getString("entered_by_name");
        Timestamp ea = rs.getTimestamp("entered_at"); g.enteredAt = ea != null ? ea.toLocalDateTime() : null;
        Timestamp ua = rs.getTimestamp("updated_at"); g.updatedAt = ua != null ? ua.toLocalDateTime() : null;
        g.semester     = rs.getString("semester");
        return g;
    }

    private FeeRecord mapFee(ResultSet rs) throws SQLException {
        FeeRecord f = new FeeRecord();
        f.id            = rs.getInt("id");
        f.receiptNo     = rs.getString("receipt_no");
        f.studentId     = rs.getInt("student_id");
        f.studentNo     = rs.getString("st_no");
        f.studentName   = rs.getString("student_name");
        f.feeType       = rs.getString("fee_type");
        f.semester      = rs.getString("semester");
        f.totalAmount   = rs.getDouble("total_amount");
        f.paidAmount    = rs.getDouble("paid_amount");
        f.balance       = f.totalAmount - f.paidAmount;
        f.paymentMethod = rs.getString("payment_method");
        Date pd = rs.getDate("payment_date"); f.paymentDate = pd != null ? pd.toLocalDate() : null;
        f.status        = rs.getString("status");
        int cb = rs.getInt("created_by"); f.createdBy = rs.wasNull() ? null : cb;
        Timestamp ca = rs.getTimestamp("created_at"); f.createdAt = ca != null ? ca.toLocalDateTime() : null;
        Timestamp ua = rs.getTimestamp("updated_at"); f.updatedAt = ua != null ? ua.toLocalDateTime() : null;
        return f;
    }

    private AuditLog mapAudit(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.id          = rs.getLong("id");
        int uid = rs.getInt("user_id"); a.userId = rs.wasNull() ? null : uid;
        a.username    = rs.getString("username");
        a.roleName    = rs.getString("role_name");
        a.action      = rs.getString("action");
        a.module      = rs.getString("module");
        a.description = rs.getString("description");
        a.ipAddress   = rs.getString("ip_address");
        a.userAgent   = rs.getString("user_agent");
        Timestamp ca = rs.getTimestamp("created_at"); a.createdAt = ca != null ? ca.toLocalDateTime() : null;
        return a;
    }
}
