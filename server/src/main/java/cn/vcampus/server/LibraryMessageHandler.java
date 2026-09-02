package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.LibraryAddBookV2Command;
import cn.vcampus.library.LibraryBorrowV2Command;
import cn.vcampus.library.LibraryDetailV2Command;
import cn.vcampus.library.LibraryHistoryV2Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.library.LibraryService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;
import java.util.List;

/** Token-authenticated adapter for the versioned library protocol. */
final class LibraryMessageHandler {
    private final LibraryService library;
    private final UserManagementService users;

    LibraryMessageHandler(LibraryService library, UserManagementService users) {
        if (library == null || users == null) throw new IllegalArgumentException("services must not be null");
        this.library = library;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.LIBRARY_QUERY_V2, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case LIBRARY_QUERY_V2:
                    LibraryQueryV2Command query = payload(request, LibraryQueryV2Command.class);
                    result = withPermission(query.getToken(), Permission.LIBRARY_READ,
                            () -> library.search(query.getKeyword(), query.getCategory()));
                    break;
                case LIBRARY_DETAIL_V2:
                    LibraryDetailV2Command detail = payload(request, LibraryDetailV2Command.class);
                    result = withPermission(detail.getToken(), Permission.LIBRARY_READ,
                            () -> library.getBook(detail.getBookId()));
                    break;
                case LIBRARY_BORROW_V2:
                    LibraryBorrowV2Command borrow = payload(request, LibraryBorrowV2Command.class);
                    result = withCurrentUser(borrow.getToken(), Permission.LIBRARY_BORROW,
                            userId -> library.borrowBatch(userId, borrow.getBookIds()));
                    break;
                case LIBRARY_RETURN_V2:
                    LibraryReturnV2Command returned = payload(request, LibraryReturnV2Command.class);
                    result = returnBook(returned);
                    break;
                case LIBRARY_HISTORY_V2:
                    result = history(payload(request, LibraryHistoryV2Command.class));
                    break;
                case LIBRARY_ADD_BOOK_V2:
                    LibraryAddBookV2Command add = payload(request, LibraryAddBookV2Command.class);
                    result = withPermission(add.getToken(), Permission.LIBRARY_MANAGE,
                            () -> library.addBook(add.getBook()));
                    break;
                default:
                    result = ServiceResult.failure(StatusCode.NOT_FOUND,
                            "library handler does not support this message type");
            }
            return response(request, result);
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        } catch (RuntimeException unexpected) {
            return Message.response(request, StatusCode.SERVER_ERROR, "library request failed");
        }
    }

    private ServiceResult<?> history(LibraryHistoryV2Command command) {
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) return session;
        String currentUserId = session.getData().getUser().getUserId();
        String targetUserId = command.getTargetUserId();
        boolean own = targetUserId == null || targetUserId.isEmpty() || targetUserId.equals(currentUserId);
        if (!command.isAllUsers() && own) {
            return withPermission(command.getToken(), Permission.LIBRARY_READ,
                    () -> library.borrowHistory(currentUserId));
        }
        return withPermission(command.getToken(), Permission.LIBRARY_MANAGE,
                () -> command.isAllUsers() ? library.allBorrowHistory() : library.borrowHistory(targetUserId));
    }

    private ServiceResult<?> returnBook(LibraryReturnV2Command command) {
        ServiceResult<Boolean> borrowAuthorization = users.authorize(
                command.getToken(), Permission.LIBRARY_BORROW.getCode());
        if (borrowAuthorization.getStatus() != StatusCode.OK) return borrowAuthorization;
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) return session;

        ServiceResult<List<BorrowRecord>> history = library.allBorrowHistory();
        if (history.getStatus() != StatusCode.OK) return history;
        BorrowRecord target = null;
        for (BorrowRecord record : history.getData()) {
            if (record.getRecordId().equals(command.getRecordId())) {
                target = record;
                break;
            }
        }
        if (target == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "borrow record not found");
        }

        String currentUserId = session.getData().getUser().getUserId();
        if (target.getUserId().equals(currentUserId)) {
            return library.returnBook(currentUserId, target.getRecordId());
        }
        ServiceResult<Boolean> manageAuthorization = users.authorize(
                command.getToken(), Permission.LIBRARY_MANAGE.getCode());
        if (manageAuthorization.getStatus() != StatusCode.OK) return manageAuthorization;
        return library.returnBook(target.getUserId(), target.getRecordId());
    }

    private ServiceResult<?> withPermission(String token, Permission permission, ResultSupplier action) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        return authorization.getStatus() == StatusCode.OK ? action.get() : authorization;
    }

    private ServiceResult<?> withCurrentUser(
            String token, Permission permission, UserResultSupplier action) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        if (authorization.getStatus() != StatusCode.OK) return authorization;
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return session;
        return action.get(session.getData().getUser().getUserId());
    }

    private static Message response(Message request, ServiceResult<?> result) {
        Object payload = result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage();
        return Message.response(request, result.getStatus(), payload);
    }

    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }

    private interface ResultSupplier { ServiceResult<?> get(); }
    private interface UserResultSupplier { ServiceResult<?> get(String userId); }
}
