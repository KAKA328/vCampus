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
        if (entry == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "grade entry must not be null");
        ServiceResult<GradeSubmission> submissionResult = findById(entry.getSubmissionId());
        if (submissionResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submissionResult.getStatus(), submissionResult.getMessage());
        }
        GradeSubmission submission = submissionResult.getData();
        if (submission.getStatus() != GradeSubmissionStatus.DRAFT
                && submission.getStatus() != GradeSubmissionStatus.RETURNED) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "grade entries can only be changed in draft or returned status");
        }
        entriesBySubmission.get(submission.getSubmissionId()).put(entry.getStudentId(), entry);
        submissions.put(submission.getSubmissionId(), submission.withUpdatedAt(LocalDateTime.now()));
        return ServiceResult.ok(entry);
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
