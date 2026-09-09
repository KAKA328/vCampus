package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseDropRecordV2Command;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseGradeDraftV2Command;
import cn.vcampus.course.CourseGradeImportV2Command;
import cn.vcampus.course.CourseGradeReviewV2Command;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectOfferingV2Command;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.CourseTeachingQueryV2Command;
import cn.vcampus.course.CourseTeacherDirectoryV1Command;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeImportFileParser;
import cn.vcampus.course.GradeImportResult;
import cn.vcampus.course.GradeImportRow;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.SelectionRoundService;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingGradeDraft;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.course.TrainingPlanManagementCommand;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.CourseResultRecordingService;
import cn.vcampus.student.FormalCourseResult;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 将当前选课流程转换为 Socket 消息；学生资料仅由服务器按 token 查询。 */
final class CourseMessageHandler {
    private final CourseSelectionService courses;
    private final CourseCatalogService catalog;
    private final CourseOfferingService offerings;
    private final SelectionRoundService selectionRounds;
    private final CourseSelectionRecordService records;
    private final GradeSubmissionService gradeSubmissions;
    private final CourseResultRecordingService formalResults;
    private final GradeApprovalWorkflow gradeApprovals;
    private final TrainingPlanService trainingPlans;
    private final StudentSelectionProfileProvider profiles;
    private final UserManagementService users;
    private final TeacherProfileService teachers;
    private final StudentManagementService students;

