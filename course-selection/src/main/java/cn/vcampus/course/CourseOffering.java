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
    private final CourseSchedule meetingSchedule;

    public CourseOffering(String offeringId, String courseId, String term, String teacherId,
            String schedule, String location, int requiredCapacity, int electiveCapacity,
            int crossMajorCapacity, CourseOfferingStatus status) {
        this(offeringId, courseId, term, teacherId, schedule, location, requiredCapacity,
                electiveCapacity, crossMajorCapacity, status, CourseSchedule.empty());
    }

    private CourseOffering(String offeringId, String courseId, String term, String teacherId,
            String schedule, String location, int requiredCapacity, int electiveCapacity,
            int crossMajorCapacity, CourseOfferingStatus status, CourseSchedule meetingSchedule) {
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
        if (meetingSchedule == null) {
            throw new IllegalArgumentException("meetingSchedule must not be null");
        }
        this.requiredCapacity = requiredCapacity;
        this.electiveCapacity = electiveCapacity;
        this.crossMajorCapacity = crossMajorCapacity;
        this.status = status;
        this.meetingSchedule = meetingSchedule;
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

    /**
     * 返回可用于时间冲突检测的结构化上课时间表。
     *
     * <p>旧教学班尚未配置时返回空时间表；原有的 {@link #getSchedule()} 继续用于显示文本。</p>
     */
    public CourseSchedule getMeetingSchedule() {
        return meetingSchedule;
    }

    /**
     * 返回状态更新后的新教学班对象，原对象保持不变。
     */
    public CourseOffering withStatus(CourseOfferingStatus newStatus) {
        return new CourseOffering(offeringId, courseId, term, teacherId, schedule, location,
                requiredCapacity, electiveCapacity, crossMajorCapacity, newStatus, meetingSchedule);
    }

    /**
     * 返回容量更新后的新教学班对象。
     *
     * <p>后续接入实际选课记录后，服务层还需要确保新容量不低于已经选中的人数。</p>
     */
    public CourseOffering withCapacities(int newRequiredCapacity, int newElectiveCapacity,
            int newCrossMajorCapacity) {
        return new CourseOffering(offeringId, courseId, term, teacherId, schedule, location,
                newRequiredCapacity, newElectiveCapacity, newCrossMajorCapacity, status,
                meetingSchedule);
    }

    /**
     * 返回任课老师或上课地点更新后的教学班。
     *
     * <p>本方法刻意不接收上课时间参数，避免普通教学班维护误改既有的上课安排。</p>
     */
    public CourseOffering withTeachingInfo(String newTeacherId, String newLocation) {
        return new CourseOffering(offeringId, courseId, term, newTeacherId, schedule, newLocation,
                requiredCapacity, electiveCapacity, crossMajorCapacity, status, meetingSchedule);
    }

    /** 返回附加结构化上课时间表后的新教学班对象。 */
    public CourseOffering withMeetingSchedule(CourseSchedule newMeetingSchedule) {
        return new CourseOffering(offeringId, courseId, term, teacherId, schedule, location,
                requiredCapacity, electiveCapacity, crossMajorCapacity, status, newMeetingSchedule);
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
