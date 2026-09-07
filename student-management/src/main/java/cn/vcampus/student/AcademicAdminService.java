package cn.vcampus.student;

import cn.vcampus.common.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Shared administrative rules. Store implementations provide the transaction boundary. */
public final class AcademicAdminService {
    private final AcademicAdminStore store;
    public AcademicAdminService(AcademicAdminStore store) { this.store = Objects.requireNonNull(store); }

    public ServiceResult<?> execute(AcademicAdminCommandV1 command, String actor) {
        try {
            command.validate();
            if (actor == null || actor.trim().isEmpty()) throw new IllegalArgumentException("actor is required");
            return store.transaction(context -> execute(context, command, actor));
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        } catch (Exception failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "教务操作失败，未完成的事务已回滚");
        }
    }

    private ServiceResult<?> execute(AcademicAdminStore.Context context,
            AcademicAdminCommandV1 command, String actor) throws Exception {
        switch (command.getAction()) {
            case STUDENTS: return ServiceResult.ok(context.students());
            case TEACHERS: return ServiceResult.ok(context.teachers());
            default: break;
        }
        StudentRecord student = context.student(command.getStudentId());
        if (student == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "学生档案不存在");
        if (command.getAction() == AcademicAdminCommandV1.Action.ASSESSMENTS) {
            return ServiceResult.ok(context.assessments(student.getStudentId()));
        }
        List<CourseHistoryRecord> history = context.history(student.getStudentId());
        if (command.getAction() == AcademicAdminCommandV1.Action.HISTORY) return ServiceResult.ok(history);
        CreditSummary credits = CreditSummary.from(student.getStudentId(), history);
        if (command.getAction() == AcademicAdminCommandV1.Action.CREDITS) return ServiceResult.ok(credits);
        if (!"在读".equals(student.getStatus())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "仅在读学生可新建审查或办理毕业");
        }
        if (command.getAction() == AcademicAdminCommandV1.Action.REVIEW) {
            AcademicAssessment assessment = new AcademicAssessment(UUID.randomUUID().toString(),
                    credits, command.getRequiredCredits(), evidence(student, history), actor,
                    Instant.now(), command.getNote(), null, null, null);
            context.save(assessment);
            return ServiceResult.ok(assessment);
        }
        List<AcademicAssessment> assessments = context.assessments(student.getStudentId());
        if (assessments.isEmpty() || !assessments.get(0).getId().equals(command.getAssessmentId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "请使用该学生最新审查记录办理毕业");
        }
        AcademicAssessment latest = assessments.get(0);
        if (latest.isGraduated() || !latest.isCreditRequirementMet()) {
            return ServiceResult.failure(StatusCode.CONFLICT, "审查未达标或已办理毕业");
        }
        if (!latest.getEvidence().equals(evidence(student, history))) {
            return ServiceResult.failure(StatusCode.CONFLICT, "成绩或学生档案已变更，请重新审查");
        }
        AcademicAssessment graduated = latest.graduate(actor, command.getNote());
        context.graduate(student, graduated);
        return ServiceResult.ok(graduated);
    }

    /** Stable multiset fingerprint includes the profile and every attempt, not just credit totals. */
    public static String evidence(StudentRecord student, List<CourseHistoryRecord> history) throws Exception {
        List<String> rows = new ArrayList<String>();
        for (CourseHistoryRecord record : history) {
            ByteArrayOutputStream row = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(row)) { output.writeObject(record); }
            rows.add(Base64.getEncoder().encodeToString(row.toByteArray()));
        }
        Collections.sort(rows);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(student);
            output.writeObject(rows);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
