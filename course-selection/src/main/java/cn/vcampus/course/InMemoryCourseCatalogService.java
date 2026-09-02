package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于开发和测试的内存课程目录服务。
 *
 * <p>停用课程会保留在目录中以支持历史查询，但不会被当作可新建培养方案或教学班的课程。
 * 程序关闭后目录数据会丢失；后续接入 Access 时应保持 {@link CourseCatalogService} 不变。</p>
 */
public final class InMemoryCourseCatalogService implements CourseCatalogService {
    private final Map<String, Course> coursesById;

    public InMemoryCourseCatalogService() {
        this(Collections.<Course>emptyList());
    }

    public InMemoryCourseCatalogService(List<Course> courses) {
        if (courses == null) {
            throw new IllegalArgumentException("courses must not be null");
        }
        this.coursesById = new LinkedHashMap<String, Course>();
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
    public synchronized ServiceResult<Course> create(Course course) {
        if (course == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "course must not be null");
        }
        if (course.getStatus() != CourseStatus.ACTIVE) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "new course must be created as ACTIVE");
        }
        if (coursesById.containsKey(course.getCourseId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course already exists");
        }
        coursesById.put(course.getCourseId(), course);
        return ServiceResult.ok(course);
    }

    @Override
    public synchronized ServiceResult<Course> findById(String courseId) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "courseId must not be blank");
        }
        Course course = coursesById.get(normalizedCourseId);
        if (course == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
        }
        return ServiceResult.ok(course);
    }

    @Override
    public synchronized ServiceResult<Course> findActiveById(String courseId) {
        ServiceResult<Course> courseResult = findById(courseId);
        if (courseResult.getStatus() != StatusCode.OK) {
            return courseResult;
        }
        if (courseResult.getData().getStatus() != CourseStatus.ACTIVE) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course is disabled");
        }
        return courseResult;
    }

    @Override
    public synchronized ServiceResult<List<Course>> listAll() {
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<Course>(
                coursesById.values())));
    }

    @Override
    public synchronized ServiceResult<List<Course>> listActive() {
        List<Course> activeCourses = new ArrayList<Course>();
        for (Course course : coursesById.values()) {
            if (course.getStatus() == CourseStatus.ACTIVE) {
                activeCourses.add(course);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(activeCourses));
    }

    @Override
    public synchronized ServiceResult<Course> updateDetails(String courseId, String name,
            int credits) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "courseId must not be blank");
        }
        Course existing = coursesById.get(normalizedCourseId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
        }
        try {
            Course changed = existing.withDetails(name, credits);
            coursesById.put(normalizedCourseId, changed);
            return ServiceResult.ok(changed);
        } catch (IllegalArgumentException invalidDetails) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidDetails.getMessage());
        }
    }

    @Override
    public synchronized ServiceResult<Course> changeStatus(String courseId, CourseStatus status) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "courseId and status must not be null");
        }
        Course existing = coursesById.get(normalizedCourseId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
        }
        Course changed = existing.withStatus(status);
        coursesById.put(normalizedCourseId, changed);
        return ServiceResult.ok(changed);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
