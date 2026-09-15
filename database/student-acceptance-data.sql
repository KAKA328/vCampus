-- Run once after schema.sql and seed.sql in a NEW acceptance database.
-- All archives are synthetic and initially unbound; all students match plan-cs-2026.
INSERT INTO tblClass(class_id,class_name,department_name,major_name,grade_year)
VALUES ('QA-CS2026','学籍验收2026班','计算机科学与工程学院','计算机科学与技术',2026);

INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_EMPTY',NULL,'验收空成绩','未知','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','','');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_EXACT',NULL,'验收恰好达标','男','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000101','exact@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_SHORT',NULL,'验收学分不足','女','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000102','short@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_PENDING',NULL,'验收学分够但待重修','男','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000103','pending@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_RETAKE',NULL,'验收重修通过','女','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000104','retake@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_DUP',NULL,'验收重复通过去重','未知','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000105','duplicate@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_LEAVE',NULL,'验收休学','女','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'休学','13900000106','leave@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_WITHDRAWN',NULL,'验收退学','男','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'退学','13900000107','withdrawn@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_EDIT',NULL,'验收并发编辑','未知','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000108','edit@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_GRAD',NULL,'验收并发毕业','男','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000109','graduate@example.test');
INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,class_id,enrollment_year,status,phone,email)
VALUES ('QA_BIND',NULL,'验收竞争绑定','女','计算机科学与工程学院','计算机科学与技术','QA-CS2026',2026,'在读','13900000110','binding@example.test');

INSERT INTO tblTeacher(teacher_id,user_id,teacher_name,department_name,title,active)
VALUES ('QA_T_ACTIVE',NULL,'验收在职教师','计算机科学与工程学院','讲师',1);
INSERT INTO tblTeacher(teacher_id,user_id,teacher_name,department_name,title,active)
VALUES ('QA_T_INACTIVE',NULL,'验收非在职教师','计算机科学与工程学院','副教授',0);

INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-exact-java','QA_EXACT','JAVA101',NULL,'2026-2027-1',1,'首修',60,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-exact-ds','QA_EXACT','DS101',NULL,'2026-2027-1',1,'首修',100,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-exact-net','QA_EXACT','NET101',NULL,'2026-2027-1',1,'首修',85,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-exact-ai','QA_EXACT','AI101',NULL,'2026-2027-1',1,'首修',88,1,2,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-short-java','QA_SHORT','JAVA101',NULL,'2026-2027-1',1,'首修',60,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-pending-java','QA_PENDING','JAVA101',NULL,'2026-2027-1',1,'首修',80,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-pending-db','QA_PENDING','DB101',NULL,'2026-2027-1',1,'首修',80,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-pending-net','QA_PENDING','NET101',NULL,'2026-2027-1',1,'首修',85,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-pending-ai','QA_PENDING','AI101',NULL,'2026-2027-1',1,'首修',88,1,2,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-pending-zero','QA_PENDING','OS101',NULL,'2026-2027-1',1,'首修',0,0,0,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-retake-fail','QA_RETAKE','JAVA101',NULL,'2026-2027-1',1,'首修',59,0,0,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-retake-pass','QA_RETAKE','JAVA101',NULL,'2026-2027-1',2,'重修',60,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-dup-one','QA_DUP','JAVA101',NULL,'2026-2027-1',1,'首修',70,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-dup-two','QA_DUP','JAVA101',NULL,'2026-2027-1',2,'重修',90,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-grad-java','QA_GRAD','JAVA101',NULL,'2026-2027-1',1,'首修',80,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-grad-ds','QA_GRAD','DS101',NULL,'2026-2027-1',1,'首修',90,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-grad-net','QA_GRAD','NET101',NULL,'2026-2027-1',1,'首修',85,1,3,NOW());
INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,attempt_no,attempt_type,score,passed,earned_credits,recorded_at)
VALUES ('qa-grad-ai','QA_GRAD','AI101',NULL,'2026-2027-1',1,'首修',88,1,2,NOW());

-- QA_GRAD starts enrolled: create its review and graduate through the actual API/UI.
-- QA_BIND is intentionally absent from the normal account CSV, for simultaneous binding attempts.
