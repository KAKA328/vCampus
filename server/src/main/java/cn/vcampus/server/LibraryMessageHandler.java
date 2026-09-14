package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.LibraryCompensationService;
import cn.vcampus.library.LibraryHistoryV3Command;
import cn.vcampus.library.LibraryLossDeclareV3Command;
import cn.vcampus.library.LibraryCompensationListV3Command;
import cn.vcampus.library.LibraryCompensationPayV3Command;
import cn.vcampus.library.LibraryWalletQueryV3Command;
import cn.vcampus.library.LibraryAddBookV2Command;
import cn.vcampus.library.LibraryBorrowV2Command;
import cn.vcampus.library.LibraryDetailV2Command;
import cn.vcampus.library.LibraryHistoryV2Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.library.LibraryRestockV2Command;
import cn.vcampus.library.LibraryService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Token-authenticated adapter for the versioned library protocol. */
final class LibraryMessageHandler {
    /** 共享图书仓库或业务服务。 */
    private final LibraryService library;
    /** 借阅历史与管理员代还的授权处理器。 */
    private final LibraryCirculationRequests circulation;
    /** 用户会话与授权服务。 */
    private final UserManagementService users;
    /** 赔偿记录或赔偿服务。 */
    private final LibraryCompensationService compensations;
    /** 实体册、快照与资料编辑的显式版本化消息处理器。 */
    private final LibraryV4MessageHandler catalogV4;

    /** 创建仅处理图书馆基础业务的消息入口，未配置赔偿服务。 */
    LibraryMessageHandler(LibraryService library, UserManagementService users) {
        this(library, users, null);
    }

    /** 绑定基础业务、会话授权和可选赔偿服务，保持现有消息协议。 */
    LibraryMessageHandler(LibraryService library, UserManagementService users,
            LibraryCompensationService compensations) {
        if (library == null || users == null) throw new IllegalArgumentException("services must not be null");
        this.library = library;
        this.circulation = new LibraryCirculationRequests(library, users);
        this.users = users;
        this.compensations = compensations;
        this.catalogV4 = new LibraryV4MessageHandler(library, users);
    }

    /** 处理现有图书馆消息，校验载荷、会话及权限后执行业务。 */
    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.LIBRARY_QUERY_V2, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case LIBRARY_HISTORY_V4:
                case LIBRARY_COPIES_V4:
                case LIBRARY_BOOK_UPDATE_V4:
                    result = catalogV4.handle(request);
                    break;
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
                    result = circulation.returnBook(returned);
                    break;
                case LIBRARY_HISTORY_V2:
                    LibraryHistoryV2Command oldHistory = payload(request, LibraryHistoryV2Command.class);
                    result = circulation.legacyHistory(circulation.history(oldHistory.getToken(), oldHistory.getTargetUserId(),
                            oldHistory.isAllUsers()));
                    break;
                case LIBRARY_HISTORY_V3:
                    LibraryHistoryV3Command newHistory = payload(request, LibraryHistoryV3Command.class);
                    result = circulation.history(newHistory.getToken(), newHistory.getTargetUserId(),
                            newHistory.isAllUsers());
                    break;
                case LIBRARY_LOSS_DECLARE_V3:
                    LibraryLossDeclareV3Command loss = payload(request, LibraryLossDeclareV3Command.class);
                    result = withCurrentUser(loss.getToken(), Permission.LIBRARY_MANAGE,
                            userId -> compensations == null ? unavailable()
                                    : compensations.declareLoss(userId, loss.getRecordId()));
                    break;
                case LIBRARY_COMPENSATION_LIST_V3:
                    LibraryCompensationListV3Command list = payload(request, LibraryCompensationListV3Command.class);
                    result = withCurrentUser(list.getToken(),
                            list.isAllUsers() ? Permission.LIBRARY_MANAGE : Permission.LIBRARY_READ,
                            userId -> compensations == null ? unavailable()
                                    : compensations.history(list.isAllUsers() ? null : userId));
                    break;
                case LIBRARY_COMPENSATION_PAY_V3:
                    LibraryCompensationPayV3Command pay = payload(request, LibraryCompensationPayV3Command.class);
                    result = withCurrentUser(pay.getToken(), Permission.LIBRARY_BORROW,
                            userId -> compensations == null ? unavailable()
                                    : compensations.pay(userId, pay.getCompensationId()));
                    break;
                case LIBRARY_WALLET_QUERY_V3:
                    LibraryWalletQueryV3Command wallet = payload(request, LibraryWalletQueryV3Command.class);
                    result = withCurrentUser(wallet.getToken(), Permission.LIBRARY_READ,
                            userId -> compensations == null ? unavailable() : compensations.balance(userId));
                    break;
                case LIBRARY_ADD_BOOK_V2:
                    LibraryAddBookV2Command add = payload(request, LibraryAddBookV2Command.class);
                    result = withPermission(add.getToken(), Permission.LIBRARY_MANAGE,
                            () -> library.addBook(add.getBook()));
                    break;
                case LIBRARY_RESTOCK_V2:
                    LibraryRestockV2Command restock = payload(request, LibraryRestockV2Command.class);
                    result = withPermission(restock.getToken(), Permission.LIBRARY_MANAGE,
                            () -> library.restock(restock.getBookId(), restock.getCopies()));
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

    /** 返回赔偿服务未配置的明确提示。 */
    private static ServiceResult<?> unavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND, "图书赔偿服务未配置，请联系管理员");
    }

    /** 通过服务端权限校验后才执行本地业务操作。 */
    private ServiceResult<?> withPermission(String token, Permission permission, ResultSupplier action) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        return authorization.getStatus() == StatusCode.OK ? action.get() : authorization;
    }

    /** 校验权限并从有效会话取得真实用户编号后调用业务。 */
    private ServiceResult<?> withCurrentUser(
            String token, Permission permission, UserResultSupplier action) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        if (authorization.getStatus() != StatusCode.OK) return authorization;
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return session;
        return action.get(session.getData().getUser().getUserId());
    }

    /** 把业务结果映射为原有成功或失败消息。 */
    private static Message response(Message request, ServiceResult<?> result) {
        Object payload = result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage();
        return Message.response(request, result.getStatus(), payload);
    }

    /** 校验实际载荷类型，拒绝伪造或不兼容数据。 */
    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }

    /** 已通过权限检查的无用户参数操作。 */
    private interface ResultSupplier {
        /** 执行已通过权限检查的业务操作。 */
        ServiceResult<?> get();
    }
    /** 接收服务端会话真实用户编号的业务操作。 */
    private interface UserResultSupplier {
        /** 使用会话推导出的真实用户编号执行业务。 */
        ServiceResult<?> get(String userId);
    }
}
