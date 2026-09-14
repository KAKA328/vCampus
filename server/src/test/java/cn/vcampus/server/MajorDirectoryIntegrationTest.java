package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.course.*;
import cn.vcampus.user.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MajorDirectoryIntegrationTest {
    @TempDir Path directory;

    @Test void accessFiltersInactiveSortsAndKeepsStableIdsAfterReopen() throws Exception {
        Path db = directory.resolve("majors.accdb");
        AccessDatabaseSchemaTest.executeScript(db, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
        AccessDatabaseSchemaTest.executeScript(db, AccessDatabaseSchemaTest.readScript("database/seed.sql"));
        AccessDatabaseSchemaTest.executeScript(db,
                "INSERT INTO tblMajor(major_id,major_name,department_name,active) VALUES('OLD','停用专业','A院系',0);"
                + "INSERT INTO tblMajor(major_id,major_name,department_name,active) VALUES('NEW','新增专业','A院系',1);");
        MajorDirectoryService service = new AccessMajorDirectoryService(db);
        List<MajorDirectoryEntry> rows = service.listActive().getData();
        assertEquals(Arrays.asList("NEW", "CS", "SE", "CN"), rows.stream()
                .map(MajorDirectoryEntry::getMajorId).collect(Collectors.toList()));
        assertEquals("NEW", new AccessMajorDirectoryService(db).listActive().getData().get(0).getMajorId());
        assertEquals(StatusCode.BAD_REQUEST, service.requireActiveName("停用专业").getStatus());
        assertEquals(StatusCode.OK, service.requireActiveName("软件工程").getStatus());
        for (TrainingPlan plan : new AccessTrainingPlanService(db, new AccessCourseCatalogService(db)).listAll().getData()) {
            assertEquals(StatusCode.OK, service.requireActiveName(plan.getMajorName()).getStatus(), plan.getMajorName());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + db + ";immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblMajor SET active=0");
        }
        assertTrue(service.listActive().getData().isEmpty());
        Path missing = directory.resolve("missing.accdb");
        AccessDatabaseSchemaTest.executeScript(missing, "CREATE TABLE other_table (id INTEGER)");
        assertEquals(StatusCode.SERVER_ERROR, new AccessMajorDirectoryService(missing).listActive().getStatus());
    }

    @Test void endpointRequiresAdministratorSessionAndDoesNotInventFallbackData() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        MajorDirectoryMessageHandler handler = new MajorDirectoryMessageHandler(InMemoryMajorDirectoryService.demo(), users);
        for (Role role : Role.values()) {
            String token = login(users, "role_" + role, role);
            Message response = handler.handle(query(token));
            assertEquals(role == Role.ADMIN || role == Role.ACADEMIC_ADMIN ? StatusCode.OK : StatusCode.FORBIDDEN,
                    response.getStatusCode(), role.name());
        }
        assertEquals(StatusCode.UNAUTHORIZED, handler.handle(query("invalid")).getStatusCode());
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.STUDENT_MAJOR_DIRECTORY_QUERY_V1, "not a command")).getStatusCode());
        String admin = login(users, "empty_admin", Role.ADMIN);
        assertTrue(((List<?>) new MajorDirectoryMessageHandler(
                new InMemoryMajorDirectoryService(Collections.emptyList()), users).handle(query(admin)).getPayload()).isEmpty());
        assertEquals(StatusCode.SERVER_ERROR, new MajorDirectoryMessageHandler(
                () -> ServiceResult.failure(StatusCode.SERVER_ERROR, "unavailable"), users)
                .handle(query(admin)).getStatusCode());
    }

    @Test void trainingPlanCreatesAndEditsUsingNamesOnlyAndRejectsInvalidDirectory() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        String token = login(users, "academic", Role.ACADEMIC_ADMIN);
        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        MajorDirectoryService majors = new InMemoryMajorDirectoryService(Arrays.asList(
                new MajorDirectoryEntry("SE", "软件工程", "计算机学院", true),
                new MajorDirectoryEntry("CS", "计算机科学与技术", "计算机学院", true),
                new MajorDirectoryEntry("OLD", "旧专业", "计算机学院", false)));
        CourseMessageHandler handler = handler(module, users, majors);
        TrainingPlan original = new TrainingPlan("PLAN-DIRECTORY", "软件工程", 2030,
                Collections.singletonList(new TrainingPlanCourse("JAVA101", 1, SelectionType.REQUIRED, false)));
        Message created = handler.handle(manage(TrainingPlanManagementCommand.create(token, original)));
        assertEquals(StatusCode.OK, created.getStatusCode(), String.valueOf(created.getPayload()));
        assertEquals("软件工程", module.getTrainingPlanService().findById(original.getPlanId()).getData().getMajorName());
        TrainingPlan changed = original.withBasicInfo("计算机科学与技术", 2030);
        assertEquals(StatusCode.OK, handler.handle(manage(TrainingPlanManagementCommand.updateBasicInfo(token, changed))).getStatusCode());
        for (String invalid : Arrays.asList("任意专业", "旧专业", "CS - 计算机科学与技术")) {
            Message rejected = handler.handle(manage(
                    TrainingPlanManagementCommand.updateBasicInfo(token, original.withBasicInfo(invalid, 2030))));
            assertEquals(StatusCode.BAD_REQUEST, rejected.getStatusCode());
            assertEquals("请选择专业目录中唯一且有效的专业", rejected.getPayload());
        }
        CourseMessageHandler unavailable = handler(module, users,
                () -> ServiceResult.failure(StatusCode.SERVER_ERROR, "专业目录不可用"));
        Message unavailableResponse = unavailable.handle(manage(
                TrainingPlanManagementCommand.updateBasicInfo(token, original)));
        assertEquals(StatusCode.SERVER_ERROR, unavailableResponse.getStatusCode());
        assertEquals("专业目录不可用", unavailableResponse.getPayload());
        assertEquals("计算机科学与技术", module.getTrainingPlanService().findById(original.getPlanId()).getData().getMajorName());
    }

    private static CourseMessageHandler handler(CourseSelectionModule module, UserManagementService users, MajorDirectoryService majors) {
        return new CourseMessageHandler(module.getSelectionService(), module.getCatalogService(),
                module.getOfferingService(), module.getSelectionRoundService(), null, null, null, null,
                module.getTrainingPlanService(), new InMemoryStudentSelectionProfileProvider(Collections.emptyList()),
                users, null, null, majors);
    }

    @Test void defaultDirectorySupportsExistingMajorsAndNewChineseLiteratureYear() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        String token = login(users, "cn_admin", Role.ACADEMIC_ADMIN);
        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        MajorDirectoryService majors = InMemoryMajorDirectoryService.demo();
        for (TrainingPlan plan : module.getTrainingPlanService().listAll().getData()) {
            assertEquals(StatusCode.OK, majors.requireActiveName(plan.getMajorName()).getStatus(), plan.getMajorName());
        }
        TrainingPlan plan = new TrainingPlan("PLAN-CN-2027", "汉语言文学", 2027,
                Collections.emptyList());
        Message result = handler(module, users, majors).handle(manage(TrainingPlanManagementCommand.create(token, plan)));
        assertEquals(StatusCode.OK, result.getStatusCode(), String.valueOf(result.getPayload()));
        assertEquals("汉语言文学", module.getTrainingPlanService().findById("PLAN-CN-2027").getData().getMajorName());
    }
    private static Message manage(TrainingPlanManagementCommand command) {
        return Message.request("plan", MessageType.COURSE_TRAINING_PLAN_MANAGE_V2, command);
    }
    private static Message query(String token) {
        return Message.request("majors", MessageType.STUDENT_MAJOR_DIRECTORY_QUERY_V1, new MajorDirectoryQueryV1Command(token));
    }
    private static String login(InMemoryUserManagementService users, String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", id, role.name());
        assertEquals(StatusCode.OK, users.register(credentials).getStatus());
        return users.login(credentials).getData().getToken();
    }
}
