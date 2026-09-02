package cn.vcampus.course;

import java.io.Serializable;

/** 教务管理员维护课程目录和教学班的 Socket 请求。 */
public final class CourseManagementCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Operation {
        LIST_COURSES,
        LIST_OFFERINGS_BY_TERM,
        CREATE_COURSE,
        UPDATE_COURSE_DETAILS,
        CHANGE_COURSE_STATUS,
        CREATE_OFFERING,
        CHANGE_OFFERING_STATUS,
        CHANGE_OFFERING_CAPACITIES,
        UPDATE_OFFERING_TEACHING_INFO
    }

    private final String token;
    private final Operation operation;
    private final Course course;
    private final CourseOffering offering;
    private final String targetId;
    private final String term;
    private final String name;
    private final int credits;
    private final int requiredCapacity;
    private final int electiveCapacity;
    private final int crossMajorCapacity;
    private final CourseStatus courseStatus;
    private final CourseOfferingStatus offeringStatus;
    private final String teacherId;
    private final String location;

    private CourseManagementCommand(String token, Operation operation, Course course,
            CourseOffering offering, String targetId, String term, String name, int credits,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity,
            CourseStatus courseStatus, CourseOfferingStatus offeringStatus) {
        this(token, operation, course, offering, targetId, term, name, credits, requiredCapacity,
                electiveCapacity, crossMajorCapacity, courseStatus, offeringStatus, null, null);
    }

    private CourseManagementCommand(String token, Operation operation, Course course,
            CourseOffering offering, String targetId, String term, String name, int credits,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity,
            CourseStatus courseStatus, CourseOfferingStatus offeringStatus, String teacherId,
            String location) {
        this.token = requireText(token, "token");
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        this.operation = operation;
        this.course = course;
        this.offering = offering;
        this.targetId = targetId;
        this.term = term;
        this.name = name;
        this.credits = credits;
        this.requiredCapacity = requiredCapacity;
        this.electiveCapacity = electiveCapacity;
        this.crossMajorCapacity = crossMajorCapacity;
        this.courseStatus = courseStatus;
        this.offeringStatus = offeringStatus;
        this.teacherId = teacherId;
        this.location = location;
    }

    public static CourseManagementCommand listCourses(String token) {
        return new CourseManagementCommand(token, Operation.LIST_COURSES, null, null, null, null,
                null, 0, 0, 0, 0, null, null);
    }

    public static CourseManagementCommand listOfferingsByTerm(String token, String term) {
        return new CourseManagementCommand(token, Operation.LIST_OFFERINGS_BY_TERM, null, null,
                null, requireText(term, "term"), null, 0, 0, 0, 0, null, null);
    }

    public static CourseManagementCommand createCourse(String token, Course course) {
        if (course == null) throw new IllegalArgumentException("course must not be null");
        return new CourseManagementCommand(token, Operation.CREATE_COURSE, course, null, null,
                null, null, 0, 0, 0, 0, null, null);
    }

    public static CourseManagementCommand updateCourseDetails(String token, String courseId,
            String name, int credits) {
        if (credits <= 0) throw new IllegalArgumentException("credits must be positive");
        return new CourseManagementCommand(token, Operation.UPDATE_COURSE_DETAILS, null, null,
                requireText(courseId, "courseId"), null, requireText(name, "name"), credits,
                0, 0, 0, null, null);
    }

    public static CourseManagementCommand changeCourseStatus(String token, String courseId,
            CourseStatus status) {
        if (status == null) throw new IllegalArgumentException("course status must not be null");
        return new CourseManagementCommand(token, Operation.CHANGE_COURSE_STATUS, null, null,
                requireText(courseId, "courseId"), null, null, 0, 0, 0, 0, status, null);
    }

    public static CourseManagementCommand createOffering(String token, CourseOffering offering) {
        if (offering == null) throw new IllegalArgumentException("offering must not be null");
        return new CourseManagementCommand(token, Operation.CREATE_OFFERING, null, offering,
                null, null, null, 0, 0, 0, 0, null, null);
    }

    public static CourseManagementCommand changeOfferingStatus(String token, String offeringId,
            CourseOfferingStatus status) {
        if (status == null) throw new IllegalArgumentException("offering status must not be null");
        return new CourseManagementCommand(token, Operation.CHANGE_OFFERING_STATUS, null, null,
                requireText(offeringId, "offeringId"), null, null, 0, 0, 0, 0, null, status);
    }

    public static CourseManagementCommand changeOfferingCapacities(String token, String offeringId,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        if (requiredCapacity < 0 || electiveCapacity < 0 || crossMajorCapacity < 0) {
            throw new IllegalArgumentException("capacities must not be negative");
        }
        return new CourseManagementCommand(token, Operation.CHANGE_OFFERING_CAPACITIES, null,
                null, requireText(offeringId, "offeringId"), null, null, 0, requiredCapacity,
                electiveCapacity, crossMajorCapacity, null, null);
    }

    public static CourseManagementCommand updateOfferingTeachingInfo(String token,
            String offeringId, String teacherId, String location) {
        return new CourseManagementCommand(token, Operation.UPDATE_OFFERING_TEACHING_INFO, null,
                null, requireText(offeringId, "offeringId"), null, null, 0, 0, 0, 0, null, null,
                requireText(teacherId, "teacherId"), requireText(location, "location"));
    }

    public String getToken() { return token; }
    public Operation getOperation() { return operation; }
    public Course getCourse() { return course; }
    public CourseOffering getOffering() { return offering; }
    public String getTargetId() { return targetId; }
    public String getTerm() { return term; }
    public String getName() { return name; }
    public int getCredits() { return credits; }
    public int getRequiredCapacity() { return requiredCapacity; }
    public int getElectiveCapacity() { return electiveCapacity; }
    public int getCrossMajorCapacity() { return crossMajorCapacity; }
    public CourseStatus getCourseStatus() { return courseStatus; }
    public CourseOfferingStatus getOfferingStatus() { return offeringStatus; }
    public String getTeacherId() { return teacherId; }
    public String getLocation() { return location; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
