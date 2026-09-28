-- The category codes are fixed by the application enums; only their display text
-- is maintainable, so the rows are inserted here and never created at runtime.
INSERT INTO course_category (code, display_name, description, version) VALUES
    ('INTERNAL', 'Internal Training', 'In-house courses delivered by NUS trainers. Attendance is recorded but no fee is charged.', 0),
    ('EXTERNAL', 'External Training', 'Courses run by an approved external training provider. Fees are payable.', 0),
    ('CERTIFICATION', 'Certification', 'Certification or examination programmes, including the certification fee.', 0)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);
