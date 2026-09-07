package cn.vcampus.course;

/** 成绩文件中一行已通过格式校验的数据，行号用于指出文件问题。 */
public final class GradeImportRow {
    private final int rowNumber;
    private final String studentId;
    private final int score;

    public GradeImportRow(int rowNumber, String studentId, int score) {
        if (rowNumber < 2 || studentId == null || studentId.trim().isEmpty()
                || score < 0 || score > 100) {
            throw new IllegalArgumentException("invalid grade import row");
        }
        this.rowNumber = rowNumber;
        this.studentId = studentId.trim();
        this.score = score;
    }

    public int getRowNumber() { return rowNumber; }
    public String getStudentId() { return studentId; }
    public int getScore() { return score; }
}
