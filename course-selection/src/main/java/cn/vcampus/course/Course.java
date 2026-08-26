package cn.vcampus.course;

import java.io.Serializable;

/**
 * A course that students can select.
 *
 * <p>The number of enrolled students belongs to the selection records, rather
 * than to this immutable course description.</p>
 */
public final class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final String name;
    private final int credits;
    private final int capacity;

    public Course(String courseId, String name, int credits, int capacity) {
        this.courseId = requireText(courseId, "courseId");
        this.name = requireText(name, "name");
        if (credits <= 0) {
            throw new IllegalArgumentException("credits must be positive");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.credits = credits;
        this.capacity = capacity;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public int getCredits() {
        return credits;
    }

    public int getCapacity() {
        return capacity;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
