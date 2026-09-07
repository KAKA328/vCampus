package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 教师一次提交后冻结的成绩审核快照。 */
public final class GradeReviewSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String submissionId;
    private final int versionNo;
    private final LocalDateTime submittedAt;
    private final List<GradeEntry> entries;

    public GradeReviewSnapshot(String submissionId, int versionNo, LocalDateTime submittedAt,
            List<GradeEntry> entries) {
        if (submissionId == null || submissionId.trim().isEmpty() || versionNo < 1
                || submittedAt == null || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("invalid grade review snapshot");
        }
        this.submissionId = submissionId.trim();
        this.versionNo = versionNo;
        this.submittedAt = submittedAt;
        this.entries = Collections.unmodifiableList(new ArrayList<GradeEntry>(entries));
    }

    public String getSubmissionId() { return submissionId; }
    public int getVersionNo() { return versionNo; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public List<GradeEntry> getEntries() { return entries; }
}
