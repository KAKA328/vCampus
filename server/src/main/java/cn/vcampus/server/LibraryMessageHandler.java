package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRequest;
import cn.vcampus.library.LibraryService;

/** Adapts library service results to the shared Socket message protocol. */
final class LibraryMessageHandler {
    private final LibraryService service;

    LibraryMessageHandler(LibraryService service) {
        this.service = service;
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
                    result = service.search(payload(request, String.class));
                    break;
                case LIBRARY_DETAIL:
                    result = service.getBook(payload(request, String.class));
                    break;
                case LIBRARY_CATEGORY:
                    result = service.listByCategory(payload(request, String.class));
                    break;
                case LIBRARY_ADD_BOOK:
                    result = service.addBook(payload(request, Book.class));
                    break;
                case LIBRARY_BORROW:
                    BorrowRequest borrow = payload(request, BorrowRequest.class);
                    result = service.borrow(borrow.getStudentId(), borrow.getBookId());
                    break;
                case LIBRARY_RETURN:
                    BorrowRequest returnRequest = payload(request, BorrowRequest.class);
                    result = service.returnBook(returnRequest.getStudentId(), returnRequest.getBookId());
                    break;
                case LIBRARY_HISTORY:
                    result = service.borrowHistory(payload(request, String.class));
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

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}
