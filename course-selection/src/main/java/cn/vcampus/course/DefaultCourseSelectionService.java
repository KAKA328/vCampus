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

/**
 * 基于课程目录、培养方案、选课轮次、教学班和选课记录的完整选课服务。
 *
 * <p>调用方必须传入由服务端构造的 {@link StudentSelectionProfile}，因此客户端不能伪造
 * 学号、专业、年级或重修资格。</p>
 */
public final class DefaultCourseSelectionService implements CourseSelectionService {
    private final CourseCatalogService courses;
    private final TrainingPlanService trainingPlans;
    private final SelectionRoundService rounds;
    private final CourseOfferingService offerings;
    private final CourseSelectionRecordService records;
    private final CourseOfferingCapacityService capacities;
    private final ScheduleConflictDetector scheduleConflicts;

    public DefaultCourseSelectionService(CourseCatalogService courses,
            TrainingPlanService trainingPlans, SelectionRoundService rounds,
            CourseOfferingService offerings, CourseSelectionRecordService records,
            CourseOfferingCapacityService capacities, ScheduleConflictDetector scheduleConflicts) {
        if (courses == null || trainingPlans == null || rounds == null || offerings == null
                || records == null || capacities == null || scheduleConflicts == null) {
            throw new IllegalArgumentException("course selection dependencies must not be null");
        }
        this.courses = courses;
        this.trainingPlans = trainingPlans;
        this.rounds = rounds;
        this.offerings = offerings;
        this.records = records;
        this.capacities = capacities;
        this.scheduleConflicts = scheduleConflicts;
    }

