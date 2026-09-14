package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryBorrowPolicy;
import cn.vcampus.library.LibraryCopyStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static cn.vcampus.server.LibraryAccessSql.*;

/** 借还事务实现；共享仓库入口必须在持有图书锁时委托到此处。 */
final class LibraryAccessCirculation {
    /** 预占实体册、扣库存、写借阅及名称快照，一个事务内全部成功或全部回滚。 */
    static ServiceResult<List<BorrowRecord>> borrowBatch(AccessLibraryRepository repository, int borrowLimit,
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        Connection connection = null;
        try {
            connection = repository.open();
            connection.setAutoCommit(false);
            boolean trackCopies = LibraryCopySchema.available(connection);
            if (!LibraryBorrowPolicy.allows(borrowLimit, activeBorrowCount(connection, userId), bookIds.size())) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.CONFLICT, LibraryBorrowPolicy.exceededMessage(borrowLimit));
            }
            for (String bookId : bookIds) {
                Book book = LibraryAccessSql.findBook(connection, bookId);
                if (book == null) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bookId);
                }
                if (book.getAvailableCopies() <= 0) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bookId);
                }
                if (hasActiveBorrow(connection, userId, bookId)) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed: " + bookId);
                }
                if (trackCopies) LibraryCopySchema.initializeBook(connection, bookId);
            }

            String orderId = id("BO");
            List<BorrowRecord> created = new ArrayList<BorrowRecord>();
            for (String bookId : bookIds) {
                String copyId = trackCopies ? LibraryCopySql.reserve(connection, bookId) : null;
                if (!decrementAvailable(connection, bookId)) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "book inventory changed: " + bookId);
                }
                BorrowRecord record = new BorrowRecord(orderId, id("BR"), userId, bookId,
                        borrowDate, dueDate, null, BorrowStatus.BORROWED);
                insertBorrowRecord(connection, record);
                if (trackCopies) LibraryCopySql.insertSnapshot(connection, record.getRecordId(),
                        LibraryAccessSql.findBook(connection, bookId).getTitle(), copyId, false);
                created.add(record);
            }
            connection.commit();
            return ServiceResult.ok(created);
        } catch (SQLException | RuntimeException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "library borrow transaction failed");
        } finally {
            close(connection);
        }
    }
    /** 精确归还绑定实体册，同时恢复库存和借阅状态；重复归还不能重复入库。 */
    static ServiceResult<BorrowRecord> returnBook(AccessLibraryRepository repository,
            String userId, String recordId, LocalDate returnDate) {
        Connection connection = null;
        try {
            connection = repository.open();
            connection.setAutoCommit(false);
            BorrowRecord active = findActiveRecord(connection, userId, recordId);
            if (active == null) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.NOT_FOUND, "active borrowing record not found");
            }
            boolean trackCopies = LibraryCopySchema.available(connection);
            if (trackCopies) LibraryCopySchema.initializeBook(connection, active.getBookId());
            if (!markReturned(connection, recordId, returnDate) || !incrementAvailable(connection, active.getBookId())) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.CONFLICT, "library return transaction failed");
            }
            if (trackCopies) LibraryCopySql.finish(connection, recordId, LibraryCopyStatus.AVAILABLE);
            connection.commit();
            return ServiceResult.ok(active.returned(returnDate));
        } catch (SQLException | RuntimeException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "library return transaction failed");
        } finally {
            close(connection);
        }
    }
}
