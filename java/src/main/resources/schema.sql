-- ═══════════════════════════════════════════════
--  USAMIS — Database Schema
--  PostgreSQL · Jinling Institute of Technology
-- ═══════════════════════════════════════════════

-- Drop existing (safe re-run)
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS attendance CASCADE;
DROP TABLE IF EXISTS grades CASCADE;
DROP TABLE IF EXISTS enrollments CASCADE;
DROP TABLE IF EXISTS fee_records CASCADE;
DROP TABLE IF EXISTS courses CASCADE;
DROP TABLE IF EXISTS students CASCADE;
DROP TABLE IF EXISTS instructors CASCADE;
DROP TABLE IF EXISTS programs CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;

-- ─── ROLES ───────────────────────────────────
CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,    -- admin, registrar, lecturer, finance, student
    description VARCHAR(255)
);

INSERT INTO roles (name, description) VALUES
  ('admin',     'Full system control'),
  ('registrar', 'Manages students, courses, enrollment'),
  ('lecturer',  'Enters grades for own courses'),
  ('finance',   'Manages fees and payments'),
  ('student',   'Views own profile, results, fees');

-- ─── PERMISSIONS ─────────────────────────────
CREATE TABLE permissions (
    id     SERIAL PRIMARY KEY,
    name   VARCHAR(100) NOT NULL UNIQUE,  -- e.g. MANAGE_USERS, ENTER_GRADES
    module VARCHAR(50)  NOT NULL
);

INSERT INTO permissions (name, module) VALUES
  ('MANAGE_USERS',    'users'),
  ('MANAGE_STUDENTS', 'students'),
  ('MANAGE_COURSES',  'courses'),
  ('MANAGE_ENROLLMENT','enrollment'),
  ('ENTER_GRADES',    'grades'),
  ('VIEW_GRADES',     'grades'),
  ('MANAGE_FEES',     'fees'),
  ('VIEW_FEES',       'fees'),
  ('VIEW_REPORTS',    'reports'),
  ('VIEW_AUDIT_LOG',  'audit'),
  ('VIEW_OWN_PROFILE','profile');

