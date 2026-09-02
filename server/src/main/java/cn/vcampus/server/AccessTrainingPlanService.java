package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.course.TrainingPlanStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 使用 Access 保存培养方案及其课程要求的服务实现。 */
public final class AccessTrainingPlanService implements TrainingPlanService {
    private final Path databasePath;
    private final CourseCatalogService courseCatalog;

    public AccessTrainingPlanService(Path databasePath, CourseCatalogService courseCatalog) {
        if (databasePath == null || courseCatalog == null) {
            throw new IllegalArgumentException("databasePath and courseCatalog must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.courseCatalog = courseCatalog;
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> create(TrainingPlan plan) {
        if (plan == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "plan must not be null");
        if (plan.getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "new training plan must be created as DRAFT");
        }
        ServiceResult<Void> courses = requireActiveCourses(plan.getCourses());
        if (courses.getStatus() != StatusCode.OK) return ServiceResult.failure(courses.getStatus(), courses.getMessage());
        ServiceResult<TrainingPlan> existing = findById(plan.getPlanId());
        if (existing.getStatus() == StatusCode.OK) return ServiceResult.failure(StatusCode.CONFLICT, "training plan already exists");
        if (existing.getStatus() != StatusCode.NOT_FOUND) return existing;
        ServiceResult<TrainingPlan> scoped = findByMajorAndEnrollmentYear(plan.getMajorName(), plan.getEnrollmentYear());
        if (scoped.getStatus() == StatusCode.OK) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "training plan already exists for major and enrollment year");
        }
        if (scoped.getStatus() != StatusCode.NOT_FOUND) return scoped;

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO tblTrainingPlan(plan_id,major_name,enrollment_year,status) VALUES(?,?,?,?)")) {
                    statement.setString(1, plan.getPlanId());
                    statement.setString(2, plan.getMajorName());
                    statement.setInt(3, plan.getEnrollmentYear());
                    statement.setString(4, plan.getStatus().name());
                    statement.executeUpdate();
                }
                for (TrainingPlanCourse course : plan.getCourses()) insertCourse(connection, plan.getPlanId(), course);
                connection.commit();
                return ServiceResult.ok(plan);
            } catch (SQLException failure) {
                rollbackQuietly(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<TrainingPlan> findById(String planId) {
        String normalized = normalize(planId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "planId must not be blank");
        String sql = "SELECT plan_id,major_name,enrollment_year,status FROM tblTrainingPlan WHERE plan_id=?";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
                return ServiceResult.ok(readPlan(connection, results));
            }
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public ServiceResult<List<TrainingPlan>> listAll() {
        String sql = "SELECT plan_id FROM tblTrainingPlan ORDER BY major_name,enrollment_year,plan_id";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            List<TrainingPlan> plans = new ArrayList<TrainingPlan>();
            while (results.next()) {
                ServiceResult<TrainingPlan> plan = findById(results.getString("plan_id"));
                if (plan.getStatus() != StatusCode.OK) return ServiceResult.failure(plan.getStatus(), plan.getMessage());
                plans.add(plan.getData());
            }
            return ServiceResult.ok(Collections.unmodifiableList(plans));
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public ServiceResult<TrainingPlan> findByMajorAndEnrollmentYear(String majorName, int enrollmentYear) {
        String normalizedMajor = normalize(majorName);
        if (normalizedMajor == null || !validYear(enrollmentYear)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "majorName and enrollmentYear are invalid");
        }
        String sql = "SELECT plan_id FROM tblTrainingPlan WHERE major_name=? AND enrollment_year=?";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedMajor);
            statement.setInt(2, enrollmentYear);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? findById(results.getString("plan_id"))
                        : ServiceResult.<TrainingPlan>failure(StatusCode.NOT_FOUND, "training plan not found");
            }
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> changeStatus(String planId, TrainingPlanStatus status) {
        if (normalize(planId) == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "planId and status must not be null");
        }
        ServiceResult<TrainingPlan> existing = findById(planId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        if (!canChangeTo(existing.getData().getStatus(), status)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "training plan status transition is not allowed");
        }
        TrainingPlan changed = existing.getData().withStatus(status);
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblTrainingPlan SET status=? WHERE plan_id=?")) {
            statement.setString(1, status.name());
            statement.setString(2, changed.getPlanId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<TrainingPlan>failure(StatusCode.NOT_FOUND, "training plan not found");
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> saveCourse(String planId, TrainingPlanCourse course) {
        if (normalize(planId) == null || course == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "planId and course must not be null");
        }
        ServiceResult<TrainingPlan> existing = findById(planId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        if (existing.getData().getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.CONFLICT, "only DRAFT training plan can be maintained");
        }
        ServiceResult<Void> active = requireActiveCourse(course.getCourseId());
        if (active.getStatus() != StatusCode.OK) return ServiceResult.failure(active.getStatus(), active.getMessage());
        TrainingPlan changed = existing.getData().withCourse(course);
        String update = "UPDATE tblTrainingPlanCourse SET recommended_term=?,selection_type=?,cross_major_allowed=? "
                + "WHERE plan_id=? AND course_id=?";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setInt(1, course.getRecommendedTerm());
            statement.setString(2, course.getSelectionType().name());
            statement.setBoolean(3, course.isCrossMajorAllowed());
            statement.setString(4, changed.getPlanId());
            statement.setString(5, course.getCourseId());
            if (statement.executeUpdate() == 0) insertCourse(connection, changed.getPlanId(), course);
            return ServiceResult.ok(changed);
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> removeCourse(String planId, String courseId) {
        if (normalize(planId) == null || normalize(courseId) == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "planId and courseId must not be blank");
        }
        ServiceResult<TrainingPlan> existing = findById(planId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        if (existing.getData().getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.CONFLICT, "only DRAFT training plan can be maintained");
        }
        final TrainingPlan changed;
        try { changed = existing.getData().withoutCourse(courseId); }
        catch (IllegalArgumentException invalid) { return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage()); }
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM tblTrainingPlanCourse WHERE plan_id=? AND course_id=?")) {
            statement.setString(1, changed.getPlanId()); statement.setString(2, normalize(courseId));
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<TrainingPlan>failure(StatusCode.NOT_FOUND, "training plan course not found");
        } catch (SQLException failure) { return databaseFailure(failure); }
    }

    @Override
    public ServiceResult<List<TrainingPlanCourse>> listCoursesByRecommendedTerm(String majorName,
            int enrollmentYear, int recommendedTerm) {
        if (recommendedTerm <= 0) return ServiceResult.failure(StatusCode.BAD_REQUEST, "recommendedTerm must be positive");
        ServiceResult<TrainingPlan> plan = findByMajorAndEnrollmentYear(majorName, enrollmentYear);
        if (plan.getStatus() != StatusCode.OK) return ServiceResult.failure(plan.getStatus(), plan.getMessage());
        if (plan.getData().getStatus() != TrainingPlanStatus.PUBLISHED) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "published training plan not found");
        }
        List<TrainingPlanCourse> result = new ArrayList<TrainingPlanCourse>();
        for (TrainingPlanCourse course : plan.getData().getCourses()) {
            if (course.getRecommendedTerm() == recommendedTerm) result.add(course);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    private TrainingPlan readPlan(Connection connection, ResultSet row) throws SQLException {
        String planId = row.getString("plan_id");
        List<TrainingPlanCourse> courses = new ArrayList<TrainingPlanCourse>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT course_id,recommended_term,"
                + "selection_type,cross_major_allowed FROM tblTrainingPlanCourse WHERE plan_id=? "
                + "ORDER BY recommended_term,course_id")) {
            statement.setString(1, planId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) courses.add(new TrainingPlanCourse(results.getString("course_id"),
                        results.getInt("recommended_term"), cn.vcampus.course.SelectionType.valueOf(
                                results.getString("selection_type")), results.getBoolean("cross_major_allowed")));
            }
        }
        return new TrainingPlan(planId, row.getString("major_name"), row.getInt("enrollment_year"),
                courses, TrainingPlanStatus.valueOf(row.getString("status")));
    }

    private ServiceResult<Void> requireActiveCourses(List<TrainingPlanCourse> courses) {
        for (TrainingPlanCourse course : courses) { ServiceResult<Void> result = requireActiveCourse(course.getCourseId()); if (result.getStatus() != StatusCode.OK) return result; }
        return ServiceResult.ok(null);
    }
    private ServiceResult<Void> requireActiveCourse(String courseId) {
        ServiceResult<Course> result = courseCatalog.findActiveById(courseId);
        return result.getStatus() == StatusCode.OK ? ServiceResult.ok(null) : ServiceResult.<Void>failure(result.getStatus(), result.getMessage());
    }
    private static void insertCourse(Connection connection, String planId, TrainingPlanCourse course) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tblTrainingPlanCourse("
                + "plan_id,course_id,recommended_term,selection_type,cross_major_allowed) VALUES(?,?,?,?,?)")) {
            statement.setString(1, planId); statement.setString(2, course.getCourseId());
            statement.setInt(3, course.getRecommendedTerm()); statement.setString(4, course.getSelectionType().name());
            statement.setBoolean(5, course.isCrossMajorAllowed()); statement.executeUpdate();
        }
    }
    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath + ";immediatelyReleaseResources=true"); }
    private static void rollbackQuietly(Connection connection) { try { connection.rollback(); } catch (SQLException ignored) { } }
    private static boolean canChangeTo(TrainingPlanStatus current, TrainingPlanStatus target) { return (current == TrainingPlanStatus.DRAFT && target == TrainingPlanStatus.PUBLISHED) || (current == TrainingPlanStatus.PUBLISHED && target == TrainingPlanStatus.ARCHIVED); }
    private static boolean validYear(int value) { return value >= 1900 && value <= 9999; }
    private static String normalize(String value) { if (value == null) return null; String normalized = value.trim(); return normalized.isEmpty() ? null : normalized; }
    private static <T> ServiceResult<T> databaseFailure(SQLException failure) { return ServiceResult.failure(StatusCode.SERVER_ERROR, "training plan database operation failed: " + failure.getMessage()); }
}
