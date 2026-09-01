package cn.vcampus.server;

import cn.vcampus.student.InMemoryStudentManagementService;
import cn.vcampus.student.StudentManagementService;
import java.nio.file.Path;

/** Creates student-management service for memory or Access-backed modes. */
final class StudentServiceFactory {
    private StudentServiceFactory() { }

    static StudentManagementService create(String[] args) {
        return create(UserServiceFactory.databasePath(args));
    }

    static StudentManagementService create(Path databasePath) {
        return databasePath == null
                ? new InMemoryStudentManagementService()
                : new AccessStudentManagementService(databasePath);
    }
}
