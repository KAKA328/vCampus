package cn.vcampus.server;

import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.DefaultCourseOfferingCapacityService;
import cn.vcampus.course.DefaultCourseSelectionService;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.ScheduleConflictDetector;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.student.DefaultTeacherProfileService;
import cn.vcampus.student.InMemoryTeacherRepository;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import java.nio.file.Path;

/** 按启动模式组装完整选课模块，确保所有服务使用同一个 Access 数据库。 */
final class CourseServiceFactory {
    private CourseServiceFactory() {
    }

    static CourseRuntime create(String[] args) {
        return create(UserServiceFactory.databasePath(args));
    }

    static CourseRuntime create(Path databasePath) {
        if (databasePath == null) {
            TeacherProfileService teachers = demoTeacherProfiles();
            return new CourseRuntime(CourseSelectionDemoFactory.createModule(),
                    CourseSelectionDemoFactory.createProfileProvider(), teachers);
        }
        CourseCatalogService catalog = new AccessCourseCatalogService(databasePath);
        TeacherProfileService teachers = new DefaultTeacherProfileService(
                new AccessTeacherRepository(databasePath));
        CourseOfferingService offerings = new AccessCourseOfferingService(
                databasePath, catalog, null, teachers);
        CourseSelectionRecordService records = new AccessCourseSelectionRecordService(databasePath,
                offerings);
        GradeSubmissionService gradeSubmissions = new AccessGradeSubmissionService(databasePath);
        SelectionRoundService rounds = new AccessSelectionRoundService(databasePath);
        TrainingPlanService trainingPlans = new AccessTrainingPlanService(databasePath, catalog);
        CourseSelectionService selections = new DefaultCourseSelectionService(catalog, trainingPlans,
                rounds, offerings, records, new DefaultCourseOfferingCapacityService(offerings, records),
                new ScheduleConflictDetector());
        return new CourseRuntime(new CourseSelectionModule(selections, catalog, offerings, rounds, records,
                gradeSubmissions),
                new AccessStudentSelectionProfileProvider(databasePath), teachers);
    }

    private static TeacherProfileService demoTeacherProfiles() {
        InMemoryTeacherRepository repository = new InMemoryTeacherRepository();
        TeacherProfileService teachers = new DefaultTeacherProfileService(repository);
        teachers.save(new TeacherProfile("教师001", "demo_teacher_001", "演示教师一", "计算机学院", "讲师", true));
        teachers.save(new TeacherProfile("教师002", "demo_teacher_002", "演示教师二", "计算机学院", "讲师", true));
        teachers.save(new TeacherProfile("教师003", "demo_teacher_003", "演示教师三", "通识教育学院", "讲师", true));
        return teachers;
    }

    static final class CourseRuntime {
        private final CourseSelectionModule module;
        private final StudentSelectionProfileProvider profiles;
        private final TeacherProfileService teachers;

        private CourseRuntime(CourseSelectionModule module, StudentSelectionProfileProvider profiles,
                TeacherProfileService teachers) {
            this.module = module;
            this.profiles = profiles;
            this.teachers = teachers;
        }

        CourseSelectionModule getModule() {
            return module;
        }

        StudentSelectionProfileProvider getProfiles() {
            return profiles;
        }

        TeacherProfileService getTeachers() {
            return teachers;
        }
    }
}
