package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.library.LibraryService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;
import java.util.List;

/** 借阅历史的本人/管理员权限分流、旧协议兼容与管理员代还。 */
final class LibraryCirculationRequests {

    /** 共享图书仓库或业务服务。 */
    private final LibraryService library;
    /** 用户会话与授权服务。 */
    private final UserManagementService users;

    /** 注入同一套业务和会话服务，不维护独立权限缓存。 */
    LibraryCirculationRequests(LibraryService library, UserManagementService users) {
        this.library = library;
        this.users = users;
    }

    /** 根据授权范围读取本人或全部赔偿／借阅记录。 */
    ServiceResult<?> history(String token, String targetUserId, boolean allUsers) {
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return session;
        String currentUserId = session.getData().getUser().getUserId();
        boolean own = targetUserId == null || targetUserId.isEmpty() || targetUserId.equals(currentUserId);
        if (!allUsers && own) {
            return withPermission(token, Permission.LIBRARY_READ,
                    () -> library.borrowHistory(currentUserId));
        }
        return withPermission(token, Permission.LIBRARY_MANAGE,
                () -> allUsers ? library.allBorrowHistory() : library.borrowHistory(targetUserId));
    }

    /** 旧客户端遇到新增生命周期状态时返回升级提示，避免反序列化异常。 */
    ServiceResult<?> legacyHistory(ServiceResult<?> result) {
        if (result.getStatus() == StatusCode.OK && result.getData() instanceof List<?>) {
            for (Object value : (List<?>) result.getData()) {
                BorrowStatus status = ((BorrowRecord) value).getStatus();
                if (status != BorrowStatus.BORROWED && status != BorrowStatus.RETURNED) {
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "借阅记录包含遗失赔偿状态，请升级客户端后查看");
                }
            }
        }
        return result;
    }

    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    ServiceResult<?> returnBook(LibraryReturnV2Command command) {
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

    /** 通过服务端权限校验后才执行本地业务操作。 */
    ServiceResult<?> withPermission(String token, Permission permission, ResultSupplier action) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        return authorization.getStatus() == StatusCode.OK ? action.get() : authorization;
    }

    /** 通过权限检查后执行的本地业务调用。 */
    private interface ResultSupplier {
        /** 执行已通过权限检查的业务操作。 */
        ServiceResult<?> get();
    }
}
