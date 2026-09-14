package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** V4 实体册快照；不包含借阅人信息，编号一经创建不再复用。 */
public final class LibraryCopy implements Serializable {
    /** 本 DTO 的独立序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 全局唯一的实体册编号。 */
    private final String copyId;
    /** 所属馆藏条目编号。 */
    private final String bookId;
    /** 当前实体册状态。 */
    private final LibraryCopyStatus status;

    /** 校验编号及状态，构造不可变实体册。 */
    public LibraryCopy(String copyId, String bookId, LibraryCopyStatus status) {
        this.copyId = LibraryCommandSupport.required(copyId, "copyId");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
        this.status = Objects.requireNonNull(status, "status");
    }
    /** 返回实体册编号。 */
    public String getCopyId() { return copyId; }
    /** 返回馆藏条目编号。 */
    public String getBookId() { return bookId; }
    /** 返回实体册状态。 */
    public LibraryCopyStatus getStatus() { return status; }
    /** 创建同一册书的新状态，不修改旧快照。 */
    public LibraryCopy withStatus(LibraryCopyStatus value) { return new LibraryCopy(copyId, bookId, value); }
}
