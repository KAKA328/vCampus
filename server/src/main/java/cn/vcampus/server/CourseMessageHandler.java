package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseDropCommand;
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
    private final StudentSelectionProfileProvider profiles;
    private final UserManagementService users;

    CourseMessageHandler(CourseSelectionService courses, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        if (courses == null || profiles == null || users == null) {
            throw new IllegalArgumentException("course handler dependencies must not be null");
        }
        this.courses = courses;
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

    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }
}
