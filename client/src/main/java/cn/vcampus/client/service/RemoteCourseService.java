package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseDropRecordV2Command;
import cn.vcampus.course.CourseGradeDraftV2Command;
import cn.vcampus.course.CourseGradeImportV2Command;
import cn.vcampus.course.CourseGradeReviewV2Command;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectOfferingV2Command;
import cn.vcampus.course.CourseTeachingQueryV2Command;
import cn.vcampus.course.CourseTeacherDirectoryV1Command;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanManagementCommand;
import cn.vcampus.course.TrainingPlanStatus;
import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端选课服务：仅发送 token、轮次、教学班或选课记录编号。 */
public final class RemoteCourseService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();
    public RemoteCourseService(String host, int port) throws IOException { messages = new SocketMessageClient(host, port); }
    public Message availableRounds(String token) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.availableRounds(token)); }
    public Message availableOfferings(String token, String roundId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.availableOfferings(token, roundId)); }
    public Message selectedOfferings(String token) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.selectedOfferings(token)); }
    public Message select(String token, String roundId, String offeringId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECT_OFFERING_V2, new CourseSelectOfferingV2Command(token, roundId, offeringId)); }
    public Message drop(String token, String recordId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_DROP_RECORD_V2, new CourseDropRecordV2Command(token, recordId)); }

    /** 查询当前任课老师在指定学期的教学班。 */
    public Message myTeachingOfferings(String token, String term)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.myOfferings(token, term));
    }

    /** 查询当前任课老师指定教学班的有效选课名单。 */
    public Message teachingRoster(String token, String offeringId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.offeringRoster(token, offeringId));
    }

    /** 打开教师本人的教学班成绩草稿。 */
    public Message openGradeDraft(String token, String offeringId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.openDraft(token, offeringId));
    }

    /** 保存或覆盖一名有效选课学生的成绩。 */
    public Message saveGradeEntry(String token, String offeringId, String studentId, int score)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.saveEntry(token, offeringId, studentId, score));
    }

    /** 将当前教学班成绩草稿提交教务审核。 */
    public Message submitGradesForReview(String token, String offeringId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(token, offeringId));
    }

    /** 查询当前任课老师本人教学班的成绩提交流转记录。 */
    public Message gradeDraftAudit(String token, String offeringId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.listAudit(token, offeringId));
    }

    /** 导入 CSV、XLS 或 XLSX 成绩文件的字节内容。 */
    public Message importGrades(String token, String offeringId, String fileName, byte[] content)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_IMPORT_V2,
                new CourseGradeImportV2Command(token, offeringId, fileName, content));
    }

    /** 查询教务老师当前可审核的成绩提交单。 */
    public Message pendingGradeSubmissions(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.listPending(token));
    }

    public Message gradeReviewHistory(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.listHistory(token));
    }

    public Message gradeReviewDetail(String token, String submissionId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.viewDetail(token, submissionId));
    }

    public Message gradeReviewAudit(String token, String submissionId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.viewAudit(token, submissionId));
    }

    public Message approveGradeSubmission(String token, String submissionId, String remark)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.approve(token, submissionId, remark));
    }

    public Message returnGradeSubmission(String token, String submissionId, String remark)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.returnForRevision(token, submissionId, remark));
    }

    /** 发送教务管理员维护课程目录或教学班的请求。 */
    public Message manage(CourseManagementCommand command) throws IOException, ClassNotFoundException {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        return send(MessageType.COURSE_MANAGE, command);
    }

    /** 查询可被分配教学班的在职教师。 */
    public Message activeTeachers(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_TEACHER_DIRECTORY_V1,
                new CourseTeacherDirectoryV1Command(token));
    }

    public Message selectionRoundsByTerm(String token, String term)
            throws IOException, ClassNotFoundException {
        return manage(CourseManagementCommand.listSelectionRoundsByTerm(token, term));
    }

    public Message createSelectionRound(String token, SelectionRound round)
            throws IOException, ClassNotFoundException {
        return manage(CourseManagementCommand.createSelectionRound(token, round));
    }

    public Message updateSelectionRoundTimeWindow(String token, String roundId,
            LocalDateTime startsAt, LocalDateTime endsAt) throws IOException, ClassNotFoundException {
        return manage(CourseManagementCommand.updateSelectionRoundTimeWindow(token, roundId, startsAt,
                endsAt));
    }

    public Message changeSelectionRoundStatus(String token, String roundId,
            SelectionRoundStatus status) throws IOException, ClassNotFoundException {
        return manage(CourseManagementCommand.changeSelectionRoundStatus(token, roundId, status));
    }

    /** 培养方案管理使用独立协议，避免继续扩张课程/教学班维护命令。 */
    public Message listTrainingPlans(String token) throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.list(token));
    }

    public Message createTrainingPlan(String token, TrainingPlan plan)
            throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.create(token, plan));
    }

    public Message updateTrainingPlanBasicInfo(String token, TrainingPlan plan)
            throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.updateBasicInfo(token, plan));
    }

    public Message saveTrainingPlanCourse(String token, String planId, TrainingPlanCourse course)
            throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.saveCourse(token, planId, course));
    }

    public Message removeTrainingPlanCourse(String token, String planId, String courseId)
            throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.removeCourse(token, planId, courseId));
    }

    public Message changeTrainingPlanStatus(String token, String planId,
            TrainingPlanStatus status) throws IOException, ClassNotFoundException {
        return manageTrainingPlans(TrainingPlanManagementCommand.changeStatus(token, planId, status));
    }

    private Message manageTrainingPlans(TrainingPlanManagementCommand command)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_TRAINING_PLAN_MANAGE_V2, command);
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException { return messages.send(Message.request("course-" + sequence.incrementAndGet(), type, payload)); }
    @Override public void close() throws IOException { messages.close(); }
}
