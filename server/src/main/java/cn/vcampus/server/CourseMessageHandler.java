package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseDropRecordV2Command;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectOfferingV2Command;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.CourseTeachingQueryV2Command;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将当前选课流程转换为 Socket 消息；学生资料仅由服务器按 token 查询。 */
final class CourseMessageHandler {
    private final CourseSelectionService courses;
    private final CourseCatalogService catalog;
    private final CourseOfferingService offerings;
    private final SelectionRoundService selectionRounds;
    private final CourseSelectionRecordService records;
    private final StudentSelectionProfileProvider profiles;
    private final UserManagementService users;
    private final TeacherProfileService teachers;
    private final StudentManagementService students;

    CourseMessageHandler(CourseSelectionService courses, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, null, null, null, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, catalog, offerings, null, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            StudentSelectionProfileProvider profiles, UserManagementService users) {
        this(courses, catalog, offerings, selectionRounds, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, StudentSelectionProfileProvider profiles,
            UserManagementService users, TeacherProfileService teachers,
            StudentManagementService students) {
        if (courses == null || profiles == null || users == null) {
            throw new IllegalArgumentException("course handler dependencies must not be null");
        }
        this.courses = courses;
        this.catalog = catalog;
        this.offerings = offerings;
        this.selectionRounds = selectionRounds;
        this.records = records;
        this.profiles = profiles;
        this.users = users;
        this.teachers = teachers;
        this.students = students;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.COURSE_SELECTION_QUERY_V2, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case COURSE_SELECTION_QUERY_V2:
                    result = query(payload(request, CourseSelectionQueryV2Command.class));
                    break;
                case COURSE_SELECT_OFFERING_V2:
                    CourseSelectOfferingV2Command select =
                            payload(request, CourseSelectOfferingV2Command.class);
                    result = select(select);
                    break;
                case COURSE_DROP_RECORD_V2:
                    result = drop(payload(request, CourseDropRecordV2Command.class));
                    break;
                case COURSE_TEACHING_QUERY_V2:
                    result = teachingQuery(payload(request, CourseTeachingQueryV2Command.class));
                    break;
                case COURSE_MANAGE:
                    result = manage(payload(request, CourseManagementCommand.class));
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "course handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(CourseSelectionQueryV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_READ);
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (command.getQueryType() == CourseSelectionQueryV2Command.QueryType.AVAILABLE_ROUNDS) {
            return courses.listAvailableRounds(profile.getData(), LocalDateTime.now());
        }
        if (command.getQueryType() == CourseSelectionQueryV2Command.QueryType.AVAILABLE_OFFERINGS) {
            return courses.listAvailableOfferings(profile.getData(), command.getRoundId(),
                    LocalDateTime.now());
        }
        return courses.listSelectedOfferings(profile.getData());
    }

    private ServiceResult<?> select(CourseSelectOfferingV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.select(profile.getData(), command.getRoundId(), command.getOfferingId(),
                        LocalDateTime.now()) : profile;
    }

    private ServiceResult<?> drop(CourseDropRecordV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.drop(profile.getData(), command.getRecordId(), LocalDateTime.now()) : profile;
    }

    /** 教师仅能查看自己任教的教学班和其中仍有效的选课记录。 */
    private ServiceResult<?> teachingQuery(CourseTeachingQueryV2Command command) {
        ServiceResult<TeacherProfile> profile = teacherProfile(command.getToken());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (offerings == null || catalog == null) return teachingServiceUnavailable();
        if (command.getQueryType() == CourseTeachingQueryV2Command.QueryType.MY_OFFERINGS) {
            ServiceResult<List<cn.vcampus.course.CourseOffering>> offeringResult = offerings
                    .listByTeacher(profile.getData().getTeacherId(), command.getTerm());
            if (offeringResult.getStatus() != StatusCode.OK) return offeringResult;
            List<TeachingOffering> result = new ArrayList<TeachingOffering>();
            for (cn.vcampus.course.CourseOffering offering : offeringResult.getData()) {
                ServiceResult<cn.vcampus.course.Course> course = catalog.findById(offering.getCourseId());
                if (course.getStatus() != StatusCode.OK) {
                    return ServiceResult.failure(course.getStatus(), course.getMessage());
                }
                result.add(new TeachingOffering(offering, course.getData()));
            }
            return ServiceResult.ok(result);
        }
        return roster(profile.getData(), command.getOfferingId());
    }

    private ServiceResult<?> roster(TeacherProfile teacher, String offeringId) {
        if (records == null || students == null) return teachingServiceUnavailable();
        ServiceResult<cn.vcampus.course.CourseOffering> offering = offerings.findById(offeringId);
        if (offering.getStatus() != StatusCode.OK) return offering;
        if (!teacher.getTeacherId().equals(offering.getData().getTeacherId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "teacher cannot view another teacher's offering roster");
        }
        ServiceResult<cn.vcampus.course.Course> course = catalog.findById(
                offering.getData().getCourseId());
        if (course.getStatus() != StatusCode.OK) return course;
        ServiceResult<List<CourseSelectionRecord>> recordsResult = records
                .listActiveByOffering(offering.getData().getOfferingId());
        if (recordsResult.getStatus() != StatusCode.OK) return recordsResult;
        List<String> studentIds = new ArrayList<String>();
        for (CourseSelectionRecord record : recordsResult.getData()) {
            studentIds.add(record.getStudentId());
        }
        ServiceResult<List<StudentRecord>> studentsResult = students.findByIds(studentIds);
        if (studentsResult.getStatus() != StatusCode.OK) return studentsResult;
        Map<String, StudentRecord> studentsById = new LinkedHashMap<String, StudentRecord>();
        for (StudentRecord student : studentsResult.getData()) {
            studentsById.put(student.getStudentId(), student);
        }
        List<TeachingRosterEntry> roster = new ArrayList<TeachingRosterEntry>();
        for (CourseSelectionRecord record : recordsResult.getData()) {
            StudentRecord student = studentsById.get(record.getStudentId());
            if (student == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "student profile for active selection was not found");
            }
            roster.add(new TeachingRosterEntry(student.getStudentId(), student.getName(),
                    student.getMajorName(), student.getClassId(), record.getSelectionType()));
        }
        return ServiceResult.ok(new TeachingRoster(new TeachingOffering(offering.getData(),
                course.getData()), roster));
    }

