package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.library.LibraryService;
import cn.vcampus.store.InMemoryStoreService;
import cn.vcampus.store.StoreService;
import cn.vcampus.student.AcademicAdminService;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.CourseResultRecordingService;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.DefaultTeacherProfileService;
import cn.vcampus.student.InMemoryAcademicAdminStore;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.InMemoryStudentRepository;
import cn.vcampus.student.InMemoryTeacherRepository;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import cn.vcampus.user.AuditLogRepository;
import cn.vcampus.user.UserManagementService;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 多客户端服务器启动入口，负责组装各业务模块并分发 Socket 消息。 */
public final class ServerApplication implements Closeable {
    public static final int DEFAULT_PORT = 19090;
    // 读超时：缓解 newCachedThreadPool + 无超时 readObject 的慢连接占线程问题。
    private static final int CLIENT_SO_TIMEOUT_MS = 60000;

    private final int port;
    private final UserMessageHandler userMessages;
    private final CourseMessageHandler courseMessages;
    private final StoreMessageHandler storeMessages;
    private final StudentMessageHandler studentMessages;
    private final StudentAcademicMessageHandler academicMessages;
    private final TeacherSelfMessageHandler teacherMessages;
    private final AcademicAdminMessageHandler adminMessages;
    private final LibraryMessageHandler libraryMessages;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    /** 为现有测试和本地演示提供一套完整的内存版服务。 */
    public ServerApplication(int port, UserManagementService users) {
        this(port, users, demoBootstrap());
    }

    private ServerApplication(int port, UserManagementService users, DemoBootstrap bootstrap) {
        this(port, users, bootstrap.module.getSelectionService(), bootstrap.module.getCatalogService(),
                bootstrap.module.getOfferingService(), bootstrap.module.getSelectionRoundService(),
                bootstrap.module.getSelectionRecordService(), bootstrap.module.getGradeSubmissionService(),
                bootstrap.students.results, bootstrap.gradeApprovals, bootstrap.students.profiles,
                bootstrap.store, bootstrap.students.students, new InMemoryLibraryService(),
                new DenyTeacherStudentAccessPolicy(), null, bootstrap.students.academics,
                bootstrap.teachers, bootstrap.administration,
                bootstrap.module.getTrainingPlanService());
    }

