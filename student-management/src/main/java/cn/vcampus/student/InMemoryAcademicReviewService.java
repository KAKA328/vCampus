package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory academic review implementation used before Access persistence is connected. */
public final class InMemoryAcademicReviewService implements AcademicReviewService {
    private final Map<String, List<CourseHistoryRecord>> historiesByStudentId = new LinkedHashMap<String, List<CourseHistoryRecord>>();

    public synchronized ServiceResult<Void> addHistory(CourseHistoryRecord record) {
        List<CourseHistoryRecord> records = historiesByStudentId.get(record.getStudentId());
        if (records == null) {
            records = new ArrayList<CourseHistoryRecord>();
            historiesByStudentId.put(record.getStudentId(), records);
        }
        records.add(record);
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<List<CourseHistoryRecord>> historyFor(String studentId) {
        List<CourseHistoryRecord> records = historiesByStudentId.get(studentId);
        if (records == null) {
            return ServiceResult.ok(Collections.<CourseHistoryRecord>emptyList());
        }
        return ServiceResult.ok(new ArrayList<CourseHistoryRecord>(records));
    }

    @Override
    public synchronized ServiceResult<List<CourseHistoryRecord>> pendingRetakes(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(cn.vcampus.common.StatusCode.BAD_REQUEST,
                    "studentId must not be blank");
        }
        Map<String, CourseSummary> summariesByCourseId = new LinkedHashMap<String, CourseSummary>();
        List<CourseHistoryRecord> records = historiesByStudentId.get(studentId.trim());
        if (records == null) {
            return ServiceResult.ok(Collections.<CourseHistoryRecord>emptyList());
        }
        for (CourseHistoryRecord record : records) {
            CourseSummary summary = summariesByCourseId.get(record.getCourseId());
            if (summary == null) {
                summary = new CourseSummary();
                summariesByCourseId.put(record.getCourseId(), summary);
            }
            summary.passed = summary.passed || record.isPassed();
            if (!record.isPassed() && (summary.latestFailed == null
                    || record.getAttemptNo() > summary.latestFailed.getAttemptNo()
                    || (record.getAttemptNo() == summary.latestFailed.getAttemptNo()
                    && record.getSemester().compareTo(summary.latestFailed.getSemester()) > 0))) {
                summary.latestFailed = record;
            }
        }
        List<CourseHistoryRecord> pending = new ArrayList<CourseHistoryRecord>();
        for (Map.Entry<String, CourseSummary> entry : summariesByCourseId.entrySet()) {
            if (!entry.getValue().passed && entry.getValue().latestFailed != null) {
                pending.add(entry.getValue().latestFailed);
            }
        }
        Collections.sort(pending, (left, right) -> left.getCourseId().compareTo(right.getCourseId()));
        return ServiceResult.ok(pending);
    }

    public synchronized ServiceResult<AcademicReview> review(String studentId, int requiredCredits) {
        List<CourseHistoryRecord> records = historyFor(studentId).getData();
        Map<String, CourseSummary> summariesByCourseId = new LinkedHashMap<String, CourseSummary>();

        for (CourseHistoryRecord record : records) {
            CourseSummary summary = summariesByCourseId.get(record.getCourseId());
            if (summary == null) {
                summary = new CourseSummary();
                summariesByCourseId.put(record.getCourseId(), summary);
            }
            summary.seen = true;
            summary.passed = summary.passed || record.isPassed();
            summary.maxEarnedCredits = Math.max(summary.maxEarnedCredits, record.getEarnedCredits());
            summary.retake = summary.retake || record.getAttemptNo() > 1 || "重修".equals(record.getAttemptType());
        }

        int totalEarnedCredits = 0;
        int passedCourseCount = 0;
        int failedCourseCount = 0;
        int retakeCourseCount = 0;
        for (CourseSummary summary : summariesByCourseId.values()) {
            if (summary.passed) {
                passedCourseCount++;
                totalEarnedCredits += summary.maxEarnedCredits;
            } else if (summary.seen) {
                failedCourseCount++;
            }
            if (summary.retake) {
                retakeCourseCount++;
            }
        }

        boolean graduationReady = totalEarnedCredits >= requiredCredits && failedCourseCount == 0;
        String remark = records.isEmpty() ? "暂无课程成绩记录" : (graduationReady ? "达到阶段学分要求" : "未达到阶段学分要求");
        return ServiceResult.ok(new AcademicReview(
                studentId,
                totalEarnedCredits,
                passedCourseCount,
                failedCourseCount,
                retakeCourseCount,
                graduationReady,
                remark
        ));
    }

    private static final class CourseSummary {
        private boolean seen;
        private boolean passed;
        private boolean retake;
        private int maxEarnedCredits;
        private CourseHistoryRecord latestFailed;
    }
}
