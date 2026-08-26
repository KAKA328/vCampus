package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.LibraryCommand;
import cn.vcampus.library.LibraryQueryCommand;
import cn.vcampus.library.LibraryService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Converts library requests to shared Socket responses. */
final class LibraryMessageHandler {
    private final LibraryService library;
    private final UserManagementService users;

    LibraryMessageHandler(LibraryService library, UserManagementService users) {
        if (library == null || users == null) {
            throw new IllegalArgumentException("library and users must not be null");
        }
        this.library = library;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.LIBRARY_QUERY, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case LIBRARY_QUERY:
                    result = query(payload(request, LibraryQueryCommand.class));
                    break;
                case LIBRARY_BORROW:
                    result = borrow(payload(request, LibraryCommand.class));
                    break;
                case LIBRARY_RETURN:
                    result = returnBook(payload(request, LibraryCommand.class));
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "library handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(LibraryQueryCommand command) {
        ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.LIBRARY_READ.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        return library.search(command.getKeyword());
    }

    private ServiceResult<?> borrow(LibraryCommand command) {
        ServiceResult<Session> scope = authorizePatronScope(command.getToken(), command.getPatronId());
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        return library.borrow(command.getPatronId(), command.getBookId());
    }

    private ServiceResult<?> returnBook(LibraryCommand command) {
        ServiceResult<Session> scope = authorizePatronScope(command.getToken(), command.getPatronId());
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        return library.returnBook(command.getPatronId(), command.getBookId());
    }

    private ServiceResult<Session> authorizePatronScope(String token, String patronId) {
        ServiceResult<Boolean> authorization = users.authorize(token, Permission.LIBRARY_BORROW.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        ServiceResult<Session> current = users.currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return current;
        }
        Role role = current.getData().getUser().getRole();
        if (role == Role.ADMIN || role == Role.LIBRARIAN) {
            return current;
        }
        if ((role == Role.STUDENT || role == Role.TEACHER)
                && current.getData().getUser().getUserId().equals(patronId)) {
            return current;
        }
        return ServiceResult.failure(StatusCode.FORBIDDEN, "library patron scope denied");
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}
