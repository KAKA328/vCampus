package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.SelectionRecordStatus;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证选课和退选记录可真实保存到 Access。 */
class AccessCourseSelectionRecordServiceTest {
    @TempDir
    Path temporaryDirectory;

    private AccessCourseSelectionRecordService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("selection-record-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            createCurrentTables(statement);
        }
        AccessCourseCatalogService catalog = new AccessCourseCatalogService(database);
        catalog.create(new Course("CS101", "程序设计基础", 3));
        AccessCourseOfferingService offerings = new AccessCourseOfferingService(database, catalog);
        offerings.create(offering("OFFER-001"));
        service = new AccessCourseSelectionRecordService(database, offerings);
    }

    @Test
    void createsPersistsAndDropsRecord() {
        CourseSelectionRecord record = record("RECORD-001", "S001", SelectionType.REQUIRED);

        assertEquals(StatusCode.OK, service.create(record).getStatus());
        AccessCourseSelectionRecordService restarted = new AccessCourseSelectionRecordService(
                temporaryDirectory.resolve("selection-record-test.accdb"),
                new AccessCourseOfferingService(temporaryDirectory.resolve("selection-record-test.accdb"),
                        new AccessCourseCatalogService(temporaryDirectory.resolve(
                                "selection-record-test.accdb"))));
        assertEquals(1, restarted.listActiveByStudent("S001").getData().size());
        assertEquals(StatusCode.OK, restarted.markDropped("RECORD-001",
                LocalDateTime.of(2026, 9, 2, 9, 0)).getStatus());

        ServiceResult<List<CourseSelectionRecord>> records = restarted.listByStudent("S001");
        assertEquals(1, records.getData().size());
        assertEquals(SelectionRecordStatus.DROPPED, records.getData().get(0).getStatus());
        assertEquals(0, restarted.listActiveByOffering("OFFER-001").getData().size());
    }

    @Test
    void rejectsDuplicateActiveSelectionForSameOffering() {
        assertEquals(StatusCode.OK,
                service.create(record("RECORD-001", "S001", SelectionType.REQUIRED)).getStatus());

        assertEquals(StatusCode.CONFLICT,
                service.create(record("RECORD-002", "S001", SelectionType.RETAKE)).getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.create(record("RECORD-001", "S002", SelectionType.REQUIRED)).getStatus());
    }

    @Test
    void databaseConstraintKeepsOneActiveRecordAndAllowsReselectAfterDrop() throws Exception {
        AccessCourseSelectionRecordService secondService = new AccessCourseSelectionRecordService(
                temporaryDirectory.resolve("selection-record-test.accdb"),
                new AccessCourseOfferingService(temporaryDirectory.resolve("selection-record-test.accdb"),
                        new AccessCourseCatalogService(temporaryDirectory.resolve(
                                "selection-record-test.accdb"))));
        ServiceResult<CourseSelectionRecord>[] results = createConcurrently(service,
                record("RECORD-001", "S001", SelectionType.REQUIRED), secondService,
                record("RECORD-002", "S001", SelectionType.REQUIRED));

        assertOneSuccessAndOneConflict(results);
        CourseSelectionRecord first = results[0].getStatus() == StatusCode.OK
                ? results[0].getData() : results[1].getData();
        assertEquals(StatusCode.OK, secondService.markDropped(first.getRecordId(),
                LocalDateTime.of(2026, 9, 2, 9, 0)).getStatus());
        assertEquals(StatusCode.OK, service.create(record("RECORD-003", "S001",
                SelectionType.REQUIRED)).getStatus());

        ServiceResult<List<CourseSelectionRecord>> history = service.listByStudent("S001");
        assertEquals(2, history.getData().size());
        assertTrue(history.getData().stream().anyMatch(
                record -> record.getStatus() == SelectionRecordStatus.DROPPED));
        assertEquals(1, service.listActiveByStudent("S001").getData().size());
    }

    @Test
    void databaseCapacityReservationPreventsConcurrentOverbooking() throws Exception {
        AccessCourseSelectionRecordService secondService = new AccessCourseSelectionRecordService(
                temporaryDirectory.resolve("selection-record-test.accdb"),
                new AccessCourseOfferingService(temporaryDirectory.resolve("selection-record-test.accdb"),
                        new AccessCourseCatalogService(temporaryDirectory.resolve(
                                "selection-record-test.accdb"))));
        ServiceResult<CourseSelectionRecord>[] results = createConcurrently(service,
                record("RECORD-001", "S001", SelectionType.REQUIRED), secondService,
                record("RECORD-002", "S002", SelectionType.REQUIRED));

        assertOneSuccessAndOneConflict(results);
        assertEquals(1, service.listActiveByOffering("OFFER-001").getData().size());
    }

    private static CourseOffering offering(String offeringId) {
        return new CourseOffering(offeringId, "CS101", "2026-2027-1", "T001", "周一第1-2节",
                "教学楼A201", 1, 5, 4, CourseOfferingStatus.OPEN);
    }

    private static CourseSelectionRecord record(String recordId, String studentId,
            SelectionType selectionType) {
        return new CourseSelectionRecord(recordId, studentId, "OFFER-001", "ROUND-001",
                selectionType, LocalDateTime.of(2026, 9, 1, 8, 0));
    }

    private static void createCurrentTables(Statement statement) throws Exception {
        statement.execute("CREATE TABLE tblCourse ("
                + "course_id VARCHAR(32) NOT NULL,course_name VARCHAR(100) NOT NULL,"
                + "credits INTEGER NOT NULL,status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (course_id))");
        statement.execute("CREATE TABLE tblCourseOffering ("
                + "offering_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL,"
                + "teacher_id VARCHAR(32) NOT NULL,term VARCHAR(32) NOT NULL,schedule VARCHAR(128) NOT NULL,"
                + "location VARCHAR(64) NOT NULL,required_capacity INTEGER NOT NULL,"
                + "elective_capacity INTEGER NOT NULL,cross_major_capacity INTEGER NOT NULL,"
                + "status VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (offering_id))");
        statement.execute("CREATE TABLE tblCourseMeeting ("
                + "offering_id VARCHAR(36) NOT NULL,day_of_week INTEGER NOT NULL,"
                + "start_period INTEGER NOT NULL,end_period INTEGER NOT NULL,"
                + "location VARCHAR(64) NOT NULL,"
                + "PRIMARY KEY (offering_id,day_of_week,start_period))");
        statement.execute("CREATE TABLE tblCourseSelection ("
                + "selection_id VARCHAR(36) NOT NULL,student_id VARCHAR(32) NOT NULL,"
                + "offering_id VARCHAR(36) NOT NULL,"
                + "round_id VARCHAR(36) NOT NULL,selection_type VARCHAR(16) NOT NULL,"
                + "selected_at DATETIME NOT NULL,status VARCHAR(16) NOT NULL,dropped_at DATETIME,"
                + "PRIMARY KEY (selection_id))");
        statement.execute("CREATE TABLE tblActiveCourseSelection ("
                + "student_id VARCHAR(32) NOT NULL,offering_id VARCHAR(36) NOT NULL,"
                + "PRIMARY KEY (student_id,offering_id))");
        statement.execute("CREATE TABLE tblCourseOfferingCapacityUsage ("
                + "offering_id VARCHAR(36) NOT NULL,capacity_bucket VARCHAR(16) NOT NULL,"
                + "used_count INTEGER NOT NULL,PRIMARY KEY (offering_id,capacity_bucket))");
    }

    @SuppressWarnings("unchecked")
    private static ServiceResult<CourseSelectionRecord>[] createConcurrently(
            AccessCourseSelectionRecordService firstService, CourseSelectionRecord firstRecord,
            AccessCourseSelectionRecordService secondService, CourseSelectionRecord secondRecord)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ServiceResult<CourseSelectionRecord>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return firstService.create(firstRecord);
            });
            Future<ServiceResult<CourseSelectionRecord>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return secondService.create(secondRecord);
            });
            ready.await();
            start.countDown();
            return new ServiceResult[] { first.get(), second.get() };
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertOneSuccessAndOneConflict(ServiceResult<CourseSelectionRecord>[] results) {
        int successCount = 0;
        int conflictCount = 0;
        for (ServiceResult<CourseSelectionRecord> result : results) {
            if (result.getStatus() == StatusCode.OK) successCount++;
            if (result.getStatus() == StatusCode.CONFLICT) conflictCount++;
        }
        assertEquals(1, successCount);
        assertEquals(1, conflictCount);
    }

}
