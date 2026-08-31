package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A course selection service that keeps course and selection data in memory.
 *
 * <p>This implementation is intended for the first development stage. Its data
 * disappears when the program stops; a later database-backed implementation can
 * keep the same {@link CourseSelectionService} interface.</p>
 */
public final class InMemoryCourseSelectionService implements CourseSelectionService {
    private final Map<String, Course> coursesById;
    private final Map<String, Set<String>> courseIdsByStudent;
    private final Map<String, SelectionRound> roundsById;
    private final Map<String, CourseOffering> offeringsById;
    private final Map<String, CourseSelectionRecord> recordsById;
    private final Map<String, Set<String>> recordIdsByStudent;

    /** Creates a service with a small set of courses for local development. */
    public InMemoryCourseSelectionService() {
        this(defaultCourses());
    }

    private static List<Course> defaultCourses() {
        return Arrays.asList(
                new Course("JAVA101", "Java 程序设计", 3, 40),
                new Course("DB101", "数据库原理", 3, 40),
                new Course("NET101", "计算机网络", 3, 30));
    }

    /**
     * Creates a service with the supplied courses.
     *
     * @param courses initial courses; course ids must not be duplicated
     */
    public InMemoryCourseSelectionService(List<Course> courses) {
        if (courses == null) {
            throw new IllegalArgumentException("courses must not be null");
        }

        this.coursesById = new LinkedHashMap<String, Course>();
        this.courseIdsByStudent = new LinkedHashMap<String, Set<String>>();
        this.roundsById = new LinkedHashMap<String, SelectionRound>();
        this.offeringsById = new LinkedHashMap<String, CourseOffering>();
        this.recordsById = new LinkedHashMap<String, CourseSelectionRecord>();
        this.recordIdsByStudent = new LinkedHashMap<String, Set<String>>();
        for (Course course : courses) {
            if (course == null) {
                throw new IllegalArgumentException("courses must not contain null");
            }
            if (coursesById.put(course.getCourseId(), course) != null) {
                throw new IllegalArgumentException("duplicate courseId: " + course.getCourseId());
            }
        }
        seedProtocolV2Data();
    }

    @Override
    public synchronized ServiceResult<List<Course>> listCourses() {
        return ServiceResult.ok(Collections.unmodifiableList(
                new ArrayList<Course>(coursesById.values())));
    }

