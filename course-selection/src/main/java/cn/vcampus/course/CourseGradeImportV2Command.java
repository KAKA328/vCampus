package cn.vcampus.course;

import java.io.Serializable;
import java.util.Locale;

/** 教师上传一个教学班成绩文件；文件内容仅包含学号和成绩。 */
public final class CourseGradeImportV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final String token;
    private final String offeringId;
    private final String fileName;
    private final byte[] content;

    public CourseGradeImportV2Command(String token, String offeringId, String fileName,
            byte[] content) {
        this.token = requireText(token, "token");
        this.offeringId = requireText(offeringId, "offeringId");
        this.fileName = requireText(fileName, "fileName");
        if (!isSupported(fileName)) {
            throw new IllegalArgumentException("only .csv, .xls and .xlsx grade files are supported");
        }
        if (content == null || content.length == 0 || content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("file content must be between 1 byte and 5 MB");
        }
        this.content = content.clone();
    }

    public String getToken() { return token; }
    public String getOfferingId() { return offeringId; }
    public String getFileName() { return fileName; }
    public byte[] getContent() { return content.clone(); }

    private static boolean isSupported(String fileName) {
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
