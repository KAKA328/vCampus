package cn.vcampus.course;

import java.io.Serializable;

/** 成功导入后返回实际更新行数和最新成绩草稿。 */
public final class GradeImportResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int importedCount;
    private final TeachingGradeDraft draft;

    public GradeImportResult(int importedCount, TeachingGradeDraft draft) {
        if (importedCount < 1 || draft == null) {
            throw new IllegalArgumentException("importedCount and draft are invalid");
        }
        this.importedCount = importedCount;
        this.draft = draft;
    }

    public int getImportedCount() { return importedCount; }
    public TeachingGradeDraft getDraft() { return draft; }
}