    CourseMessageHandler(CourseSelectionService courses, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, null, null, null, null, null, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, catalog, offerings, null, null, null, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            StudentSelectionProfileProvider profiles, UserManagementService users) {
        this(courses, catalog, offerings, selectionRounds, null, null, null, profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            TrainingPlanService trainingPlans, StudentSelectionProfileProvider profiles,
            UserManagementService users) {
        this(courses, catalog, offerings, selectionRounds, null, null, null, null, trainingPlans,
                profiles, users, null, null);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, StudentSelectionProfileProvider profiles,
            UserManagementService users, TeacherProfileService teachers,
            StudentManagementService students) {
        this(courses, catalog, offerings, selectionRounds, records, null, null, profiles, users,
                teachers, students);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, GradeSubmissionService gradeSubmissions,
            StudentSelectionProfileProvider profiles, UserManagementService users,
            TeacherProfileService teachers, StudentManagementService students) {
        this(courses, catalog, offerings, selectionRounds, records, gradeSubmissions, null, profiles,
                users, teachers, students);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, GradeSubmissionService gradeSubmissions,
            CourseResultRecordingService formalResults, StudentSelectionProfileProvider profiles,
            UserManagementService users, TeacherProfileService teachers,
            StudentManagementService students) {
        this(courses, catalog, offerings, selectionRounds, records, gradeSubmissions, formalResults,
                null, profiles, users, teachers, students);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, GradeSubmissionService gradeSubmissions,
            CourseResultRecordingService formalResults, GradeApprovalWorkflow gradeApprovals,
            StudentSelectionProfileProvider profiles, UserManagementService users,
            TeacherProfileService teachers, StudentManagementService students) {
        this(courses, catalog, offerings, selectionRounds, records, gradeSubmissions, formalResults,
                gradeApprovals, null, profiles, users, teachers, students);
    }

    CourseMessageHandler(CourseSelectionService courses, CourseCatalogService catalog,
            CourseOfferingService offerings, SelectionRoundService selectionRounds,
            CourseSelectionRecordService records, GradeSubmissionService gradeSubmissions,
            CourseResultRecordingService formalResults, GradeApprovalWorkflow gradeApprovals,
            TrainingPlanService trainingPlans, StudentSelectionProfileProvider profiles,
            UserManagementService users, TeacherProfileService teachers,
            StudentManagementService students) {
        if (courses == null || profiles == null || users == null) {
            throw new IllegalArgumentException("course handler dependencies must not be null");
        }
        this.courses = courses;
        this.catalog = catalog;
        this.offerings = offerings;
        this.selectionRounds = selectionRounds;
        this.records = records;
        this.gradeSubmissions = gradeSubmissions;
        this.formalResults = formalResults;
        this.gradeApprovals = gradeApprovals != null ? gradeApprovals
                : (gradeSubmissions == null || formalResults == null ? null
                        : new InMemoryGradeApprovalWorkflow(gradeSubmissions, formalResults));
        this.trainingPlans = trainingPlans;
        this.profiles = profiles;
        this.users = users;
        this.teachers = teachers;
        this.students = students;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.COURSE_SELECTION_QUERY_V2, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case COURSE_SELECTION_QUERY_V2:
                    result = query(payload(request, CourseSelectionQueryV2Command.class));
                    break;
                case COURSE_SELECT_OFFERING_V2:
                    CourseSelectOfferingV2Command select =
                            payload(request, CourseSelectOfferingV2Command.class);
                    result = select(select);
                    break;
                case COURSE_DROP_RECORD_V2:
                    result = drop(payload(request, CourseDropRecordV2Command.class));
                    break;
                case COURSE_TEACHER_DIRECTORY_V1:
                    result = activeTeachers(payload(request, CourseTeacherDirectoryV1Command.class));
                    break;
                case COURSE_TEACHING_QUERY_V2:
                    result = teachingQuery(payload(request, CourseTeachingQueryV2Command.class));
                    break;
                case COURSE_GRADE_DRAFT_V2:
                    result = gradeDraft(payload(request, CourseGradeDraftV2Command.class));
                    break;
                case COURSE_GRADE_IMPORT_V2:
                    result = gradeImport(payload(request, CourseGradeImportV2Command.class));
                    break;
                case COURSE_GRADE_REVIEW_V2:
                    result = gradeReview(payload(request, CourseGradeReviewV2Command.class));
                    break;
                case COURSE_TRAINING_PLAN_MANAGE_V2:
                    result = manageTrainingPlans(
                            payload(request, TrainingPlanManagementCommand.class));
                    break;
                case COURSE_MANAGE:
                    result = manage(payload(request, CourseManagementCommand.class));
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "course handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(CourseSelectionQueryV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_READ);
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (command.getQueryType() == CourseSelectionQueryV2Command.QueryType.AVAILABLE_ROUNDS) {
            return courses.listAvailableRounds(profile.getData(), LocalDateTime.now());
        }
        if (command.getQueryType() == CourseSelectionQueryV2Command.QueryType.AVAILABLE_OFFERINGS) {
            return courses.listAvailableOfferings(profile.getData(), command.getRoundId(),
                    LocalDateTime.now());
        }
        return courses.listSelectedOfferings(profile.getData());
    }

    private ServiceResult<?> select(CourseSelectOfferingV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.select(profile.getData(), command.getRoundId(), command.getOfferingId(),
                        LocalDateTime.now()) : profile;
    }

    private ServiceResult<?> drop(CourseDropRecordV2Command command) {
        ServiceResult<StudentSelectionProfile> profile = profile(command.getToken(), Permission.COURSE_SELECT);
        return profile.getStatus() == StatusCode.OK
                ? courses.drop(profile.getData(), command.getRecordId(), LocalDateTime.now()) : profile;
    }

    /** 教务端仅获取已建档且在职的教师，教学班保存时仍会再次校验。 */
    private ServiceResult<?> activeTeachers(CourseTeacherDirectoryV1Command command) {
        ServiceResult<Void> authorization = authorizeCourseManager(command.getToken());
        if (authorization.getStatus() != StatusCode.OK) return authorization;
        if (teachers == null) return ServiceResult.failure(StatusCode.NOT_FOUND,
                "teacher directory is not configured");
        ServiceResult<List<TeacherProfile>> result = teachers.findAll();
        if (result.getStatus() != StatusCode.OK) return result;
        List<TeacherProfile> active = new ArrayList<TeacherProfile>();
        for (TeacherProfile teacher : result.getData()) if (teacher.isActive()) active.add(teacher);
        return ServiceResult.ok(active);
    }

    /** 教师仅能查看自己任教的教学班和其中仍有效的选课记录。 */
    private ServiceResult<?> teachingQuery(CourseTeachingQueryV2Command command) {
        ServiceResult<TeacherProfile> profile = teacherProfile(command.getToken());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (offerings == null || catalog == null) return teachingServiceUnavailable();
        if (command.getQueryType() == CourseTeachingQueryV2Command.QueryType.MY_OFFERINGS) {
            ServiceResult<List<cn.vcampus.course.CourseOffering>> offeringResult = offerings
                    .listByTeacher(profile.getData().getTeacherId(), command.getTerm());
            if (offeringResult.getStatus() != StatusCode.OK) return offeringResult;
            List<TeachingOffering> result = new ArrayList<TeachingOffering>();
            for (cn.vcampus.course.CourseOffering offering : offeringResult.getData()) {
                ServiceResult<cn.vcampus.course.Course> course = catalog.findById(offering.getCourseId());
                if (course.getStatus() != StatusCode.OK) {
                    return ServiceResult.failure(course.getStatus(), course.getMessage());
                }
                result.add(new TeachingOffering(offering, course.getData()));
            }
            return ServiceResult.ok(result);
        }
        return roster(profile.getData(), command.getOfferingId());
    }

    private ServiceResult<TeachingRoster> roster(TeacherProfile teacher, String offeringId) {
        if (offerings == null || catalog == null || records == null || students == null) {
            return teachingServiceUnavailable();
        }
        ServiceResult<cn.vcampus.course.CourseOffering> offering = offerings.findById(offeringId);
        if (offering.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(offering.getStatus(), offering.getMessage());
        }
        if (!teacher.getTeacherId().equals(offering.getData().getTeacherId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "teacher cannot view another teacher's offering roster");
        }
        ServiceResult<cn.vcampus.course.Course> course = catalog.findById(
                offering.getData().getCourseId());
        if (course.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(course.getStatus(), course.getMessage());
        }
        ServiceResult<List<CourseSelectionRecord>> recordsResult = records
                .listActiveByOffering(offering.getData().getOfferingId());
        if (recordsResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(recordsResult.getStatus(), recordsResult.getMessage());
        }
        List<String> studentIds = new ArrayList<String>();
        for (CourseSelectionRecord record : recordsResult.getData()) {
            studentIds.add(record.getStudentId());
        }
        ServiceResult<List<StudentRecord>> studentsResult = students.findByIds(studentIds);
        if (studentsResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(studentsResult.getStatus(), studentsResult.getMessage());
        }
        Map<String, StudentRecord> studentsById = new LinkedHashMap<String, StudentRecord>();
        for (StudentRecord student : studentsResult.getData()) {
            studentsById.put(student.getStudentId(), student);
        }
        List<TeachingRosterEntry> roster = new ArrayList<TeachingRosterEntry>();
        for (CourseSelectionRecord record : recordsResult.getData()) {
            StudentRecord student = studentsById.get(record.getStudentId());
            if (student == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "student profile for active selection was not found");
            }
            roster.add(new TeachingRosterEntry(student.getStudentId(), student.getName(),
                    student.getMajorName(), student.getClassId(), record.getSelectionType()));
        }
        return ServiceResult.ok(new TeachingRoster(new TeachingOffering(offering.getData(),
                course.getData()), roster));
    }