    /** 保留给只需验证学生选课查询的轻量级测试。 */
    public ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            StudentSelectionProfileProvider profiles) {
        this(port, users, courses, null, null, null, null, null, null, null, profiles,
                new InMemoryStoreService(), memoryStudentServices().students,
                new InMemoryLibraryService(), new DenyTeacherStudentAccessPolicy(), null,
                null, null, null);
    }

    /** 保留既有的模块级构造入口。 */
    ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            CourseCatalogService catalog, CourseOfferingService offerings,
            SelectionRoundService selectionRounds, StudentSelectionProfileProvider profiles,
            StoreService store, StudentManagementService students) {
        this(port, users, courses, catalog, offerings, selectionRounds, null, null, null, null,
                profiles, store, students, new InMemoryLibraryService(),
                new DenyTeacherStudentAccessPolicy(), null, null, null, null);
    }

    /** 供 Access 初始化测试注入学籍查询服务。 */
    ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            CourseCatalogService catalog, CourseOfferingService offerings,
            SelectionRoundService selectionRounds, StudentSelectionProfileProvider profiles,
            StoreService store, StudentManagementService students, LibraryService library,
            TeacherStudentAccessPolicy teacherAccess, AuditLogRepository storeAudit,
            AcademicReviewService academics) {
        this(port, users, courses, catalog, offerings, selectionRounds, null, null, null, null,
                profiles, store, students, library, teacherAccess, storeAudit, academics, null, null);
    }

    /**
     * 统一组装所有可选模块。选课成绩服务与学籍查询服务必须使用同一份 Access 数据源，
     * 以保证教务审核通过后的正式成绩能立即被学籍查询读到。
     */
    ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            CourseCatalogService catalog, CourseOfferingService offerings,
            SelectionRoundService selectionRounds, CourseSelectionRecordService records,
            GradeSubmissionService gradeSubmissions, CourseResultRecordingService formalResults,
            GradeApprovalWorkflow gradeApprovals, StudentSelectionProfileProvider profiles,
            StoreService store, StudentManagementService students, LibraryService library,
            TeacherStudentAccessPolicy teacherAccess, AuditLogRepository storeAudit,
            AcademicReviewService academics, TeacherProfileService teachers,
            AcademicAdminService administration) {
        this(port, users, courses, catalog, offerings, selectionRounds, records, gradeSubmissions,
                formalResults, gradeApprovals, profiles, store, students, library, teacherAccess,
                storeAudit, academics, teachers, administration, null);
    }

    ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            CourseCatalogService catalog, CourseOfferingService offerings,
            SelectionRoundService selectionRounds, CourseSelectionRecordService records,
            GradeSubmissionService gradeSubmissions, CourseResultRecordingService formalResults,
            GradeApprovalWorkflow gradeApprovals, StudentSelectionProfileProvider profiles,
            StoreService store, StudentManagementService students, LibraryService library,
            TeacherStudentAccessPolicy teacherAccess, AuditLogRepository storeAudit,
            AcademicReviewService academics, TeacherProfileService teachers,
            AcademicAdminService administration, TrainingPlanService trainingPlans) {
        InMemoryAcademicReviewService fallbackAcademics = null;
        if (academics == null) {
            fallbackAcademics = new InMemoryAcademicReviewService();
            academics = fallbackAcademics;
            if (formalResults == null) {
                formalResults = fallbackAcademics;
            }
        }
        if (teachers == null) {
            teachers = teacherProfiles(null);
        }
        if (administration == null) {
            administration = new AcademicAdminService(new InMemoryAcademicAdminStore(
                    students, teachers, academics));
        }

        this.port = port;
        this.userMessages = new UserMessageHandler(users);
        this.courseMessages = new CourseMessageHandler(courses, catalog, offerings, selectionRounds,
                records, gradeSubmissions, formalResults, gradeApprovals, trainingPlans, profiles, users,
                teachers, students);
        this.storeMessages = new StoreMessageHandler(store, users, storeAudit);
        this.studentMessages = new StudentMessageHandler(students, users, teacherAccess);
        this.academicMessages = new StudentAcademicMessageHandler(students, academics, users);
        this.teacherMessages = new TeacherSelfMessageHandler(teachers, users);
        this.adminMessages = new AcademicAdminMessageHandler(administration, users);
        this.libraryMessages = new LibraryMessageHandler(library, users);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("vCampus server listening on port " + port);
        while (!serverSocket.isClosed()) {
            try {
                clients.submit(new ClientHandler(serverSocket.accept()));
            } catch (IOException acceptedFailure) {
                if (!serverSocket.isClosed()) {
                    throw acceptedFailure;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
        clients.shutdownNow();
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;

        private ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket client = socket;
                    ObjectInputStream input = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                client.setSoTimeout(CLIENT_SO_TIMEOUT_MS);
                while (!client.isClosed()) {
                    Message request;
                    try {
                        request = (Message) input.readObject();
                    } catch (EOFException end) {
                        break;
                    }
                    output.writeObject(ServerApplication.this.dispatch(request));
                    output.flush();
                }
            } catch (IOException | ClassNotFoundException failure) {
                System.err.println("client connection closed: " + failure.getMessage());
            }
        }
    }

    Message dispatch(Message request) {
        if (request != null && request.getType() == MessageType.ACADEMIC_ADMIN_V1) {
            return adminMessages.handle(request);
        }
        if (request != null && request.getType() == MessageType.TEACHER_SELF_QUERY_V1) {
            return teacherMessages.handle(request);
        }
        if (request != null && request.getType() == MessageType.STUDENT_ACADEMIC_QUERY_V1) {
            return academicMessages.handle(request);
        }
        if (request != null && isCourseMessage(request.getType())) {
            return courseMessages.handle(request);
        }
        if (request != null && isStoreMessage(request.getType())) {
            return storeMessages.handle(request);
        }
        if (request != null && isStudentMessage(request.getType())) {
            return studentMessages.handle(request);
        }
        if (request != null && isLibraryMessage(request.getType())) {
            return libraryMessages.handle(request);
        }
        return userMessages.handle(request);
    }

    private static boolean isCourseMessage(MessageType type) {
        return type == MessageType.COURSE_MANAGE
                || type == MessageType.COURSE_SELECTION_QUERY_V2
                || type == MessageType.COURSE_SELECT_OFFERING_V2
                || type == MessageType.COURSE_DROP_RECORD_V2
                || type == MessageType.COURSE_TEACHING_QUERY_V2
                || type == MessageType.COURSE_GRADE_DRAFT_V2
                || type == MessageType.COURSE_GRADE_IMPORT_V2
                || type == MessageType.COURSE_GRADE_REVIEW_V2
                || type == MessageType.COURSE_TRAINING_PLAN_MANAGE_V2;
    }

    // 商店消息白名单必须覆盖全部 STORE_* 类型，由守护测试锁定。
    static boolean isStoreMessage(MessageType type) {
        return type == MessageType.STORE_QUERY
                || type == MessageType.STORE_PURCHASE || type == MessageType.STORE_ORDER_QUERY
                || type == MessageType.STORE_RESTOCK || type == MessageType.STORE_PRODUCT_ADD
                || type == MessageType.STORE_PRODUCT_UPDATE || type == MessageType.STORE_PRODUCT_DEACTIVATE
                || type == MessageType.STORE_CART_ADD || type == MessageType.STORE_CART_REMOVE
                || type == MessageType.STORE_CART_QUERY || type == MessageType.STORE_CART_CHECKOUT
                || type == MessageType.STORE_ORDER_LIST_ALL || type == MessageType.STORE_HOT_PRODUCTS
                || type == MessageType.STORE_ACCOUNT_QUERY || type == MessageType.STORE_ACCOUNT_RECHARGE
                || type == MessageType.STORE_ACCOUNT_ADJUST
                || type == MessageType.STORE_CART_UPDATE || type == MessageType.STORE_CART_DETAIL
                || type == MessageType.STORE_ACCOUNT_LEDGER || type == MessageType.STORE_PRODUCT_REACTIVATE
                || type == MessageType.STORE_CART_REMOVE_BATCH
                || type == MessageType.STORE_CART_CHECKOUT_SELECTED;
    }

    private static boolean isStudentMessage(MessageType type) {
        return type == MessageType.STUDENT_QUERY || type == MessageType.STUDENT_UPDATE;
    }

    private static boolean isLibraryMessage(MessageType type) {
        return type == MessageType.LIBRARY_QUERY_V2 || type == MessageType.LIBRARY_DETAIL_V2
                || type == MessageType.LIBRARY_BORROW_V2 || type == MessageType.LIBRARY_RETURN_V2
                || type == MessageType.LIBRARY_HISTORY_V2 || type == MessageType.LIBRARY_ADD_BOOK_V2;
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        Path databasePath = UserServiceFactory.databasePath(args);
        CourseServiceFactory.CourseRuntime courses = CourseServiceFactory.create(databasePath);
        StudentServices studentServices = databasePath == null
                ? memoryStudentServices(true)
                : accessStudentServices(databasePath);
        TeacherProfileService teachers = teacherProfiles(databasePath);
        AcademicAdminService administration = new AcademicAdminService(databasePath == null
                ? new InMemoryAcademicAdminStore(studentServices.students, teachers,
                        studentServices.academics)
                : new AccessAcademicAdminStore(databasePath));
        GradeApprovalWorkflow gradeApprovals = databasePath == null
                ? new InMemoryGradeApprovalWorkflow(courses.getModule().getGradeSubmissionService(),
                        studentServices.results)
                : new AccessGradeApprovalWorkflow(databasePath);
        CourseSelectionModule module = courses.getModule();
        new ServerApplication(port, UserServiceFactory.create(args), module.getSelectionService(),
                module.getCatalogService(), module.getOfferingService(), module.getSelectionRoundService(),
                module.getSelectionRecordService(), module.getGradeSubmissionService(),
                studentServices.results, gradeApprovals, courses.getProfiles(),
                StoreServiceFactory.create(databasePath), studentServices.students,
                LibraryServiceFactory.create(databasePath), teacherAccess(databasePath),
                UserServiceFactory.createStoreAuditLog(args), studentServices.academics,
                teachers, administration, module.getTrainingPlanService()).start();
    }

    /** 教师档案与教学班教师编号使用同一资料源，避免教师登录后找不到自己的教学班。 */
    static TeacherProfileService teacherProfiles(Path databasePath) {
        if (databasePath != null) {
            return new DefaultTeacherProfileService(new AccessTeacherRepository(databasePath));
        }
        InMemoryTeacherRepository repository = new InMemoryTeacherRepository();
        repository.save(new TeacherProfile("教师001", "demo_teacher", "演示任课教师一",
                "计算机学院", "讲师", true));
        repository.save(new TeacherProfile("教师002", "demo_teacher_002", "演示教师二",
                "计算机学院", "讲师", true));
        repository.save(new TeacherProfile("教师003", "demo_teacher_003", "演示教师三",
                "通识教育学院", "讲师", true));
        return new DefaultTeacherProfileService(repository);
    }

    private static StudentServices memoryStudentServices() {
        return memoryStudentServices(false);
    }

    /** 生产内存演示可加载完整样例；测试构造器默认使用最小资料，避免测试相互污染。 */
    private static StudentServices memoryStudentServices(boolean richDemo) {
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        repository.save(new StudentRecord("20260001", "demo_student", "演示学生", "未知",
                "计算机学院", "计算机科学与技术", "CS2026-01", 2026,
                "在读", "", ""));
        if (richDemo) {
            repository.save(new StudentRecord("20260002", "demo_student_new", "演示新生", "未知",
                    "计算机学院", "计算机科学与技术", "CS2026-01", 2026,
                    "在读", "", ""));
            repository.save(new StudentRecord("20230003", "demo_student_retake", "演示重修学生", "未知",
                    "计算机学院", "软件工程", "SE2023-01", 2023,
                    "在读", "", ""));
            repository.save(new StudentRecord("20260004", "demo_student_elective", "演示选修学生", "未知",
                    "计算机学院", "计算机科学与技术", "CS2026-01", 2026,
                    "在读", "", ""));
            repository.save(new StudentRecord("20260005", "demo_student_cross", "演示跨专业学生", "未知",
                    "计算机学院", "计算机科学与技术", "CS2026-01", 2026,
                    "在读", "", ""));
        }
        StudentManagementService students = new DefaultStudentManagementService(repository);
        InMemoryAcademicReviewService academics = new InMemoryAcademicReviewService();
        if (richDemo) {
            academics.addHistory(new CourseHistoryRecord("20230003", "DB101", "数据库原理",
                    "2025-2026-2", 1, "首修", 48, false, 0));
        }
        return new StudentServices(students, new StudentSelectionProfileAdapter(students, academics,
                CourseSelectionDemoFactory.DEMO_TERM), academics, academics);
    }

    private static StudentServices accessStudentServices(Path databasePath) {
        StudentManagementService students = new DefaultStudentManagementService(
                new AccessStudentRepository(databasePath));
        AccessAcademicReviewService academics = new AccessAcademicReviewService(databasePath);
        return new StudentServices(students, new StudentSelectionProfileAdapter(students, academics,
                CourseSelectionDemoFactory.DEMO_TERM), academics, academics);
    }

    private static TeacherStudentAccessPolicy teacherAccess(Path databasePath) {
        return databasePath == null ? new DenyTeacherStudentAccessPolicy()
                : new AccessTeacherStudentAccessPolicy(databasePath);
    }

    private static DemoBootstrap demoBootstrap() {
        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        StudentServices students = memoryStudentServices();
        TeacherProfileService teachers = teacherProfiles(null);
        AcademicAdminService administration = new AcademicAdminService(new InMemoryAcademicAdminStore(
                students.students, teachers, students.academics));
        return new DemoBootstrap(module, students, new InMemoryStoreService(), teachers, administration,
                new InMemoryGradeApprovalWorkflow(module.getGradeSubmissionService(), students.results));
    }

    private static final class StudentServices {
        private final StudentManagementService students;
        private final StudentSelectionProfileProvider profiles;
        private final AcademicReviewService academics;
        private final CourseResultRecordingService results;

        private StudentServices(StudentManagementService students,
                StudentSelectionProfileProvider profiles, AcademicReviewService academics,
                CourseResultRecordingService results) {
            this.students = students;
            this.profiles = profiles;
            this.academics = academics;
            this.results = results;
        }
    }

    private static final class DemoBootstrap {
        private final CourseSelectionModule module;
        private final StudentServices students;
        private final StoreService store;
        private final TeacherProfileService teachers;
        private final AcademicAdminService administration;
        private final GradeApprovalWorkflow gradeApprovals;

        private DemoBootstrap(CourseSelectionModule module, StudentServices students, StoreService store,
                TeacherProfileService teachers, AcademicAdminService administration,
                GradeApprovalWorkflow gradeApprovals) {
            this.module = module;
            this.students = students;
            this.store = store;
            this.teachers = teachers;
            this.administration = administration;
            this.gradeApprovals = gradeApprovals;
        }
    }

    private static int parsePort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        if (args.length > 0 && args[0].matches("\\d+")) {
            return Integer.parseInt(args[0]);
        }
        return DEFAULT_PORT;
    }
}
