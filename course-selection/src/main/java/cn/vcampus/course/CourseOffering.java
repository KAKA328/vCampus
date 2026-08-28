package cn.vcampus.course;

import java.io.Serializable;

/**
 * 某门课程在一个学期内实际开设的教学班。
 *
 * <p>{@link Course} 表示稳定的课程目录信息，例如“Java 程序设计”；教学班则表示某个学期的
 * 具体班次，包含任课教师、上课安排和不同选课类别可使用的名额。重修学生保留重修身份，
 * 但与正常必修学生共用 {@link CapacityBucket#REQUIRED} 容量池。</p>
 */
public final class CourseOffering implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String offeringId;
    private final String courseId;
    private final String term;
    private final String teacherId;
    private final String schedule;
    private final String location;
    private final int requiredCapacity;
    private final int electiveCapacity;
    private final int crossMajorCapacity;
    private final CourseOfferingStatus status;

    public CourseOffering(String offeringId, String courseId, String term, String teacherId,
            String schedule, String location, int requiredCapacity, int electiveCapacity,
            int crossMajorCapacity, CourseOfferingStatus status) {
        this.offeringId = requireText(offeringId, "offeringId");
        this.courseId = requireText(courseId, "courseId");
        this.term = requireText(term, "term");
        this.teacherId = requireText(teacherId, "teacherId");
        this.schedule = requireText(schedule, "schedule");
        this.location = requireText(location, "location");
        validateCapacity(requiredCapacity, "requiredCapacity");
        validateCapacity(electiveCapacity, "electiveCapacity");
        validateCapacity(crossMajorCapacity, "crossMajorCapacity");
        if (requiredCapacity + electiveCapacity + crossMajorCapacity == 0) {
            throw new IllegalArgumentException("at least one capacity must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.requiredCapacity = requiredCapacity;
        this.electiveCapacity = electiveCapacity;
        this.crossMajorCapacity = crossMajorCapacity;
        this.status = status;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getTerm() {
        return term;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getLocation() {
        return location;
    }

    public int getRequiredCapacity() {
        return requiredCapacity;
    }

    public int getElectiveCapacity() {
        return electiveCapacity;
    }

    public int getCrossMajorCapacity() {
        return crossMajorCapacity;
    }

    /**
     * 按容量池返回该教学班可供分配的初始名额。
     */
    public int getCapacity(CapacityBucket capacityBucket) {
        if (capacityBucket == null) {
            throw new IllegalArgumentException("capacityBucket must not be null");
        }
        switch (capacityBucket) {
            case REQUIRED:
                return requiredCapacity;
            case ELECTIVE:
                return electiveCapacity;
            case CROSS_MAJOR:
                return crossMajorCapacity;
            default:
                throw new IllegalArgumentException("unsupported capacity bucket");
        }
    }

    public int getTotalCapacity() {
        return requiredCapacity + electiveCapacity + crossMajorCapacity;
    }

    public CourseOfferingStatus getStatus() {
        return status;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static void validateCapacity(int capacity, String fieldName) {
        if (capacity < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
