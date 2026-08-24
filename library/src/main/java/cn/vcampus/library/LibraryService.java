package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Library inventory and borrowing contract. */
public interface LibraryService {
    ServiceResult<List<Book>> search(String keyword);
    ServiceResult<Void> borrow(String studentId, String bookId);
    ServiceResult<Void> returnBook(String studentId, String bookId);
}
