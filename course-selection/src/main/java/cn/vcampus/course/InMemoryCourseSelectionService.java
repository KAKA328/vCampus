package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** Creates a service with a small set of courses for local development. */
    public InMemoryCourseSelectionService() {
        this(Arrays.asList(
                new Course("JAVA101", "Java 程序设计", 3, 40),
                new Course("DB101", "数据库原理", 3, 40),
                new Course("NET101", "计算机网络", 3, 30)));
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
        for (Course course : courses) {
            if (course == null) {
                throw new IllegalArgumentException("courses must not contain null");
            }
            if (coursesById.put(course.getCourseId(), course) != null) {
                throw new IllegalArgumentException("duplicate courseId: " + course.getCourseId());
            }
        }
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

    private int selectedCount(String courseId) {
        int count = 0;
        for (Set<String> selectedCourseIds : courseIdsByStudent.values()) {
            if (selectedCourseIds.contains(courseId)) {
                count++;
            }
        }
        return count;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
