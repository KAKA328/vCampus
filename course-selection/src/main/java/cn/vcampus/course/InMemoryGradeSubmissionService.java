package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 供开发和测试使用的内存成绩草稿服务，程序重启后数据会丢失。 */
public final class InMemoryGradeSubmissionService implements GradeSubmissionService {
    private final Map<String, GradeSubmission> submissions =
            new LinkedHashMap<String, GradeSubmission>();
    private final Map<String, Map<String, GradeEntry>> entriesBySubmission =
            new LinkedHashMap<String, Map<String, GradeEntry>>();

    @Override
    public synchronized ServiceResult<GradeSubmission> createDraft(GradeSubmission submission) {
        if (submission == null || submission.getStatus() != GradeSubmissionStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "only a draft grade submission can be created");
        }
        if (submissions.containsKey(submission.getSubmissionId())
                || findExistingByOffering(submission.getOfferingId()) != null) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "a grade submission already exists for this offering");
        }
        submissions.put(submission.getSubmissionId(), submission);
        entriesBySubmission.put(submission.getSubmissionId(),
                new LinkedHashMap<String, GradeEntry>());
        return ServiceResult.ok(submission);
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> findById(String submissionId) {
        String normalized = normalize(submissionId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "submissionId must not be blank");
        GradeSubmission submission = submissions.get(normalized);
        return submission == null ? ServiceResult.<GradeSubmission>failure(StatusCode.NOT_FOUND,
                "grade submission not found") : ServiceResult.ok(submission);
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> findByOffering(String offeringId) {
        String normalized = normalize(offeringId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "offeringId must not be blank");
        GradeSubmission submission = findExistingByOffering(normalized);
        return submission == null ? ServiceResult.<GradeSubmission>failure(StatusCode.NOT_FOUND,
                "grade submission not found") : ServiceResult.ok(submission);
    }

    @Override
    public synchronized ServiceResult<List<GradeSubmission>> listByStatus(
            GradeSubmissionStatus status) {
        if (status == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "status must not be null");
        List<GradeSubmission> result = new ArrayList<GradeSubmission>();
        for (GradeSubmission submission : submissions.values()) {
            if (submission.getStatus() == status) result.add(submission);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    @Override
    public synchronized ServiceResult<List<GradeEntry>> listEntries(String submissionId) {
        ServiceResult<GradeSubmission> submission = findById(submissionId);
        if (submission.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submission.getStatus(), submission.getMessage());
        }
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<GradeEntry>(
                entriesBySubmission.get(submission.getData().getSubmissionId()).values())));
    }

    @Override
    public synchronized ServiceResult<GradeEntry> saveDraftEntry(GradeEntry entry) {
        ServiceResult<List<GradeEntry>> saved = saveDraftEntries(Collections.singletonList(entry));
        return saved.getStatus() == StatusCode.OK ? ServiceResult.ok(saved.getData().get(0))
                : ServiceResult.<GradeEntry>failure(saved.getStatus(), saved.getMessage());
    }

    @Override
    public synchronized ServiceResult<List<GradeEntry>> saveDraftEntries(List<GradeEntry> entries) {
        if (entries == null || entries.isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "grade entries must not be empty");
        String submissionId = null;
        Map<String, GradeEntry> replacement = new LinkedHashMap<String, GradeEntry>();
        for (GradeEntry entry : entries) {
            if (entry == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "grade entry must not be null");
            if (submissionId == null) submissionId = entry.getSubmissionId();
            if (!submissionId.equals(entry.getSubmissionId())
                    || replacement.put(entry.getStudentId(), entry) != null) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST,
                        "grade batch must contain unique students from one submission");
            }
        }
        ServiceResult<GradeSubmission> submissionResult = findById(submissionId);
        if (submissionResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submissionResult.getStatus(), submissionResult.getMessage());
        }
        GradeSubmission submission = submissionResult.getData();
        if (submission.getStatus() != GradeSubmissionStatus.DRAFT
                && submission.getStatus() != GradeSubmissionStatus.RETURNED
                && submission.getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "approved grade entries cannot be changed until they are returned");
        }
        entriesBySubmission.get(submissionId).putAll(replacement);
        submissions.put(submissionId, submission.withUpdatedAt(LocalDateTime.now()));
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<GradeEntry>(entries)));
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> submitForReview(String submissionId) {
        ServiceResult<GradeSubmission> submissionResult = findById(submissionId);
        if (submissionResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submissionResult.getStatus(), submissionResult.getMessage());
        }
        GradeSubmission submission = submissionResult.getData();
        if (submission.getStatus() == GradeSubmissionStatus.APPROVED) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "approved grade submission must be returned before it can be changed");
        }
        GradeSubmission pending = submission.pendingReview(LocalDateTime.now());
        submissions.put(pending.getSubmissionId(), pending);
        return ServiceResult.ok(pending);
    }

    @Override
    public synchronized ServiceResult<GradeSubmission> review(String submissionId,
            GradeReviewDecision decision, String reviewerId, String remark) {
        ServiceResult<GradeSubmission> found = findById(submissionId);
        if (found.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(found.getStatus(), found.getMessage());
        }
        if (decision == null || normalize(reviewerId) == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "decision and reviewerId must not be blank");
        }
        if (found.getData().getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending grade submissions can be reviewed");
        }
        if (decision == GradeReviewDecision.RETURN && normalize(remark) == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "a return remark is required");
        }
        GradeSubmissionStatus status = decision == GradeReviewDecision.APPROVE
                ? GradeSubmissionStatus.APPROVED : GradeSubmissionStatus.RETURNED;
        GradeSubmission reviewed = found.getData().reviewed(status, reviewerId, remark,
                LocalDateTime.now());
        submissions.put(reviewed.getSubmissionId(), reviewed);
        return ServiceResult.ok(reviewed);
    }

    private GradeSubmission findExistingByOffering(String offeringId) {
        for (GradeSubmission submission : submissions.values()) {
            if (offeringId.equals(submission.getOfferingId())) return submission;
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
