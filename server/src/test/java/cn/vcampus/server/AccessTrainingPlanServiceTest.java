package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证培养方案和课程要求会作为一个整体保存到 Access。 */
class AccessTrainingPlanServiceTest {
    @TempDir Path temporaryDirectory;
    private AccessTrainingPlanService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("training-plan-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblCourse (course_id VARCHAR(32) NOT NULL,course_name VARCHAR(100) NOT NULL,credits INTEGER NOT NULL,status VARCHAR(16) NOT NULL,PRIMARY KEY(course_id))");
            statement.execute("CREATE TABLE tblTrainingPlan (plan_id VARCHAR(36) NOT NULL,major_name VARCHAR(64) NOT NULL,enrollment_year INTEGER NOT NULL,status VARCHAR(16) NOT NULL,PRIMARY KEY(plan_id))");
            statement.execute("CREATE TABLE tblTrainingPlanCourse (plan_id VARCHAR(36) NOT NULL,course_id VARCHAR(32) NOT NULL,recommended_term INTEGER NOT NULL,selection_type VARCHAR(16) NOT NULL,cross_major_allowed BIT NOT NULL,PRIMARY KEY(plan_id,course_id))");
        }
        AccessCourseCatalogService catalog = new AccessCourseCatalogService(database);
        catalog.create(new Course("CS101", "程序设计基础", 3));
        catalog.create(new Course("CS102", "数据结构", 3));
        service = new AccessTrainingPlanService(database, catalog);
    }

    @Test
    void createsPublishesAndQueriesPlanCourses() {
        TrainingPlan plan = new TrainingPlan("PLAN-001", "软件工程", 2026, Arrays.asList(
                new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false)));
        assertEquals(StatusCode.OK, service.create(plan).getStatus());
        assertEquals(StatusCode.OK, service.saveCourse("PLAN-001",
                new TrainingPlanCourse("CS102", 1, SelectionType.ELECTIVE, false)).getStatus());
        assertEquals(StatusCode.OK, service.changeStatus("PLAN-001", TrainingPlanStatus.PUBLISHED).getStatus());
        assertEquals(2, service.listCoursesByRecommendedTerm("软件工程", 2026, 1).getData().size());
    }

    @Test
    void rejectsDuplicateScopeAndPublishedPlanChanges() {
        TrainingPlan plan = new TrainingPlan("PLAN-001", "软件工程", 2026, Arrays.asList(
                new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false)));
        service.create(plan);
        TrainingPlan duplicate = new TrainingPlan("PLAN-002", "软件工程", 2026, Arrays.asList(
                new TrainingPlanCourse("CS102", 1, SelectionType.ELECTIVE, false)));
        assertEquals(StatusCode.CONFLICT, service.create(duplicate).getStatus());
        service.changeStatus("PLAN-001", TrainingPlanStatus.PUBLISHED);
        assertEquals(StatusCode.CONFLICT, service.removeCourse("PLAN-001", "CS101").getStatus());
    }
}
