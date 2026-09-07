package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory academic review implementation used before Access persistence is connected. */
public final class InMemoryAcademicReviewService
        implements AcademicReviewService, CourseResultRecordingService {
    private final Map<String, List<CourseHistoryRecord>> historiesByStudentId = new LinkedHashMap<String, List<CourseHistoryRecord>>();
    private final Map<String, AcademicReview> latestReviewsByStudentId = new LinkedHashMap<String, AcademicReview>();
    private final Map<String, FormalCourseResult> formalResultsById =
            new LinkedHashMap<String, FormalCourseResult>();
    private final Map<String, CourseHistoryRecord> formalHistoriesByResultId =
            new LinkedHashMap<String, CourseHistoryRecord>();

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
        CreditSummary summary = CreditSummary.from(normalizedStudentId, records);
        int totalEarnedCredits = summary.getEarnedCredits();
        int passedCourseCount = summary.getPassedCourses();
        int failedCourseCount = summary.getPendingRetakes();
        int retakeCourseCount = summary.getHistoricalRetakes();

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

    @Override
    public synchronized ServiceResult<Integer> nextAttemptNo(String studentId, String courseId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedStudentId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "studentId and courseId must not be blank");
        }
        int maxAttempt = 0;
        List<CourseHistoryRecord> history = historiesByStudentId.get(normalizedStudentId);
        if (history != null) {
            for (CourseHistoryRecord record : history) {
                if (normalizedCourseId.equals(record.getCourseId())) {
                    maxAttempt = Math.max(maxAttempt, record.getAttemptNo());
                }
            }
        }
        return ServiceResult.ok(Integer.valueOf(maxAttempt + 1));
    }

    @Override
    public synchronized ServiceResult<Void> recordAll(List<FormalCourseResult> results) {
        if (results == null || results.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "results must not be empty");
        }
        for (FormalCourseResult result : results) {
            if (result == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "result must not be null");
            if (formalResultsById.containsKey(result.getResultId())) {
                return ServiceResult.failure(StatusCode.CONFLICT, "formal course result already exists");
            }
        }
        for (FormalCourseResult result : results) {
            formalResultsById.put(result.getResultId(), result);
            CourseHistoryRecord history = result.toHistoryRecord(null);
            formalHistoriesByResultId.put(result.getResultId(), history);
            addHistory(history);
        }
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<Void> retractAll(List<FormalCourseResult> results) {
        if (results == null || results.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "results must not be empty");
        }
        for (FormalCourseResult result : results) {
            if (result == null || !formalResultsById.containsKey(result.getResultId())) {
                return ServiceResult.failure(StatusCode.CONFLICT,
                        "formal course result does not exist");
            }
        }
        for (FormalCourseResult result : results) {
            formalResultsById.remove(result.getResultId());
            removeGeneratedHistory(result.getStudentId(),
                    formalHistoriesByResultId.remove(result.getResultId()));
        }
        return ServiceResult.ok(null);
    }

    /** 仅移除由正式成绩写入生成的那条历史记录，保留其他来源的学业历史。 */
    private void removeGeneratedHistory(String studentId, CourseHistoryRecord generated) {
        if (generated == null) return;
        List<CourseHistoryRecord> records = historiesByStudentId.get(studentId);
        if (records == null) return;
        records.remove(generated);
        if (records.isEmpty()) historiesByStudentId.remove(studentId);
    }

    private static final class CourseSummary {
        private boolean passed;
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