    @Override
    public synchronized ServiceResult<Void> select(String studentId, String courseId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedStudentId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "studentId and courseId must not be blank");
        }

        Course course = coursesById.get(normalizedCourseId);
        if (course == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
        }

        Set<String> selectedCourseIds = courseIdsByStudent.get(normalizedStudentId);
        if (selectedCourseIds != null && selectedCourseIds.contains(normalizedCourseId)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course is already selected");
        }
        if (selectedCount(normalizedCourseId) >= course.getCapacity()) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course is full");
        }

        if (selectedCourseIds == null) {
            selectedCourseIds = new LinkedHashSet<String>();
            courseIdsByStudent.put(normalizedStudentId, selectedCourseIds);
        }
        selectedCourseIds.add(normalizedCourseId);
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<Void> drop(String studentId, String courseId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedStudentId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "studentId and courseId must not be blank");
        }
        if (!coursesById.containsKey(normalizedCourseId)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
        }

        Set<String> selectedCourseIds = courseIdsByStudent.get(normalizedStudentId);
        if (selectedCourseIds == null || !selectedCourseIds.remove(normalizedCourseId)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course selection not found");
        }
        if (selectedCourseIds.isEmpty()) {
            courseIdsByStudent.remove(normalizedStudentId);
        }
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<List<Course>> selectedCourses(String studentId) {
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }

        Set<String> selectedCourseIds = courseIdsByStudent.get(normalizedStudentId);
        if (selectedCourseIds == null) {
            return ServiceResult.ok(Collections.<Course>emptyList());
        }

        List<Course> selectedCourses = new ArrayList<Course>();
        for (String selectedCourseId : selectedCourseIds) {
            selectedCourses.add(coursesById.get(selectedCourseId));
        }
        return ServiceResult.ok(Collections.unmodifiableList(selectedCourses));
    }

    @Override
    public synchronized ServiceResult<List<SelectionRound>> listRounds(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term must not be blank");
        }

        List<SelectionRound> rounds = new ArrayList<SelectionRound>();
        for (SelectionRound round : roundsById.values()) {
            if (normalizedTerm.equals(round.getTerm())) {
                rounds.add(round);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(rounds));
    }

    @Override
    public synchronized ServiceResult<List<CourseOffering>> listOfferings(String roundId, String courseId) {
        SelectionRound round = roundsById.get(normalize(roundId));
        if (round == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection round not found");
        }

        String normalizedCourseId = normalize(courseId);
        List<CourseOffering> offerings = new ArrayList<CourseOffering>();
        for (CourseOffering offering : offeringsById.values()) {
            boolean sameTerm = round.getTerm().equals(offering.getTerm());
            boolean sameCourse = normalizedCourseId == null || normalizedCourseId.equals(offering.getCourseId());
            if (sameTerm && sameCourse && offering.getStatus() == CourseOfferingStatus.OPEN) {
                offerings.add(offering);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(offerings));
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> selectOffering(
            String studentId, String roundId, String offeringId) {
        String normalizedStudentId = normalize(studentId);
        SelectionRound round = roundsById.get(normalize(roundId));
        CourseOffering offering = offeringsById.get(normalize(offeringId));
        if (normalizedStudentId == null || round == null || offering == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection round or offering not found");
        }
        if (!round.isOpenAt(LocalDateTime.now()) || offering.getStatus() != CourseOfferingStatus.OPEN) {
            return ServiceResult.failure(StatusCode.CONFLICT, "selection round or offering is closed");
        }
        if (!round.getTerm().equals(offering.getTerm())) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offering does not belong to round term");
        }
        if (hasSelectedOffering(normalizedStudentId, offering.getOfferingId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "offering is already selected");
        }

        SelectionType selectionType = selectionTypeFor(round);
        if (selectedOfferingCount(offering.getOfferingId(), selectionType.getCapacityBucket())
                >= offering.getCapacity(selectionType.getCapacityBucket())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "offering is full");
        }

        CourseSelectionRecord record = new CourseSelectionRecord("SEL-" + UUID.randomUUID().toString(),
                normalizedStudentId, round.getRoundId(), offering.getOfferingId(),
                offering.getCourseId(), selectionType, LocalDateTime.now());
        recordsById.put(record.getRecordId(), record);
        Set<String> recordIds = recordIdsByStudent.get(normalizedStudentId);
        if (recordIds == null) {
            recordIds = new LinkedHashSet<String>();
            recordIdsByStudent.put(normalizedStudentId, recordIds);
        }
        recordIds.add(record.getRecordId());
        addLegacyCourseSelection(normalizedStudentId, offering.getCourseId());
        return ServiceResult.ok(record);
    }

    @Override
    public synchronized ServiceResult<Void> dropRecord(String studentId, String recordId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedRecordId = normalize(recordId);
        if (normalizedStudentId == null || normalizedRecordId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId and recordId must not be blank");
        }
        CourseSelectionRecord record = recordsById.get(normalizedRecordId);
        if (record == null || !normalizedStudentId.equals(record.getStudentId())) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course selection record not found");
        }

        recordsById.remove(normalizedRecordId);
        Set<String> recordIds = recordIdsByStudent.get(normalizedStudentId);
        if (recordIds != null) {
            recordIds.remove(normalizedRecordId);
            if (recordIds.isEmpty()) {
                recordIdsByStudent.remove(normalizedStudentId);
            }
        }
        if (!hasSelectedCourseRecord(normalizedStudentId, record.getCourseId())) {
            removeLegacyCourseSelection(normalizedStudentId, record.getCourseId());
        }
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<List<CourseSelectionRecord>> selectedRecords(String studentId, String term) {
        String normalizedStudentId = normalize(studentId);
        String normalizedTerm = normalize(term);
        if (normalizedStudentId == null || normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId and term must not be blank");
        }

        Set<String> recordIds = recordIdsByStudent.get(normalizedStudentId);
        if (recordIds == null) {
            return ServiceResult.ok(Collections.<CourseSelectionRecord>emptyList());
        }
        List<CourseSelectionRecord> records = new ArrayList<CourseSelectionRecord>();
        for (String recordId : recordIds) {
            CourseSelectionRecord record = recordsById.get(recordId);
            CourseOffering offering = record == null ? null : offeringsById.get(record.getOfferingId());
            if (offering != null && normalizedTerm.equals(offering.getTerm())) {
                records.add(record);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(records));
    }

    private int selectedCount(String courseId) {
        int count = 0;
        for (Set<String> selectedCourseIds : courseIdsByStudent.values()) {
            if (selectedCourseIds.contains(courseId)) {
                count++;
            }
        }
        return count;
    }

    private void seedProtocolV2Data() {
        LocalDateTime now = LocalDateTime.now();
        roundsById.put("ROUND-INITIAL", new SelectionRound("ROUND-INITIAL", "2026-2027-1",
                SelectionRoundType.INITIAL, now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN));
        roundsById.put("ROUND-RETAKE", new SelectionRound("ROUND-RETAKE", "2026-2027-1",
                SelectionRoundType.RETAKE, now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN));
        for (Course course : coursesById.values()) {
            String offeringId = "OFFER-" + course.getCourseId() + "-01";
            offeringsById.put(offeringId, new CourseOffering(offeringId, course.getCourseId(),
                    "2026-2027-1", "demo_teacher", "周一 1-2 节", "教学楼 A201",
                    course.getCapacity(), 0, 0, CourseOfferingStatus.OPEN));
        }
    }

    private boolean hasSelectedOffering(String studentId, String offeringId) {
        Set<String> recordIds = recordIdsByStudent.get(studentId);
        if (recordIds == null) {
            return false;
        }
        for (String recordId : recordIds) {
            CourseSelectionRecord record = recordsById.get(recordId);
            if (record != null && offeringId.equals(record.getOfferingId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSelectedCourseRecord(String studentId, String courseId) {
        Set<String> recordIds = recordIdsByStudent.get(studentId);
        if (recordIds == null) {
            return false;
        }
        for (String recordId : recordIds) {
            CourseSelectionRecord record = recordsById.get(recordId);
            if (record != null && courseId.equals(record.getCourseId())) {
                return true;
            }
        }
        return false;
    }

    private int selectedOfferingCount(String offeringId, CapacityBucket bucket) {
        int count = 0;
        for (CourseSelectionRecord record : recordsById.values()) {
            if (offeringId.equals(record.getOfferingId())
                    && record.getSelectionType().getCapacityBucket() == bucket) {
                count++;
            }
        }
        return count;
    }

    private static SelectionType selectionTypeFor(SelectionRound round) {
        return round.getType() == SelectionRoundType.RETAKE ? SelectionType.RETAKE : SelectionType.REQUIRED;
    }

    private void addLegacyCourseSelection(String studentId, String courseId) {
        Set<String> selectedCourseIds = courseIdsByStudent.get(studentId);
        if (selectedCourseIds == null) {
            selectedCourseIds = new LinkedHashSet<String>();
            courseIdsByStudent.put(studentId, selectedCourseIds);
        }
        selectedCourseIds.add(courseId);
    }

    private void removeLegacyCourseSelection(String studentId, String courseId) {
        Set<String> selectedCourseIds = courseIdsByStudent.get(studentId);
        if (selectedCourseIds == null) {
            return;
        }
        selectedCourseIds.remove(courseId);
        if (selectedCourseIds.isEmpty()) {
            courseIdsByStudent.remove(studentId);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
