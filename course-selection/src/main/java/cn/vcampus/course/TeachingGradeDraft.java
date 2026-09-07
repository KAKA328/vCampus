package cn.vcampus.course;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 教师打开成绩草稿时返回的教学班有效名单、提交单信息及当前已录入成绩。 */
public final class TeachingGradeDraft implements Serializable {
    private static final long serialVersionUID = 1L;

    private final TeachingRoster roster;
    private final GradeSubmission submission;
    private final List<GradeEntry> entries;
    private final Integer reviewVersionNo;

    public TeachingGradeDraft(TeachingRoster roster, GradeSubmission submission,
            List<GradeEntry> entries) {
        if (roster == null || submission == null || entries == null) {
            throw new IllegalArgumentException("roster, submission and entries must not be null");
        }
        this.roster = roster;
        this.submission = submission;
        this.entries = Collections.unmodifiableList(new ArrayList<GradeEntry>(entries));
        this.reviewVersionNo = null;
    }

    /** 教务查看时附带实际审核的提交快照版本；教师草稿视图为 null。 */
    public TeachingGradeDraft(TeachingRoster roster, GradeSubmission submission,
            GradeReviewSnapshot snapshot) {
        this.roster = roster;
        this.submission = submission;
        this.entries = snapshot.getEntries();
        this.reviewVersionNo = Integer.valueOf(snapshot.getVersionNo());
    }

    public TeachingRoster getRoster() { return roster; }
    public GradeSubmission getSubmission() { return submission; }
    public List<GradeEntry> getEntries() { return entries; }
    public Integer getReviewVersionNo() { return reviewVersionNo; }
}
