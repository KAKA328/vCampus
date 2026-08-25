package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Library catalog and borrowing contract. */
public interface LibraryService {
    /** Searches the catalog by title, author, ISBN, category or book id (case-insensitive). */
    ServiceResult<List<Book>> search(String keyword);

    /** Returns the full detail of one book, or NOT_FOUND when it does not exist. */
    ServiceResult<Book> getBook(String bookId);

    /** Lists all books in a category (case-insensitive). */
    ServiceResult<List<Book>> listByCategory(String category);

    /** Adds a new book to the catalog (librarian/admin operation). */
    ServiceResult<Void> addBook(Book book);

    /** Borrows one available copy for a student. */
    ServiceResult<Void> borrow(String studentId, String bookId);

    /** Returns an active borrowed copy. */
    ServiceResult<Void> returnBook(String studentId, String bookId);

    /** Returns the full borrowing history of a student. */
    ServiceResult<List<BorrowRecord>> borrowHistory(String studentId);
}
