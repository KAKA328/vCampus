package cn.vcampus.server;

import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.DefaultCourseOfferingCapacityService;
import cn.vcampus.course.DefaultCourseSelectionService;
import cn.vcampus.course.ScheduleConflictDetector;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.student.DefaultTeacherProfileService;
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
            return new CourseRuntime(CourseSelectionDemoFactory.createModule(),
                    CourseSelectionDemoFactory.createProfileProvider());
        }
        CourseCatalogService catalog = new AccessCourseCatalogService(databasePath);
        TeacherProfileService teachers = new DefaultTeacherProfileService(
                new AccessTeacherRepository(databasePath));
        CourseOfferingService offerings = new AccessCourseOfferingService(
                databasePath, catalog, null, teachers);
        CourseSelectionRecordService records = new AccessCourseSelectionRecordService(databasePath,
                offerings);
        SelectionRoundService rounds = new AccessSelectionRoundService(databasePath);
        TrainingPlanService trainingPlans = new AccessTrainingPlanService(databasePath, catalog);
        CourseSelectionService selections = new DefaultCourseSelectionService(catalog, trainingPlans,
                rounds, offerings, records, new DefaultCourseOfferingCapacityService(offerings, records),
                new ScheduleConflictDetector());
        return new CourseRuntime(new CourseSelectionModule(selections, catalog, offerings, rounds),
                new AccessStudentSelectionProfileProvider(databasePath));
    }

    static final class CourseRuntime {
        private final CourseSelectionModule module;
        private final StudentSelectionProfileProvider profiles;

        private CourseRuntime(CourseSelectionModule module, StudentSelectionProfileProvider profiles) {
            this.module = module;
            this.profiles = profiles;
        }

        CourseSelectionModule getModule() {
            return module;
        }

        StudentSelectionProfileProvider getProfiles() {
            return profiles;
        }
    }
}