    /** 教师打开、修改或提交本人教学班成绩草稿，学生范围和选课类别均由服务端确定。 */
    private ServiceResult<?> gradeDraft(CourseGradeDraftV2Command command) {
        ServiceResult<TeacherProfile> profile = teacherProfile(command.getToken());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (gradeSubmissions == null) return gradeDraftServiceUnavailable();
        ServiceResult<TeachingRoster> roster = roster(profile.getData(), command.getOfferingId());
        if (roster.getStatus() != StatusCode.OK) return roster;

        TeachingRosterEntry selectedStudent = null;
        if (command.getOperation() == CourseGradeDraftV2Command.Operation.SAVE_ENTRY) {
            selectedStudent = findRosterStudent(roster.getData(), command.getStudentId());
            if (selectedStudent == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "student is not an active selection in this offering");
            }
        }

        ServiceResult<GradeSubmission> submission = findOrCreateDraft(profile.getData(),
                command.getOfferingId());
        if (submission.getStatus() != StatusCode.OK) return submission;
        GradeSubmission currentSubmission = submission.getData();
        if (command.getOperation() == CourseGradeDraftV2Command.Operation.SAVE_ENTRY) {
            ServiceResult<GradeEntry> saved = gradeSubmissions.saveDraftEntry(new GradeEntry(
                    currentSubmission.getSubmissionId(), selectedStudent.getStudentId(),
                    selectedStudent.getSelectionType(), command.getScore(), LocalDateTime.now()));
            if (saved.getStatus() != StatusCode.OK) return saved;
            ServiceResult<GradeSubmission> refreshed = gradeSubmissions.findById(
                    currentSubmission.getSubmissionId());
            if (refreshed.getStatus() != StatusCode.OK) return refreshed;
            currentSubmission = refreshed.getData();
        } else if (command.getOperation()
                == CourseGradeDraftV2Command.Operation.SUBMIT_FOR_REVIEW) {
            ServiceResult<Void> completeness = requireCompleteGrades(roster.getData(),
                    currentSubmission);
            if (completeness.getStatus() != StatusCode.OK) return completeness;
            ServiceResult<GradeSubmission> submitted = gradeSubmissions.submitForReview(
                    currentSubmission.getSubmissionId());
            if (submitted.getStatus() != StatusCode.OK) return submitted;
            currentSubmission = submitted.getData();
        }
        return readTeachingGradeDraft(roster.getData(), currentSubmission);
    }

    /** 先完整解析、校验文件，再原子写入一个教学班的成绩草稿。 */
    private ServiceResult<?> gradeImport(CourseGradeImportV2Command command) {
        ServiceResult<TeacherProfile> profile = teacherProfile(command.getToken());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (gradeSubmissions == null) return gradeDraftServiceUnavailable();
        ServiceResult<TeachingRoster> roster = roster(profile.getData(), command.getOfferingId());
        if (roster.getStatus() != StatusCode.OK) return roster;

        List<GradeImportRow> rows = GradeImportFileParser.parse(command.getFileName(),
                command.getContent());
        Map<String, TeachingRosterEntry> studentsById =
                new LinkedHashMap<String, TeachingRosterEntry>();
        for (TeachingRosterEntry student : roster.getData().getStudents()) {
            studentsById.put(student.getStudentId(), student);
        }
        for (GradeImportRow row : rows) {
            TeachingRosterEntry student = studentsById.get(row.getStudentId());
            if (student == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND, "row " + row.getRowNumber()
                        + " student is not an active selection in this offering: "
                        + row.getStudentId());
            }
        }
        ServiceResult<GradeSubmission> submission = findOrCreateDraft(profile.getData(),
                command.getOfferingId());
        if (submission.getStatus() != StatusCode.OK) return submission;
        List<GradeEntry> savedEntries = new ArrayList<GradeEntry>();
        for (GradeImportRow row : rows) {
            TeachingRosterEntry student = studentsById.get(row.getStudentId());
            savedEntries.add(new GradeEntry(submission.getData().getSubmissionId(),
                    student.getStudentId(), student.getSelectionType(), row.getScore(),
                    LocalDateTime.now()));
        }
        ServiceResult<List<GradeEntry>> saved = gradeSubmissions.saveDraftEntries(savedEntries);
        if (saved.getStatus() != StatusCode.OK) return saved;
        ServiceResult<GradeSubmission> refreshed = gradeSubmissions.findById(
                submission.getData().getSubmissionId());
        if (refreshed.getStatus() != StatusCode.OK) return refreshed;
        ServiceResult<TeachingGradeDraft> draft = readTeachingGradeDraft(roster.getData(),
                refreshed.getData());
        if (draft.getStatus() != StatusCode.OK) return draft;
        return ServiceResult.ok(new GradeImportResult(rows.size(), draft.getData()));
    }

    /** 提交前要求每一名当前有效选课学生都恰有一条成绩记录。 */
    private ServiceResult<Void> requireCompleteGrades(TeachingRoster roster,
            GradeSubmission submission) {
        ServiceResult<List<GradeEntry>> entries = gradeSubmissions.listEntries(
                submission.getSubmissionId());
        if (entries.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(entries.getStatus(), entries.getMessage());
        }
        Map<String, GradeEntry> entriesByStudent = new LinkedHashMap<String, GradeEntry>();
        for (GradeEntry entry : entries.getData()) entriesByStudent.put(entry.getStudentId(), entry);
        List<String> missingStudentIds = new ArrayList<String>();
        for (TeachingRosterEntry student : roster.getStudents()) {
            if (!entriesByStudent.containsKey(student.getStudentId())) {
                missingStudentIds.add(student.getStudentId());
            }
        }
        if (!missingStudentIds.isEmpty()) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "all active students must have grades before submission: " + missingStudentIds);
        }
        return ServiceResult.ok(null);
    }

    /** 找到既有草稿；首次打开教学班时创建一份。并发首次打开时回读已经创建成功的草稿。 */
    private ServiceResult<GradeSubmission> findOrCreateDraft(TeacherProfile teacher,
            String offeringId) {
        ServiceResult<GradeSubmission> found = gradeSubmissions.findByOffering(offeringId);
        if (found.getStatus() == StatusCode.OK) return found;
        if (found.getStatus() != StatusCode.NOT_FOUND) return found;
        ServiceResult<GradeSubmission> created = gradeSubmissions.createDraft(GradeSubmission.draft(
                UUID.randomUUID().toString(), offeringId, teacher.getTeacherId(), LocalDateTime.now()));
        if (created.getStatus() != StatusCode.CONFLICT) return created;
        return gradeSubmissions.findByOffering(offeringId);
    }

    private ServiceResult<TeachingGradeDraft> readTeachingGradeDraft(TeachingRoster roster,
            GradeSubmission submission) {
        ServiceResult<List<GradeEntry>> entries = gradeSubmissions.listEntries(
                submission.getSubmissionId());
        if (entries.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(entries.getStatus(), entries.getMessage());
        }
        return ServiceResult.ok(new TeachingGradeDraft(roster, submission, entries.getData()));
    }

    /** 教务老师查看待审核成绩、退回修改或通过并发布为正式成绩。 */
    private ServiceResult<?> gradeReview(CourseGradeReviewV2Command command) {
        ServiceResult<Session> reviewer = academicReviewer(command.getToken());
        if (reviewer.getStatus() != StatusCode.OK) return reviewer;
        if (gradeSubmissions == null) return gradeReviewServiceUnavailable();
        if (command.getOperation() == CourseGradeReviewV2Command.Operation.LIST_PENDING) {
            return gradeSubmissions.listByStatus(GradeSubmissionStatus.PENDING_REVIEW);
        }
        if (command.getOperation() == CourseGradeReviewV2Command.Operation.LIST_HISTORY) {
            return gradeSubmissions.listReviewHistory();
        }
        if (command.getOperation() == CourseGradeReviewV2Command.Operation.VIEW_AUDIT) {
            return gradeSubmissions.listAudit(command.getSubmissionId());
        }
        ServiceResult<TeachingGradeDraft> detail = reviewDetail(command.getSubmissionId());
        if (detail.getStatus() != StatusCode.OK) return detail;
        if (command.getOperation() == CourseGradeReviewV2Command.Operation.VIEW_DETAIL) {
            return detail;
        }
        if (command.getOperation() == CourseGradeReviewV2Command.Operation.RETURN) {
            if (gradeApprovals == null) return gradeReviewServiceUnavailable();
            return gradeApprovals.returnForRevision(command.getSubmissionId(),
                    detail.getData().getReviewVersionNo().intValue(),
                    reviewer.getData().getUser().getUserId(), command.getRemark());
        }
        if (detail.getData().getSubmission().getStatus()
                != GradeSubmissionStatus.PENDING_REVIEW) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only pending grade submissions can be approved");
        }
        if (formalResults == null || gradeApprovals == null) return gradeReviewServiceUnavailable();
        ServiceResult<Void> complete = requireCompleteGrades(detail.getData().getRoster(),
                detail.getData().getSubmission());
        if (complete.getStatus() != StatusCode.OK) return complete;
        ServiceResult<List<FormalCourseResult>> recordsToPublish = formalResults(detail.getData());
        if (recordsToPublish.getStatus() != StatusCode.OK) return recordsToPublish;
        return gradeApprovals.approve(command.getSubmissionId(),
                detail.getData().getReviewVersionNo().intValue(), recordsToPublish.getData(),
                reviewer.getData().getUser().getUserId(), command.getRemark());
    }

    private ServiceResult<TeachingGradeDraft> reviewDetail(String submissionId) {
        ServiceResult<GradeSubmission> submission = gradeSubmissions.findById(submissionId);
        if (submission.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(submission.getStatus(), submission.getMessage());
        }
        ServiceResult<TeachingRoster> roster = rosterForReview(submission.getData().getOfferingId());
        if (roster.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(roster.getStatus(), roster.getMessage());
        }
        ServiceResult<cn.vcampus.course.GradeReviewSnapshot> snapshot =
                gradeSubmissions.findLatestReviewSnapshot(submissionId);
        if (snapshot.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(snapshot.getStatus(), snapshot.getMessage());
        }
        return ServiceResult.ok(new TeachingGradeDraft(roster.getData(), submission.getData(),
                snapshot.getData()));
    }

    /** 教务审核允许查看任意教学班，但仍只读取有效选课名单。 */
    private ServiceResult<TeachingRoster> rosterForReview(String offeringId) {
        if (offerings == null || catalog == null || records == null || students == null) {
            return gradeReviewServiceUnavailable();
        }
        ServiceResult<cn.vcampus.course.CourseOffering> offering = offerings.findById(offeringId);
        if (offering.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(offering.getStatus(), offering.getMessage());
        }
        ServiceResult<cn.vcampus.course.Course> course = catalog.findById(
                offering.getData().getCourseId());
        if (course.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(course.getStatus(), course.getMessage());
        }
        ServiceResult<List<CourseSelectionRecord>> activeRecords = records.listActiveByOffering(
                offering.getData().getOfferingId());
        if (activeRecords.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(activeRecords.getStatus(), activeRecords.getMessage());
        }
        List<String> studentIds = new ArrayList<String>();
        for (CourseSelectionRecord record : activeRecords.getData()) studentIds.add(record.getStudentId());
        ServiceResult<List<StudentRecord>> studentRecords = students.findByIds(studentIds);
        if (studentRecords.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(studentRecords.getStatus(), studentRecords.getMessage());
        }
        Map<String, StudentRecord> byId = new LinkedHashMap<String, StudentRecord>();
        for (StudentRecord student : studentRecords.getData()) byId.put(student.getStudentId(), student);
        List<TeachingRosterEntry> roster = new ArrayList<TeachingRosterEntry>();
        for (CourseSelectionRecord record : activeRecords.getData()) {
            StudentRecord student = byId.get(record.getStudentId());
            if (student == null) return ServiceResult.failure(StatusCode.NOT_FOUND,
                    "student profile for active selection was not found");
            roster.add(new TeachingRosterEntry(student.getStudentId(), student.getName(),
                    student.getMajorName(), student.getClassId(), record.getSelectionType()));
        }
        return ServiceResult.ok(new TeachingRoster(new TeachingOffering(offering.getData(),
                course.getData()), roster));
    }

    /** 按最终成绩构造正式学籍记录；重修次数由已存在的正式记录确定。 */
    private ServiceResult<List<FormalCourseResult>> formalResults(TeachingGradeDraft detail) {
        cn.vcampus.course.CourseOffering offering = detail.getRoster().getTeachingOffering()
                .getOffering();
        cn.vcampus.course.Course course = detail.getRoster().getTeachingOffering().getCourse();
        Map<String, GradeEntry> grades = new LinkedHashMap<String, GradeEntry>();
        for (GradeEntry entry : detail.getEntries()) grades.put(entry.getStudentId(), entry);
        List<FormalCourseResult> result = new ArrayList<FormalCourseResult>();
        LocalDateTime now = LocalDateTime.now();
        for (TeachingRosterEntry student : detail.getRoster().getStudents()) {
            GradeEntry grade = grades.get(student.getStudentId());
            ServiceResult<Integer> nextAttempt = formalResults.nextAttemptNo(student.getStudentId(),
                    course.getCourseId());
            if (nextAttempt.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(nextAttempt.getStatus(), nextAttempt.getMessage());
            }
            boolean passed = grade.getScore() >= 60;
            result.add(new FormalCourseResult(resultId(detail.getSubmission().getSubmissionId(),
                    student.getStudentId()), student.getStudentId(), course.getCourseId(),
                    offering.getOfferingId(), offering.getTerm(), nextAttempt.getData().intValue(),
                    student.getSelectionType() == cn.vcampus.course.SelectionType.RETAKE ? "重修" : "首修",
                    grade.getScore(), passed, passed ? course.getCredits() : 0, now));
        }
        return ServiceResult.ok(result);
    }

    private static String resultId(String submissionId, String studentId) {
        return UUID.nameUUIDFromBytes((submissionId + "|" + studentId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static TeachingRosterEntry findRosterStudent(TeachingRoster roster, String studentId) {
        for (TeachingRosterEntry student : roster.getStudents()) {
            if (student.getStudentId().equals(studentId)) return student;
        }
        return null;
    }

    /** 课程目录和教学班管理仅允许拥有 COURSE_MANAGE 权限的教务人员使用。 */
    private ServiceResult<?> manage(CourseManagementCommand command) {
        ServiceResult<Void> authorization = authorizeCourseManager(command.getToken());
        if (authorization.getStatus() != StatusCode.OK) {
            return authorization;
        }
        switch (command.getOperation()) {
            case LIST_COURSES:
                return catalog == null ? managementServiceUnavailable() : catalog.listAll();
            case LIST_OFFERINGS_BY_TERM:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.listByTerm(command.getTerm());
            case CREATE_COURSE:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.create(command.getCourse());
            case UPDATE_COURSE_DETAILS:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.updateDetails(command.getTargetId(), command.getName(),
                                command.getCredits());
            case CHANGE_COURSE_STATUS:
                return catalog == null ? managementServiceUnavailable()
                        : catalog.changeStatus(command.getTargetId(), command.getCourseStatus());
            case CREATE_OFFERING:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.create(command.getOffering());
            case CHANGE_OFFERING_STATUS:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.changeStatus(command.getTargetId(), command.getOfferingStatus());
            case CHANGE_OFFERING_CAPACITIES:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.changeCapacities(command.getTargetId(),
                                command.getRequiredCapacity(), command.getElectiveCapacity(),
                                command.getCrossMajorCapacity());
            case UPDATE_OFFERING_TEACHING_INFO:
                return offerings == null ? managementServiceUnavailable()
                        : offerings.updateTeachingInfo(command.getTargetId(), command.getTeacherId(),
                                command.getLocation());
            case LIST_SELECTION_ROUNDS_BY_TERM:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.listByTerm(command.getTerm());
            case CREATE_SELECTION_ROUND:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.create(command.getSelectionRound());
            case UPDATE_SELECTION_ROUND_TIME_WINDOW:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.updateTimeWindow(command.getTargetId(), command.getStartsAt(),
                                command.getEndsAt());
            case CHANGE_SELECTION_ROUND_STATUS:
                return selectionRounds == null ? managementServiceUnavailable()
                        : selectionRounds.changeStatus(command.getTargetId(),
                                command.getSelectionRoundStatus());
            default:
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "unsupported management operation");
        }
    }

    /** 培养方案使用独立管理命令，仍复用教务课程管理权限。 */
    private ServiceResult<?> manageTrainingPlans(TrainingPlanManagementCommand command) {
        ServiceResult<Void> authorization = authorizeCourseManager(command.getToken());
        if (authorization.getStatus() != StatusCode.OK) {
            return authorization;
        }
        if (trainingPlans == null) {
            return managementServiceUnavailable();
        }
        switch (command.getOperation()) {
            case LIST:
                return trainingPlans.listAll();
            case CREATE:
                return trainingPlans.create(command.getPlan());
            case UPDATE_BASIC_INFO:
                return trainingPlans.updateBasicInfo(command.getPlan().getPlanId(),
                        command.getPlan().getMajorName(), command.getPlan().getEnrollmentYear());
            case SAVE_COURSE:
                return trainingPlans.saveCourse(command.getPlanId(), command.getCourse());
            case REMOVE_COURSE:
                return trainingPlans.removeCourse(command.getPlanId(), command.getCourseId());
            case CHANGE_STATUS:
                return trainingPlans.changeStatus(command.getPlanId(), command.getStatus());
            default:
                return ServiceResult.failure(StatusCode.BAD_REQUEST,
                        "unsupported training plan management operation");
        }
    }

    private static ServiceResult<Void> managementServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "requested course management service is not configured");
    }

    private static <T> ServiceResult<T> teachingServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "teacher teaching query service is not configured");
    }

    private static <T> ServiceResult<T> gradeDraftServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "teacher grade draft service is not configured");
    }

    private static <T> ServiceResult<T> gradeReviewServiceUnavailable() {
        return ServiceResult.failure(StatusCode.NOT_FOUND,
                "grade review service is not configured");
    }

    private ServiceResult<StudentSelectionProfile> profile(String token, Permission permission) {
        ServiceResult<Boolean> authorized = users.authorize(token, permission.getCode());
        if (authorized.getStatus() != StatusCode.OK) return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) return ServiceResult.failure(session.getStatus(), session.getMessage());
        if (session.getData().getUser().getRole() != Role.STUDENT) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "only student can use student course selection");
        }
        return profiles.findByUserId(session.getData().getUser().getUserId());
    }

    private ServiceResult<TeacherProfile> teacherProfile(String token) {
        if (teachers == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND,
                    "teacher teaching query service is not configured");
        }
        ServiceResult<Boolean> authorized = users.authorize(token, Permission.GRADE_WRITE.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(session.getStatus(), session.getMessage());
        }
        if (session.getData().getUser().getRole() != Role.TEACHER) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "only teacher can query teaching offerings and rosters");
        }
        ServiceResult<TeacherProfile> profile = teachers.findByUserId(
                session.getData().getUser().getUserId());
        if (profile.getStatus() != StatusCode.OK) return profile;
        if (!profile.getData().isActive()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "teacher profile is inactive");
        }
        return profile;
    }

    /** 教务审核仅允许教务管理员或系统管理员在具备审核权限时调用。 */
    private ServiceResult<Session> academicReviewer(String token) {
        ServiceResult<Boolean> authorized = users.authorize(token, Permission.ACADEMIC_REVIEW.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(session.getStatus(), session.getMessage());
        }
        Role role = session.getData().getUser().getRole();
        if (role != Role.ACADEMIC_ADMIN && role != Role.ADMIN) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "only academic administrators can review grade submissions");
        }
        return session;
    }

    private ServiceResult<Void> authorizeCourseManager(String token) {
        ServiceResult<Boolean> authorized = users.authorize(token, Permission.COURSE_MANAGE.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(session.getStatus(), session.getMessage());
        }
        return ServiceResult.ok(null);
    }

    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }
}