-- ─── ROLE-PERMISSION MAPPING ─────────────────
CREATE TABLE role_permissions (
    role_id       INT REFERENCES roles(id),
    permission_id INT REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- Admin → all
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'admin';

-- Registrar
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'registrar'
    AND p.name IN ('MANAGE_STUDENTS','MANAGE_COURSES','MANAGE_ENROLLMENT','VIEW_GRADES','VIEW_REPORTS','VIEW_OWN_PROFILE');

-- Lecturer
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'lecturer'
    AND p.name IN ('ENTER_GRADES','VIEW_GRADES','VIEW_OWN_PROFILE');

-- Finance
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'finance'
    AND p.name IN ('MANAGE_FEES','VIEW_FEES','VIEW_REPORTS','VIEW_OWN_PROFILE');

-- Student
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'student'
    AND p.name IN ('VIEW_GRADES','VIEW_FEES','VIEW_OWN_PROFILE');

-- ─── USERS ───────────────────────────────────
CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,           -- bcrypt
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    role_id       INT          NOT NULL REFERENCES roles(id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'active'  -- active | inactive | blocked
                  CHECK (status IN ('active','inactive','blocked')),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login    TIMESTAMP,
    CONSTRAINT users_username_len CHECK (LENGTH(username) >= 3)
);

CREATE INDEX idx_users_role ON users(role_id);
CREATE INDEX idx_users_status ON users(status);

-- ─── DEPARTMENTS ─────────────────────────────
CREATE TABLE departments (
    id   SERIAL PRIMARY KEY,
    code VARCHAR(10)  NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL
);

INSERT INTO departments (code, name) VALUES
  ('CE',  'Computer Engineering'),
  ('SE',  'Software Engineering'),
  ('ISC', 'Intelligent Science and Control Engineering'),
  ('EE',  'Electronic and Information Engineering'),
  ('NS',  'Network Security'),
  ('NCE', 'Network and Communication Engineering'),
  ('BS',  'Business School'),
  ('ME',  'Mechanical and Electrical Engineering');

-- ─── PROGRAMS ────────────────────────────────
CREATE TABLE programs (
    id            SERIAL PRIMARY KEY,
    department_id INT  NOT NULL REFERENCES departments(id),
    name          VARCHAR(200) NOT NULL,
    duration_years INT NOT NULL DEFAULT 4
);

INSERT INTO programs (department_id, name) VALUES
  (1, 'B.Sc. Computer Science'),
  (1, 'B.Sc. Computer Engineering'),
  (2, 'B.Sc. Software Engineering'),
  (3, 'B.Sc. Artificial Intelligence'),
  (3, 'B.Sc. Automation'),
  (4, 'B.Sc. Electronic Information Engineering'),
  (5, 'B.Sc. Information Security'),
  (5, 'B.Sc. Cyberspace Security'),
  (7, 'B.Sc. Finance'),
  (7, 'B.Sc. Accounting');

-- ─── STUDENTS ────────────────────────────────
CREATE TABLE students (
    id            SERIAL       PRIMARY KEY,
    student_id    VARCHAR(20)  NOT NULL UNIQUE,   -- e.g. STU2024001
    user_id       INT          REFERENCES users(id),  -- nullable: student may not have login yet
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender        VARCHAR(20)  CHECK (gender IN ('Male','Female','Other')),
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(30),
    department_id INT          NOT NULL REFERENCES departments(id),
    program_id    INT          NOT NULL REFERENCES programs(id),
    year_of_study INT          NOT NULL CHECK (year_of_study BETWEEN 1 AND 6),
    enrollment_date DATE       NOT NULL DEFAULT CURRENT_DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'Active'
                  CHECK (status IN ('Active','Inactive','Graduated','Suspended')),
    nationality   VARCHAR(100) DEFAULT 'Chinese',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_students_dept   ON students(department_id);
CREATE INDEX idx_students_status ON students(status);
CREATE INDEX idx_students_sid    ON students(student_id);

-- ─── INSTRUCTORS ─────────────────────────────
CREATE TABLE instructors (
    id            SERIAL PRIMARY KEY,
    user_id       INT NOT NULL REFERENCES users(id),
    department_id INT NOT NULL REFERENCES departments(id),
    title         VARCHAR(50) DEFAULT 'Prof.',
    specialization VARCHAR(200)
);

-- ─── COURSES ─────────────────────────────────
CREATE TABLE courses (
    id             SERIAL PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,  -- e.g. CS301
    name           VARCHAR(200) NOT NULL,
    department_id  INT          NOT NULL REFERENCES departments(id),
    instructor_id  INT          REFERENCES instructors(id),
    credits        INT          NOT NULL CHECK (credits BETWEEN 1 AND 8),
    max_enrollment INT          NOT NULL DEFAULT 50,
    semester       VARCHAR(50)  NOT NULL,          -- e.g. "Semester 1 2024/25"
    status         VARCHAR(20)  NOT NULL DEFAULT 'Active'
                   CHECK (status IN ('Active','Closed','Full','Cancelled')),
    description    TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_courses_dept ON courses(department_id);
CREATE INDEX idx_courses_code ON courses(code);

-- ─── ENROLLMENTS ─────────────────────────────
CREATE TABLE enrollments (
    id              SERIAL PRIMARY KEY,
    student_id      INT NOT NULL REFERENCES students(id),
    course_id       INT NOT NULL REFERENCES courses(id),
    semester        VARCHAR(50) NOT NULL,
    enrolled_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'Active'
                    CHECK (status IN ('Active','Dropped','Completed','Failed')),
    UNIQUE (student_id, course_id, semester)      -- prevent duplicate enrollment
);

CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course  ON enrollments(course_id);

-- ─── GRADES ──────────────────────────────────
CREATE TABLE grades (
    id            SERIAL PRIMARY KEY,
    enrollment_id INT            NOT NULL UNIQUE REFERENCES enrollments(id),
    score         DECIMAL(5,2)   CHECK (score BETWEEN 0 AND 100),
    letter_grade  VARCHAR(5),    -- A+, A, B+, B, C, D, F
    gpa_points    DECIMAL(3,1),  -- 4.0, 3.5, ...
    remarks       VARCHAR(255),
    entered_by    INT            REFERENCES users(id),
    entered_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_grades_enrollment ON grades(enrollment_id);

-- ─── FEE RECORDS ─────────────────────────────
CREATE TABLE fee_records (
    id             SERIAL PRIMARY KEY,
    receipt_no     VARCHAR(30)   NOT NULL UNIQUE,
    student_id     INT           NOT NULL REFERENCES students(id),
    fee_type       VARCHAR(100)  NOT NULL,   -- Tuition, Accommodation, Lab, Library, Registration
    semester       VARCHAR(50)   NOT NULL,
    total_amount   DECIMAL(12,2) NOT NULL CHECK (total_amount >= 0),
    paid_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    payment_method VARCHAR(50),              -- WeChat Pay, Alipay, Bank Transfer, Cash
    payment_date   DATE,
    status         VARCHAR(20)   NOT NULL DEFAULT 'Unpaid'
                   CHECK (status IN ('Paid','Partial','Unpaid')),
    created_by     INT           REFERENCES users(id),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fee_paid_lte_total CHECK (paid_amount <= total_amount)
);

CREATE INDEX idx_fees_student ON fee_records(student_id);
CREATE INDEX idx_fees_status  ON fee_records(status);

-- ─── ATTENDANCE ───────────────────────────────
CREATE TABLE attendance (
    id            SERIAL PRIMARY KEY,
    enrollment_id INT  NOT NULL REFERENCES enrollments(id),
    class_date    DATE NOT NULL,
    present       BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (enrollment_id, class_date)
);

-- ─── AUDIT LOG ───────────────────────────────
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     INT         REFERENCES users(id),
    username    VARCHAR(50),
    role_name   VARCHAR(50),
    action      VARCHAR(30) NOT NULL,    -- LOGIN, LOGOUT, CREATE, UPDATE, DELETE, ACCESS_DENIED
    module      VARCHAR(50) NOT NULL,
    description TEXT,
    ip_address  VARCHAR(50),
    user_agent  VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user   ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_time   ON audit_log(created_at DESC);

-- ─── TRIGGER: updated_at ─────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_students_updated  BEFORE UPDATE ON students   FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_fees_updated      BEFORE UPDATE ON fee_records FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_grades_updated    BEFORE UPDATE ON grades      FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ─── VIEWS ───────────────────────────────────
CREATE VIEW v_student_gpa AS
SELECT
  s.id, s.student_id, s.first_name, s.last_name,
  d.name AS department,
  ROUND(AVG(g.gpa_points)::NUMERIC, 2) AS gpa,
  COUNT(g.id) AS graded_courses
FROM students s
JOIN departments d ON s.department_id = d.id
LEFT JOIN enrollments e ON e.student_id = s.id
LEFT JOIN grades g ON g.enrollment_id = e.id
GROUP BY s.id, s.student_id, s.first_name, s.last_name, d.name;

CREATE VIEW v_fee_summary AS
SELECT
  s.student_id, s.first_name || ' ' || s.last_name AS student_name,
  SUM(f.total_amount) AS total_billed,
  SUM(f.paid_amount)  AS total_paid,
  SUM(f.total_amount - f.paid_amount) AS outstanding
FROM fee_records f
JOIN students s ON f.student_id = s.id
GROUP BY s.student_id, student_name;

CREATE VIEW v_course_enrollment AS
SELECT
  c.code, c.name, c.max_enrollment,
  COUNT(e.id) AS enrolled_count,
  c.max_enrollment - COUNT(e.id) AS seats_available
FROM courses c
LEFT JOIN enrollments e ON e.course_id = c.id AND e.status = 'Active'
GROUP BY c.id, c.code, c.name, c.max_enrollment;