    /** 课程目录和教学班管理仅允许拥有 COURSE_MANAGE 权限的教务人员使用。 */
    private ServiceResult<?> manage(CourseManagementCommand command) {
        ServiceResult<Void> authorization = authorizeCourseManager(command.getToken());
        if (authorization.getStatus() != StatusCode.OK) {
            return authorization;
        }
        switch (command.getOperation()) {
            case LIST_COURSES:
                return catalog == null ? managementServiceUnavailable() : catalog.listAll();
            case LIST_OFFERINGS_BY_TERM:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.listByTerm(command.getTerm());
            case CREATE_COURSE:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.create(command.getCourse());
            case UPDATE_COURSE_DETAILS:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.updateDetails(command.getTargetId(), command.getName(),
                                command.getCredits());
            case CHANGE_COURSE_STATUS:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.changeStatus(command.getTargetId(), command.getCourseStatus());
            case CREATE_OFFERING:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.create(command.getOffering());
            case CHANGE_OFFERING_STATUS:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.changeStatus(command.getTargetId(), command.getOfferingStatus());
            case CHANGE_OFFERING_CAPACITIES:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.changeCapacities(command.getTargetId(),
                                command.getRequiredCapacity(), command.getElectiveCapacity(),
                                command.getCrossMajorCapacity());
            case UPDATE_OFFERING_TEACHING_INFO:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.updateTeachingInfo(command.getTargetId(), command.getTeacherId(),
                                command.getLocation());
            case LIST_SELECTION_ROUNDS_BY_TERM:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.listByTerm(command.getTerm());
            case CREATE_SELECTION_ROUND:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.create(command.getSelectionRound());
            case UPDATE_SELECTION_ROUND_TIME_WINDOW:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.updateTimeWindow(command.getTargetId(), command.getStartsAt(),
                                command.getEndsAt());
            case CHANGE_SELECTION_ROUND_STATUS:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.changeStatus(command.getTargetId(),
                                command.getSelectionRoundStatus());
            default:
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "unsupported management operation");
        }
    }

    private static ServiceResult<Void> managementServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "requested course management service is not configured");
    }

    private static ServiceResult<Void> teachingServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "teacher teaching query service is not configured");
    }

    private ServiceResult<StudentSelectionProfile> profile(String token, Permission permission) {
        ServiceResult<Boolean> authorized = users.authorize(token, permission.getCode());
        if (authorized.getStatus() != StatusCode.OK) return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return ServiceResult.failure(session.getStatus(), session.getMessage());
        if (session.getData().getUser().getRole() != Role.STUDENT) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "only student can use student course selection");
        }
        return profiles.findByUserId(session.getData().getUser().getUserId());
    }

    private ServiceResult<TeacherProfile> teacherProfile(String token) {
        if (teachers == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND,
                    "teacher teaching query service is not configured");
        }
        ServiceResult<Boolean> authorized = users.authorize(token, Permission.GRADE_WRITE.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(session.getStatus(), session.getMessage());
        }
        if (session.getData().getUser().getRole() != Role.TEACHER) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "only teacher can query teaching offerings and rosters");
        }
        ServiceResult<TeacherProfile> profile = teachers.findByUserId(
                session.getData().getUser().getUserId());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (!profile.getData().isActive()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "teacher profile is inactive");
        }
        return profile;
    }

    private ServiceResult<Void> authorizeCourseManager(String token) {
        ServiceResult<Boolean> authorized = users.authorize(token, Permission.COURSE_MANAGE.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(session.getStatus(), session.getMessage());
        }
        return ServiceResult.ok(null);
    }

    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }
}
