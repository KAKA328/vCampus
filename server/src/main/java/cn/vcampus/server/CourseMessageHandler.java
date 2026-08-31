package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseDropCommand;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;
import java.time.LocalDateTime;

/** 将当前选课流程转换为 Socket 消息；学生资料仅由服务器按 token 查询。 */
final class CourseMessageHandler {
    private final CourseSelectionService courses;
    private final CourseCatalogService catalog;
    private final CourseOfferingService offerings;
    private final StudentSelectionProfileProvider profiles;
    private final UserManagementService users;

    CourseMessageHandler(CourseSelectionService courses, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, null, null, profiles, users);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        if (courses == null || profiles == null || users == null) {
            throw new IllegalArgumentException("course handler dependencies must not be null");
        }
        this.courses = courses;
        this.catalog = catalog;
        this.offerings = offerings;
        this.profiles = profiles;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.COURSE_QUERY, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case COURSE_QUERY:
                    result = query(payload(request, CourseQueryCommand.class));
                    break;
                case COURSE_SELECT:
                    CourseSelectionCommand select = payload(request, CourseSelectionCommand.class);
                    result = select(select);
                    break;
                case COURSE_DROP:
                    result = drop(payload(request, CourseDropCommand.class));
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

    private ServiceResult<?> query(CourseQueryCommand command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_READ);
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (command.getQueryType() == CourseQueryCommand.QueryType.AVAILABLE_ROUNDS) {
            return courses.listAvailableRounds(profile.getData(), LocalDateTime.now());
        }
        if (command.getQueryType() == CourseQueryCommand.QueryType.AVAILABLE_OFFERINGS) {
            return courses.listAvailableOfferings(profile.getData(), command.getRoundId(),
                    LocalDateTime.now());
        }
        return courses.listSelectedOfferings(profile.getData());
    }

    private ServiceResult<?> select(CourseSelectionCommand command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.select(profile.getData(), command.getRoundId(), command.getOfferingId(),
                        LocalDateTime.now()) : profile;
    }

    private ServiceResult<?> drop(CourseDropCommand command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.drop(profile.getData(), command.getRecordId(), LocalDateTime.now()) : profile;
    }

    /** 课程目录和教学班管理仅允许拥有 COURSE_MANAGE 权限的教务人员使用。 */
    private ServiceResult<?> manage(CourseManagementCommand command) {
        ServiceResult<Void> authorization = authorizeCourseManager(command.getToken());
        if (authorization.getStatus() != StatusCode.OK) {
            return authorization;
        }
        if (catalog == null || offerings == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND,
                    "course management services are not configured");
        }
        switch (command.getOperation()) {
            case LIST_COURSES:
                return catalog.listAll();
            case LIST_OFFERINGS_BY_TERM:
                return offerings.listByTerm(command.getTerm());
            case CREATE_COURSE:
                return catalog.create(command.getCourse());
            case UPDATE_COURSE_DETAILS:
                return catalog.updateDetails(command.getTargetId(), command.getName(),
                        command.getCredits());
            case CHANGE_COURSE_STATUS:
                return catalog.changeStatus(command.getTargetId(), command.getCourseStatus());
            case CREATE_OFFERING:
                return offerings.create(command.getOffering());
            case CHANGE_OFFERING_STATUS:
                return offerings.changeStatus(command.getTargetId(), command.getOfferingStatus());
            case CHANGE_OFFERING_CAPACITIES:
                return offerings.changeCapacities(command.getTargetId(),
                        command.getRequiredCapacity(), command.getElectiveCapacity(),
                        command.getCrossMajorCapacity());
            case UPDATE_OFFERING_TEACHING_INFO:
                return offerings.updateTeachingInfo(command.getTargetId(), command.getTeacherId(),
                        command.getLocation());
            default:
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "unsupported management operation");
        }
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
