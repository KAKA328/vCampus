package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory academic review implementation used before Access persistence is connected. */
public final class InMemoryAcademicReviewService implements AcademicReviewService {
    private final Map<String, List<CourseHistoryRecord>> historiesByStudentId = new LinkedHashMap<String, List<CourseHistoryRecord>>();
    private final Map<String, AcademicReview> latestReviewsByStudentId = new LinkedHashMap<String, AcademicReview>();

    public synchronized ServiceResult<Void> addHistory(CourseHistoryRecord record) {
        if (record == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "record must not be null");
        }
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
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        List<CourseHistoryRecord> records = historiesByStudentId.get(normalizedStudentId);
        if (records == null) {
            return ServiceResult.ok(Collections.<CourseHistoryRecord>emptyList());
        }
        return ServiceResult.ok(new ArrayList<CourseHistoryRecord>(records));
    }

    @Override
    public synchronized ServiceResult<List<CourseHistoryRecord>> pendingRetakes(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
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

    @Override
    public synchronized ServiceResult<AcademicReview> review(String studentId, int requiredCredits) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        if (requiredCredits < 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "requiredCredits cannot be negative");
        }
        String normalizedStudentId = studentId.trim();
        List<CourseHistoryRecord> records = historyFor(normalizedStudentId).getData();
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
        AcademicReview review = new AcademicReview(null, normalizedStudentId,
                totalEarnedCredits, requiredCredits, passedCourseCount, failedCourseCount,
                retakeCourseCount, graduationReady, null, null, remark);
        latestReviewsByStudentId.put(normalizedStudentId, review);
        return ServiceResult.ok(review);
    }

    @Override
    public synchronized ServiceResult<AcademicReview> latestReview(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        AcademicReview review = latestReviewsByStudentId.get(studentId.trim());
        return review == null
                ? ServiceResult.<AcademicReview>failure(StatusCode.NOT_FOUND, "academic review not found")
                : ServiceResult.ok(review);
    }

    private static final class CourseSummary {
        private boolean seen;
        private boolean passed;
        private boolean retake;
        private int maxEarnedCredits;
        private CourseHistoryRecord latestFailed;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