    @Override
    public synchronized ServiceResult<List<SelectionRound>> listAvailableRounds(
            StudentSelectionProfile student, LocalDateTime time) {
        ServiceResult<Void> profileResult = requireActiveStudent(student, time);
        if (profileResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(profileResult.getStatus(), profileResult.getMessage());
        }
        ServiceResult<List<SelectionRound>> roundResult = rounds.listOpenRounds(student.getCurrentTerm(),
                time);
        if (roundResult.getStatus() != StatusCode.OK) {
            return roundResult;
        }
        List<SelectionRound> eligibleRounds = new ArrayList<SelectionRound>();
        for (SelectionRound round : roundResult.getData()) {
            if (round.getType() == SelectionRoundType.INITIAL) {
                if (eligibleCourseTypes(student, round).getStatus() == StatusCode.OK) {
                    eligibleRounds.add(round);
                }
            } else if (round.getType() == SelectionRoundType.RETAKE
                    && !student.getPendingRetakeCourseIds().isEmpty()) {
                eligibleRounds.add(round);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(eligibleRounds));
    }

    @Override
    public synchronized ServiceResult<List<SelectableCourseOffering>> listAvailableOfferings(
            StudentSelectionProfile student, String roundId, LocalDateTime time) {
        ServiceResult<SelectionRound> roundResult = availableRound(student, roundId, time);
        if (roundResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(roundResult.getStatus(), roundResult.getMessage());
        }
        ServiceResult<Map<String, SelectionType>> eligibleResult = eligibleCourseTypes(student,
                roundResult.getData());
        if (eligibleResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(eligibleResult.getStatus(), eligibleResult.getMessage());
        }
        List<SelectableCourseOffering> result = new ArrayList<SelectableCourseOffering>();
        for (Map.Entry<String, SelectionType> eligibleCourse : eligibleResult.getData().entrySet()) {
            ServiceResult<Course> courseResult = courses.findById(eligibleCourse.getKey());
            if (courseResult.getStatus() != StatusCode.OK) {
                continue;
            }
            ServiceResult<List<CourseOffering>> offeringResult = offerings.listOpenByCourse(
                    eligibleCourse.getKey(), student.getCurrentTerm());
            if (offeringResult.getStatus() != StatusCode.OK) {
                continue;
            }
            for (CourseOffering offering : offeringResult.getData()) {
                ServiceResult<CourseOfferingCapacitySnapshot> capacityResult = capacities.snapshotFor(
                        offering.getOfferingId());
                if (capacityResult.getStatus() != StatusCode.OK) {
                    continue;
                }
                CapacityBucketUsage usage = capacityResult.getData().getUsage(
                        eligibleCourse.getValue().getCapacityBucket());
                if (!usage.isFull()) {
                    result.add(new SelectableCourseOffering(courseResult.getData(), offering,
                            eligibleCourse.getValue(), usage));
                }
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> select(StudentSelectionProfile student,
            String roundId, String offeringId, LocalDateTime time) {
        ServiceResult<SelectionRound> roundResult = availableRound(student, roundId, time);
        if (roundResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(roundResult.getStatus(), roundResult.getMessage());
        }
        ServiceResult<CourseOffering> offeringResult = offerings.findById(offeringId);
        if (offeringResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(offeringResult.getStatus(), offeringResult.getMessage());
        }
        CourseOffering offering = offeringResult.getData();
        if (offering.getStatus() != CourseOfferingStatus.OPEN
                || !student.getCurrentTerm().equals(offering.getTerm())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "course offering is not open for the current term");
        }
        ServiceResult<Map<String, SelectionType>> eligibleResult = eligibleCourseTypes(student,
                roundResult.getData());
        if (eligibleResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(eligibleResult.getStatus(), eligibleResult.getMessage());
        }
        SelectionType selectionType = eligibleResult.getData().get(offering.getCourseId());
        if (selectionType == null) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "student is not eligible for this course in the selected round");
        }
        ServiceResult<CourseOfferingCapacitySnapshot> capacityResult = capacities.snapshotFor(
                offering.getOfferingId());
        if (capacityResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(capacityResult.getStatus(), capacityResult.getMessage());
        }
        if (capacityResult.getData().getUsage(selectionType.getCapacityBucket()).isFull()) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course offering capacity is full");
        }
        ServiceResult<List<CourseSelectionRecord>> activeRecords = records.listActiveByStudent(
                student.getStudentId());
        if (activeRecords.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(activeRecords.getStatus(), activeRecords.getMessage());
        }
        for (CourseSelectionRecord existing : activeRecords.getData()) {
            ServiceResult<CourseOffering> existingOfferingResult = offerings.findById(
                    existing.getOfferingId());
            if (existingOfferingResult.getStatus() != StatusCode.OK) {
                continue;
            }
            CourseOffering existingOffering = existingOfferingResult.getData();
            if (offering.getCourseId().equals(existingOffering.getCourseId())) {
                return ServiceResult.failure(StatusCode.CONFLICT,
                        "student already selected this course");
            }
            if (offering.getTerm().equals(existingOffering.getTerm())
                    && scheduleConflicts.hasConflict(offering.getMeetingSchedule(),
                            existingOffering.getMeetingSchedule())) {
                return ServiceResult.failure(StatusCode.CONFLICT,
                        "course offering schedule conflicts with an active selection");
            }
        }
        CourseSelectionRecord record = new CourseSelectionRecord(UUID.randomUUID().toString(),
                student.getStudentId(), offering.getOfferingId(), roundResult.getData().getRoundId(),
                selectionType, time);
        return records.create(record);
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> drop(StudentSelectionProfile student,
            String recordId, LocalDateTime time) {
        ServiceResult<Void> profileResult = requireActiveStudent(student, time);
        if (profileResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(profileResult.getStatus(), profileResult.getMessage());
        }
        ServiceResult<CourseSelectionRecord> recordResult = records.findById(recordId);
        if (recordResult.getStatus() != StatusCode.OK) {
            return recordResult;
        }
        CourseSelectionRecord record = recordResult.getData();
        if (!student.getStudentId().equals(record.getStudentId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "selection record does not belong to student");
        }
        ServiceResult<SelectionRound> roundResult = rounds.findById(record.getRoundId());
        if (roundResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(roundResult.getStatus(), roundResult.getMessage());
        }
        if (!roundResult.getData().isOpenAt(time)) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "selection round is not open");
        }
        return records.markDropped(record.getRecordId(), time);
    }

    @Override
    public synchronized ServiceResult<List<SelectedCourseOffering>> listSelectedOfferings(
            StudentSelectionProfile student) {
        if (student == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "student profile must not be null");
        }
        ServiceResult<List<CourseSelectionRecord>> recordResult = records.listActiveByStudent(
                student.getStudentId());
        if (recordResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(recordResult.getStatus(), recordResult.getMessage());
        }
        List<SelectedCourseOffering> result = new ArrayList<SelectedCourseOffering>();
        for (CourseSelectionRecord record : recordResult.getData()) {
            ServiceResult<CourseOffering> offeringResult = offerings.findById(record.getOfferingId());
            if (offeringResult.getStatus() != StatusCode.OK) {
                continue;
            }
            ServiceResult<Course> courseResult = courses.findById(offeringResult.getData().getCourseId());
            if (courseResult.getStatus() == StatusCode.OK) {
                result.add(new SelectedCourseOffering(record, courseResult.getData(),
                        offeringResult.getData()));
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    private ServiceResult<SelectionRound> availableRound(StudentSelectionProfile student,
            String roundId, LocalDateTime time) {
        ServiceResult<Void> profileResult = requireActiveStudent(student, time);
        if (profileResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(profileResult.getStatus(), profileResult.getMessage());
        }
        if (roundId == null || roundId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "roundId must not be blank");
        }
        ServiceResult<SelectionRound> roundResult = rounds.findById(roundId);
        if (roundResult.getStatus() != StatusCode.OK) {
            return roundResult;
        }
        SelectionRound round = roundResult.getData();
        if (!student.getCurrentTerm().equals(round.getTerm()) || !round.isOpenAt(time)) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "selection round is not open");
        }
        return roundResult;
    }

    private ServiceResult<Map<String, SelectionType>> eligibleCourseTypes(
            StudentSelectionProfile student, SelectionRound round) {
        if (round.getType() == SelectionRoundType.RETAKE) {
            if (student.getPendingRetakeCourseIds().isEmpty()) {
                return ServiceResult.failure(StatusCode.FORBIDDEN,
                        "student has no pending retake courses");
            }
            Map<String, SelectionType> retakes = new LinkedHashMap<String, SelectionType>();
            for (String courseId : student.getPendingRetakeCourseIds()) {
                retakes.put(courseId, SelectionType.RETAKE);
            }
            return ServiceResult.ok(retakes);
        }
        ServiceResult<List<TrainingPlanCourse>> ownPlanResult = trainingPlans
                .listCoursesByRecommendedTerm(student.getMajorName(), student.getEnrollmentYear(),
                        student.getRecommendedTerm());
        if (ownPlanResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(ownPlanResult.getStatus(), ownPlanResult.getMessage());
        }
        Map<String, SelectionType> result = new LinkedHashMap<String, SelectionType>();
        for (TrainingPlanCourse course : ownPlanResult.getData()) {
            result.put(course.getCourseId(), course.getSelectionType());
        }
        ServiceResult<List<TrainingPlan>> allPlansResult = trainingPlans.listAll();
        if (allPlansResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(allPlansResult.getStatus(), allPlansResult.getMessage());
        }
        for (TrainingPlan plan : allPlansResult.getData()) {
            if (plan.getStatus() != TrainingPlanStatus.PUBLISHED
                    || plan.getEnrollmentYear() != student.getEnrollmentYear()
                    || plan.getMajorName().equals(student.getMajorName())) {
                continue;
            }
            for (TrainingPlanCourse course : plan.getCourses()) {
                if (course.getRecommendedTerm() == student.getRecommendedTerm()
                        && course.isCrossMajorAllowed() && !result.containsKey(course.getCourseId())) {
                    result.put(course.getCourseId(), SelectionType.CROSS_MAJOR);
                }
            }
        }
        return result.isEmpty()
                ? ServiceResult.<Map<String, SelectionType>>failure(StatusCode.NOT_FOUND,
                        "student has no eligible courses in this round")
                : ServiceResult.ok(result);
    }

    private static ServiceResult<Void> requireActiveStudent(StudentSelectionProfile student,
            LocalDateTime time) {
        if (student == null || time == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "student profile and time must not be null");
        }
        return student.isActiveStudent()
                ? ServiceResult.ok(null)
                : ServiceResult.<Void>failure(StatusCode.FORBIDDEN,
                        "only active student can change course selections");
    }
}
