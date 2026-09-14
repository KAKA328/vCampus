package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** V4 借阅视图：原有记录加独立的书名快照与实体册编号，不改旧 BorrowRecord 的序列化契约。 */
public final class LibraryLoanSnapshot implements Serializable {
    /** 新 DTO 的独立序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 原有借阅记录，包含当前生命周期状态。 */
    private final BorrowRecord record;
    /** 借阅时保存的书名；历史数据只能以迁移当时的馆藏名称回填。 */
    private final String bookTitle;
    /** 实体册编号；旧的已归还记录无法还原时为 null。 */
    private final String copyId;
    /** 是否为旧记录回填，不能宣称是真实的借阅时名称。 */
    private final boolean historicalBackfill;

    /** 保存独立名称与副本关系，不查询当前馆藏名称覆盖快照。 */
    public LibraryLoanSnapshot(BorrowRecord record, String bookTitle, String copyId, boolean historicalBackfill) {
        this.record = Objects.requireNonNull(record, "record");
        this.bookTitle = LibraryCommandSupport.required(bookTitle, "bookTitle");
        this.copyId = copyId == null ? null : LibraryCommandSupport.required(copyId, "copyId");
        this.historicalBackfill = historicalBackfill;
    }
    /** 返回原有借阅生命周期记录。 */
    public BorrowRecord getRecord() { return record; }
    /** 返回保存的书名。 */
    public String getBookTitle() { return bookTitle; }
    /** 返回实体册编号，无法还原的旧记录为 null。 */
    public String getCopyId() { return copyId; }
    /** 判断是否为迁移回填。 */
    public boolean isHistoricalBackfill() { return historicalBackfill; }
    /** 只替换生命周期，永久保留书名和实体册关系。 */
    public LibraryLoanSnapshot withRecord(BorrowRecord value) {
        if (!record.getRecordId().equals(value.getRecordId())) throw new IllegalArgumentException("record mismatch");
        return new LibraryLoanSnapshot(value, bookTitle, copyId, historicalBackfill);
    }
}
