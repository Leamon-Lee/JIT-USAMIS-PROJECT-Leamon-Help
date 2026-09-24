package com.usamis.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * All domain models in one file for clarity.
 * In a larger codebase, split into separate files.
 * These are plain Java records — immutable, no boilerplate.
 */
public final class Models {
    private Models() {}

    // ─── ROLE ───────────────────────────────────
    public record Role(int id, String name, String description) {}

    // ─── USER ───────────────────────────────────
    public static class User {
        public int id;
        public String username;
        public String passwordHash;   // NEVER serialise this to JSON responses
        public String firstName;
        public String lastName;
        public String email;
        public int roleId;
        public String roleName;
        public String status;
        public LocalDateTime createdAt;
        public LocalDateTime lastLogin;

        public String fullName() { return firstName + " " + lastName; }

        // Safe DTO — excludes password hash for API responses
        public UserDTO toDTO() {
            UserDTO dto = new UserDTO();
            dto.id        = this.id;
            dto.username  = this.username;
            dto.firstName = this.firstName;
            dto.lastName  = this.lastName;
            dto.email     = this.email;
            dto.roleId    = this.roleId;
            dto.roleName  = this.roleName;
            dto.status    = this.status;
            dto.createdAt = this.createdAt;
            dto.lastLogin = this.lastLogin;
            return dto;
        }
    }

    /** Safe transfer object — no password hash */
    public static class UserDTO {
        public int id;
        public String username, firstName, lastName, email, roleName, status;
        public int roleId;
        public LocalDateTime createdAt, lastLogin;
        public String fullName() { return firstName + " " + lastName; }
    }

    // ─── STUDENT ────────────────────────────────
    public static class Student {
        public int    id;
        public String studentId;     // STU2024001
        public Integer userId;        // optional link to users table
        public String firstName;
        public String lastName;
        public LocalDate dateOfBirth;
        public String gender;
        public String email;
        public String phone;
        public int    departmentId;
        public String departmentName;
        public int    programId;
        public String programName;
        public int    yearOfStudy;
        public LocalDate enrollmentDate;
        public String status;
        public String nationality;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
        // Computed
        public Double gpa;

        public String fullName() { return firstName + " " + lastName; }
    }

    // ─── COURSE ─────────────────────────────────
    public static class Course {
        public int    id;
        public String code;
        public String name;
        public int    departmentId;
        public String departmentName;
        public Integer instructorId;
        public String instructorName;
        public int    credits;
        public int    maxEnrollment;
        public int    enrolledCount;  // from view
        public String semester;
        public String status;
        public String description;
        public LocalDateTime createdAt;
    }

    // ─── ENROLLMENT ─────────────────────────────
    public static class Enrollment {
        public int      id;
        public int      studentId;
        public String   studentNo;
        public String   studentName;
        public int      courseId;
        public String   courseCode;
        public String   courseName;
        public String   semester;
        public LocalDate enrolledDate;
        public String   status;
        // Joined grade data
        public Double  score;
        public String  letterGrade;
        public Double  gpaPoints;
    }

    // ─── GRADE ──────────────────────────────────
    public static class Grade {
        public int    id;
        public int    enrollmentId;
        public String studentId;
        public String studentName;
        public String courseCode;
        public String courseName;
        public Double score;
        public String letterGrade;
        public Double gpaPoints;
        public String remarks;
        public int    enteredBy;
        public String enteredByName;
        public LocalDateTime enteredAt;
        public LocalDateTime updatedAt;
        public String semester;
    }

    // ─── FEE RECORD ─────────────────────────────
    public static class FeeRecord {
        public int    id;
        public String receiptNo;
        public int    studentId;
        public String studentNo;
        public String studentName;
        public String feeType;
        public String semester;
        public double totalAmount;
        public double paidAmount;
        public double balance;        // computed
        public String paymentMethod;
        public LocalDate paymentDate;
        public String status;
        public Integer createdBy;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    // ─── AUDIT LOG ──────────────────────────────
    public static class AuditLog {
        public long   id;
        public Integer userId;
        public String username;
        public String roleName;
        public String action;
        public String module;
        public String description;
        public String ipAddress;
        public String userAgent;
        public LocalDateTime createdAt;
    }

    // ─── DASHBOARD STATS DTO ────────────────────
    public static class DashboardStats {
        public int    totalStudents;
        public int    activeEnrollments;
        public double averageGpa;
        public int    studentsAtRisk;
        public double feeCollectionRate;
        public double totalFeesBilled;
        public double totalFeesPaid;
        public double totalFeesOutstanding;
        public int    totalCourses;
        public int    totalUsers;
    }

    // ─── GRADE COMPUTATION HELPER ────────────────
    public static GradeInfo computeGrade(double score) {
        String letter; double points;
        if      (score >= 95) { letter = "A+"; points = 4.0; }
        else if (score >= 90) { letter = "A";  points = 4.0; }
        else if (score >= 85) { letter = "B+"; points = 3.5; }
        else if (score >= 80) { letter = "B";  points = 3.0; }
        else if (score >= 75) { letter = "B-"; points = 2.7; }
        else if (score >= 70) { letter = "C+"; points = 2.3; }
        else if (score >= 65) { letter = "C";  points = 2.0; }
        else if (score >= 60) { letter = "C-"; points = 1.7; }
        else if (score >= 55) { letter = "D";  points = 1.0; }
        else                  { letter = "F";  points = 0.0; }
        return new GradeInfo(letter, points);
    }

    public record GradeInfo(String letterGrade, double gpaPoints) {}
}
