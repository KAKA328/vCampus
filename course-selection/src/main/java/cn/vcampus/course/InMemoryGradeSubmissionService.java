package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 供开发和测试使用的内存成绩草稿服务，程序重启后数据会丢失。 */
public final class InMemoryGradeSubmissionService implements GradeSubmissionService {
    private final Map<String, GradeSubmission> submissions =
            new LinkedHashMap<String, GradeSubmission>();
    private final Map<String, Map<String, GradeEntry>> entriesBySubmission =
            new LinkedHashMap<String, Map<String, GradeEntry>>();
    private final Map<String, List<GradeSubmissionAuditRecord>> auditBySubmission =
            new LinkedHashMap<String, List<GradeSubmissionAuditRecord>>();
    private final Map<String, List<GradeReviewSnapshot>> snapshotsBySubmission =
            new LinkedHashMap<String, List<GradeReviewSnapshot>>();

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
        auditBySubmission.put(submission.getSubmissionId(),
                new ArrayList<GradeSubmissionAuditRecord>());
        snapshotsBySubmission.put(submission.getSubmissionId(),
                new ArrayList<GradeReviewSnapshot>());
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
    public synchronized ServiceResult<List<GradeSubmissionAuditRecord>> listAudit(String submissionId) {
        ServiceResult<GradeSubmission> submission = findById(submissionId);
        if (submission.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submission.getStatus(), submission.getMessage());
        }
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<GradeSubmissionAuditRecord>(
                auditBySubmission.get(submission.getData().getSubmissionId()))));
    }

    @Override
    public synchronized ServiceResult<GradeReviewSnapshot> findLatestReviewSnapshot(String submissionId) {
        ServiceResult<GradeSubmission> submission = findById(submissionId);
        if (submission.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submission.getStatus(), submission.getMessage());
        }
        List<GradeReviewSnapshot> snapshots = snapshotsBySubmission.get(
                submission.getData().getSubmissionId());
        if (snapshots.isEmpty()) return ServiceResult.failure(StatusCode.NOT_FOUND,
                "grade review snapshot not found");
        return ServiceResult.ok(snapshots.get(snapshots.size() - 1));
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
        LocalDateTime now = LocalDateTime.now();
        List<GradeEntry> workingEntries = new ArrayList<GradeEntry>(entriesBySubmission.get(
                submission.getSubmissionId()).values());
        if (workingEntries.isEmpty()) return ServiceResult.failure(StatusCode.CONFLICT,
                "grade entries must not be empty before submission");
        int version = snapshotsBySubmission.get(submission.getSubmissionId()).size() + 1;
        snapshotsBySubmission.get(submission.getSubmissionId()).add(new GradeReviewSnapshot(
                submission.getSubmissionId(), version, now, workingEntries));
        GradeSubmission pending = submission.pendingReview(now);
        submissions.put(pending.getSubmissionId(), pending);
        appendAudit(pending.getSubmissionId(), GradeSubmissionAuditAction.SUBMITTED,
                submission.getTeacherId(), "提交第" + version + "版", pending.getUpdatedAt());
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
        boolean returningApproved = decision == GradeReviewDecision.RETURN
                && found.getData().getStatus() == GradeSubmissionStatus.APPROVED;
        if (found.getData().getStatus() != GradeSubmissionStatus.PENDING_REVIEW
                && !returningApproved) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending or approved grade submissions can be returned");
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
        appendAudit(reviewed.getSubmissionId(), decision == GradeReviewDecision.APPROVE
                ? GradeSubmissionAuditAction.APPROVED : GradeSubmissionAuditAction.RETURNED,
                reviewerId, remark, reviewed.getReviewedAt());
        return ServiceResult.ok(reviewed);
    }

    private void appendAudit(String submissionId, GradeSubmissionAuditAction action,
            String actorId, String remark, LocalDateTime occurredAt) {
        auditBySubmission.get(submissionId).add(new GradeSubmissionAuditRecord(
                UUID.randomUUID().toString(), submissionId, action, actorId, occurredAt, remark));
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
