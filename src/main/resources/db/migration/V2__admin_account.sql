-- 서비스 관리자 초기 계정 (ADMIN-01). 관리자는 이 데이터로만 정한다.
-- 개발용 값이다: admin@blog.test / admin1234! (bcrypt). 운영에 올리기 전에 비밀번호를 바꾼다.
INSERT INTO member (email, password_hash, nickname, role, status)
VALUES ('admin@blog.test', '$2a$10$1XrAfK4aVBNuC7uNq35kiuugsN.I0RN6Ynqh4Bj6xqJy4B1SUTfB6', 'admin', 'ADMIN', 'ACTIVE');
