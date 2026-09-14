package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.LibraryBookUpdateV4Command;
import cn.vcampus.library.LibraryCatalogV4;
import cn.vcampus.library.LibraryCopiesV4Command;
import cn.vcampus.library.LibraryHistoryV4Command;
import cn.vcampus.library.LibraryService;
import cn.vcampus.library.LibraryV4Delegation;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** V4 独立授权适配器；不改变已有 V2/V3 载荷或从客户端接收真实操作者编号。 */
final class LibraryV4MessageHandler {
    /** 可选的显式扩展业务实现。 */
    private final LibraryCatalogV4 service;
    /** 当前服务器共用的会话及授权服务。 */
    private final UserManagementService users;
    /** 注入既有服务，缺少 V4 实现时返回配套升级提示。 */
    LibraryV4MessageHandler(LibraryService library, UserManagementService users) {
        this.service = library instanceof LibraryCatalogV4 ? (LibraryCatalogV4) library : null;
        this.users = users;
    }
    /** 识别三类新消息，所有受限操作均先校验会话和权限。 */
    ServiceResult<?> handle(Message request) {
        switch (request.getType()) {
            case LIBRARY_COPIES_V4:
                LibraryCopiesV4Command copies = payload(request, LibraryCopiesV4Command.class);
                return authorized(copies.getToken(), Permission.LIBRARY_MANAGE,
                        session -> service.copies(copies.getBookId()));
            case LIBRARY_BOOK_UPDATE_V4:
                LibraryBookUpdateV4Command edit = payload(request, LibraryBookUpdateV4Command.class);
                return authorized(edit.getToken(), Permission.LIBRARY_MANAGE,
                        session -> service.updateBook(session.getUser().getUserId(), edit.getExpected(), edit.getReplacement()));
            case LIBRARY_HISTORY_V4:
                return history(payload(request, LibraryHistoryV4Command.class));
            default:
                return ServiceResult.failure(StatusCode.NOT_FOUND, "unsupported library V4 message");
        }
    }
    /** 本人查询只需读取权限；任何他人或全校范围均须管理权限。 */
    private ServiceResult<?> history(LibraryHistoryV4Command command) {
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) return session;
        String own = session.getData().getUser().getUserId();
        String target = command.getTargetUserId();
        boolean self = target == null || target.trim().isEmpty() || own.equals(target.trim());
        return authorized(command.getToken(), command.isAllUsers() || !self ? Permission.LIBRARY_MANAGE : Permission.LIBRARY_READ,
                current -> service.loanSnapshots(command.isAllUsers() ? null : self ? own : target.trim()));
    }
    /** 服务端权限通过后重新取真实会话；不信任表单角色或请求中的目标用户。 */
    private ServiceResult<?> authorized(String token, Permission permission, Action action) {
        ServiceResult<Boolean> allowed = users.authorize(token, permission.getCode());
        if (allowed.getStatus() != StatusCode.OK) return allowed;
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return session;
        return service == null ? LibraryV4Delegation.unavailable() : action.run(session.getData());
    }
    /** 校验载荷真实类型，让外层处理器统一返回 BAD_REQUEST。 */
    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }
    /** 只接收已认证的服务器会话。 */
    private interface Action {
        /** 执行通过授权的本地操作。 */
        ServiceResult<?> run(Session session);
    }
}
