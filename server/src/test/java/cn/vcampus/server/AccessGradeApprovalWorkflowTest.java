package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeSubmissionAuditAction;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.student.FormalCourseResult;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证 Access 审核通过时，正式成绩与成绩单状态在同一事务内提交。 */
class AccessGradeApprovalWorkflowTest {
    @TempDir
    Path temporaryDirectory;

    private Path database;
    private AccessGradeApprovalWorkflow workflow;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("grade-approval.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblGradeSubmission ("
                    + "submission_id VARCHAR(36) NOT NULL,offering_id VARCHAR(36) NOT NULL,"
                    + "teacher_id VARCHAR(32) NOT NULL,status VARCHAR(20) NOT NULL,"
                    + "created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,"
                    + "reviewed_by VARCHAR(32),reviewed_at DATETIME,review_remark VARCHAR(255),"
                    + "PRIMARY KEY (submission_id))");
            statement.execute("CREATE TABLE tblCourseResult ("
                    + "result_id VARCHAR(36) NOT NULL,student_id VARCHAR(32) NOT NULL,"
                    + "course_id VARCHAR(32) NOT NULL,offering_id VARCHAR(36),semester VARCHAR(32) NOT NULL,"
                    + "attempt_no INTEGER NOT NULL,attempt_type VARCHAR(16) NOT NULL,score INTEGER,"
                    + "passed BIT NOT NULL,earned_credits INTEGER NOT NULL,recorded_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (result_id))");
            statement.execute("CREATE TABLE tblGradeSubmissionResult ("
                    + "submission_id VARCHAR(36) NOT NULL,result_id VARCHAR(36) NOT NULL,"
                    + "PRIMARY KEY (submission_id,result_id),"
                    + "CONSTRAINT uk_tblGradeSubmissionResult_result UNIQUE (result_id))");
            statement.execute("CREATE TABLE tblGradeEntry (submission_id VARCHAR(36) NOT NULL,"
                    + "student_id VARCHAR(32) NOT NULL,selection_type VARCHAR(16) NOT NULL,"
                    + "score INTEGER NOT NULL,updated_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (submission_id,student_id))");
            statement.execute("CREATE TABLE tblGradeSubmissionSnapshot (submission_id VARCHAR(36) NOT NULL,"
                    + "version_no INTEGER NOT NULL,submitted_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (submission_id,version_no))");
            statement.execute("CREATE TABLE tblGradeSubmissionSnapshotEntry (submission_id VARCHAR(36) NOT NULL,"
                    + "version_no INTEGER NOT NULL,student_id VARCHAR(32) NOT NULL,"
                    + "selection_type VARCHAR(16) NOT NULL,score INTEGER NOT NULL,"
                    + "PRIMARY KEY (submission_id,version_no,student_id))");
            statement.execute("CREATE TABLE tblGradeSubmissionAudit ("
                    + "audit_id VARCHAR(36) NOT NULL,submission_id VARCHAR(36) NOT NULL,"
                    + "action VARCHAR(16) NOT NULL,actor_id VARCHAR(32) NOT NULL,"
                    + "occurred_at DATETIME NOT NULL,remark VARCHAR(255),PRIMARY KEY (audit_id))");
        }
        workflow = new AccessGradeApprovalWorkflow(database);
    }

    @Test
    void commitsFormalResultAndApprovedStatusTogether() throws Exception {
        insertPending("GRADE-001");

        assertEquals(StatusCode.OK, workflow.approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-001")), "academic_001", "审核通过")
                .getStatus());
        assertEquals(GradeSubmissionStatus.APPROVED.name(), submissionStatus("GRADE-001"));
        assertEquals(1, formalResultCount());
        assertEquals(GradeSubmissionAuditAction.APPROVED,
                new AccessGradeSubmissionService(database).listAudit("GRADE-001")
                        .getData().get(0).getAction());
    }

    @Test
    void rollsBackStatusWhenFormalResultCannotBeInserted() throws Exception {
        insertPending("GRADE-001");
        insertFormalResult("RESULT-001");

        assertEquals(StatusCode.CONFLICT, workflow.approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-001")), "academic_001", "审核通过")
                .getStatus());
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW.name(), submissionStatus("GRADE-001"));
        assertEquals(1, formalResultCount());
    }

    @Test
    void rejectsSecondApprovalFromAnotherServiceInstance() throws Exception {
        insertPending("GRADE-001");

        assertEquals(StatusCode.OK, workflow.approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-001")), "academic_001", "审核通过")
                .getStatus());

        AccessGradeApprovalWorkflow anotherWorkflow = new AccessGradeApprovalWorkflow(database);
        assertEquals(StatusCode.CONFLICT, anotherWorkflow.approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-002")), "academic_002", "重复审核")
                .getStatus());
        assertEquals(GradeSubmissionStatus.APPROVED.name(), submissionStatus("GRADE-001"));
        assertEquals(1, formalResultCount());
    }

    @Test
    void allowsOnlyOneConcurrentApprovalAcrossServiceInstances() throws Exception {
        insertPending("GRADE-001");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<StatusCode> first = workers.submit(() -> approveAtTheSameTime(ready, start,
                    "academic_001"));
            Future<StatusCode> second = workers.submit(() -> approveAtTheSameTime(ready, start,
                    "academic_002"));
            ready.await();
            start.countDown();

            StatusCode firstStatus = first.get();
            StatusCode secondStatus = second.get();
            assertTrue((firstStatus == StatusCode.OK && secondStatus == StatusCode.CONFLICT)
                    || (firstStatus == StatusCode.CONFLICT && secondStatus == StatusCode.OK));
            assertEquals(GradeSubmissionStatus.APPROVED.name(), submissionStatus("GRADE-001"));
            assertEquals(1, formalResultCount());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void returnsApprovedSubmissionByRetractingOnlyItsPublishedFormalResults() throws Exception {
        insertPending("GRADE-001");
        assertEquals(StatusCode.OK, workflow.approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-001")), "academic_001", "审核通过")
                .getStatus());

        assertEquals(StatusCode.OK, workflow.returnForRevision("GRADE-001", 1, "academic_002",
                "发现成绩录入错误，请修改后重新提交").getStatus());
        assertEquals(GradeSubmissionStatus.RETURNED.name(), submissionStatus("GRADE-001"));
        assertEquals(0, formalResultCount());
        assertEquals(0, publicationLinkCount());
        assertEquals(2, new AccessGradeSubmissionService(database).listAudit("GRADE-001")
                .getData().size());

        AccessGradeSubmissionService submissions = new AccessGradeSubmissionService(database);
        assertEquals(StatusCode.OK, submissions.submitForReview("GRADE-001").getStatus());
        assertEquals(StatusCode.OK, workflow.approve("GRADE-001", 2,
                Collections.singletonList(result("RESULT-002")), "academic_003", "复核通过")
                .getStatus());
        assertEquals(GradeSubmissionStatus.APPROVED.name(), submissionStatus("GRADE-001"));
        assertEquals(1, formalResultCount());
        assertEquals(1, publicationLinkCount());
    }

    private StatusCode approveAtTheSameTime(CountDownLatch ready, CountDownLatch start,
            String reviewerId) throws Exception {
        ready.countDown();
        start.await();
        return new AccessGradeApprovalWorkflow(database).approve("GRADE-001", 1,
                Collections.singletonList(result("RESULT-001")), reviewerId, "审核通过")
                .getStatus();
    }

    private void insertPending(String submissionId) throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 15, 0);
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblGradeSubmission(submission_id,offering_id,teacher_id,status,"
                        + "created_at,updated_at) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, submissionId);
            statement.setString(2, "OFFER-001");
            statement.setString(3, "T001");
            statement.setString(4, GradeSubmissionStatus.PENDING_REVIEW.name());
            statement.setTimestamp(5, java.sql.Timestamp.valueOf(now));
            statement.setTimestamp(6, java.sql.Timestamp.valueOf(now));
            statement.executeUpdate();
        }
        try (Connection connection = open(); PreparedStatement entry = connection.prepareStatement(
                "INSERT INTO tblGradeEntry(submission_id,student_id,selection_type,score,updated_at) VALUES(?,?,?,?,?)");
                PreparedStatement snapshot = connection.prepareStatement(
                "INSERT INTO tblGradeSubmissionSnapshot(submission_id,version_no,submitted_at) VALUES(?,?,?)");
                PreparedStatement snapshotEntry = connection.prepareStatement(
                "INSERT INTO tblGradeSubmissionSnapshotEntry(submission_id,version_no,student_id,selection_type,score) VALUES(?,?,?,?,?)")) {
            entry.setString(1, submissionId); entry.setString(2, "S001");
            entry.setString(3, "REQUIRED"); entry.setInt(4, 88);
            entry.setTimestamp(5, java.sql.Timestamp.valueOf(now)); entry.executeUpdate();
            snapshot.setString(1, submissionId); snapshot.setInt(2, 1);
            snapshot.setTimestamp(3, java.sql.Timestamp.valueOf(now)); snapshot.executeUpdate();
            snapshotEntry.setString(1, submissionId); snapshotEntry.setInt(2, 1);
            snapshotEntry.setString(3, "S001"); snapshotEntry.setString(4, "REQUIRED");
            snapshotEntry.setInt(5, 88); snapshotEntry.executeUpdate();
        }
    }

    private void insertFormalResult(String resultId) throws Exception {
        FormalCourseResult result = result(resultId);
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,"
                        + "attempt_no,attempt_type,score,passed,earned_credits,recorded_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, result.getResultId());
            statement.setString(2, result.getStudentId());
            statement.setString(3, result.getCourseId());
            statement.setString(4, result.getOfferingId());
            statement.setString(5, result.getSemester());
            statement.setInt(6, result.getAttemptNo());
            statement.setString(7, result.getAttemptType());
            statement.setInt(8, result.getScore());
            statement.setBoolean(9, result.isPassed());
            statement.setInt(10, result.getEarnedCredits());
            statement.setTimestamp(11, java.sql.Timestamp.valueOf(result.getRecordedAt()));
            statement.executeUpdate();
        }
    }

    private String submissionStatus(String submissionId) throws Exception {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM tblGradeSubmission WHERE submission_id=?")) {
            statement.setString(1, submissionId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getString("status");
            }
        }
    }

    private int formalResultCount() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT COUNT(*) AS total FROM tblCourseResult")) {
            results.next();
            return results.getInt("total");
        }
    }

    private int publicationLinkCount() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(
                        "SELECT COUNT(*) AS total FROM tblGradeSubmissionResult")) {
            results.next();
            return results.getInt("total");
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true");
    }

    private static FormalCourseResult result(String resultId) {
        return new FormalCourseResult(resultId, "S001", "C001", "OFFER-001", "2026-2027-1",
                1, "首修", 88, true, 3, LocalDateTime.of(2026, 9, 6, 15, 0));
    }
}
