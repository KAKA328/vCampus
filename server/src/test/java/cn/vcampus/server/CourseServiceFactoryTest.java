package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证指定 --db 时选课模块不会回退到内存演示服务。 */
class CourseServiceFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void accessDatabaseModeBuildsAccessBackedCourseServices() {
        CourseServiceFactory.CourseRuntime runtime = CourseServiceFactory.create(
                temporaryDirectory.resolve("vcampus.accdb"));

        assertTrue(runtime.getModule().getCatalogService() instanceof AccessCourseCatalogService);
        assertTrue(runtime.getModule().getOfferingService() instanceof AccessCourseOfferingService);
        assertTrue(runtime.getModule().getSelectionRoundService() instanceof AccessSelectionRoundService);
        assertTrue(runtime.getProfiles() instanceof AccessStudentSelectionProfileProvider);
    }
}
