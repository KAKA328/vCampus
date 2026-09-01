package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access-backed student record service. */
public final class AccessStudentManagementService implements StudentManagementService {
    private final Path databasePath;

    public AccessStudentManagementService(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<StudentRecord> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        String sql = "SELECT student_id,user_id,student_name,gender,department_name,major_name,class_id,"
                + "enrollment_year,status,phone,email FROM tblStudent WHERE user_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? ServiceResult.ok(map(rs))
                        : ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student not found");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read student by user id", failure);
        }
    }

    @Override
    public ServiceResult<StudentRecord> findById(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        String sql = "SELECT student_id,user_id,student_name,gender,department_name,major_name,class_id,"
                + "enrollment_year,status,phone,email FROM tblStudent WHERE student_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? ServiceResult.ok(map(rs))
                        : ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student not found");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read student", failure);
        }
    }

    @Override
    public ServiceResult<List<StudentRecord>> findByClass(String classId) {
        if (classId == null || classId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "classId must not be blank");
        }
        String sql = "SELECT student_id,user_id,student_name,gender,department_name,major_name,class_id,"
                + "enrollment_year,status,phone,email FROM tblStudent WHERE class_id=? ORDER BY student_id";
        List<StudentRecord> records = new ArrayList<StudentRecord>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, classId.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) records.add(map(rs));
            }
            return ServiceResult.ok(records);
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to list students by class", failure);
        }
    }

    @Override
    public ServiceResult<StudentRecord> save(StudentRecord record) {
        if (record == null || record.getStudentId() == null || record.getStudentId().trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "student record is invalid");
        }
        if (findById(record.getStudentId()).getStatus() == StatusCode.OK) {
            return update(record);
        }
        return insert(record);
    }

    private ServiceResult<StudentRecord> insert(StudentRecord record) {
        String sql = "INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,"
                + "major_name,class_id,enrollment_year,status,phone,email) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, record);
            statement.executeUpdate();
            return ServiceResult.ok(record);
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to create student", failure);
        }
    }

    private ServiceResult<StudentRecord> update(StudentRecord record) {
        String sql = "UPDATE tblStudent SET student_name=?,gender=?,department_name=?,major_name=?,"
                + "class_id=?,enrollment_year=?,status=?,phone=?,email=? WHERE student_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getName());
            statement.setString(2, record.getGender());
            statement.setString(3, record.getDepartmentName());
            statement.setString(4, record.getMajorName());
            statement.setString(5, record.getClassId());
            statement.setInt(6, record.getEnrollmentYear());
            statement.setString(7, record.getStatus());
            statement.setString(8, record.getPhone());
            statement.setString(9, record.getEmail());
            statement.setString(10, record.getStudentId());
            return statement.executeUpdate() > 0 ? ServiceResult.ok(record)
                    : ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student not found");
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to update student", failure);
        }
    }

    private static void bind(PreparedStatement statement, StudentRecord record) throws SQLException {
        statement.setString(1, record.getStudentId());
        statement.setString(2, record.getStudentId());
        statement.setString(3, record.getName());
        statement.setString(4, record.getGender());
        statement.setString(5, record.getDepartmentName());
        statement.setString(6, record.getMajorName());
        statement.setString(7, record.getClassId());
        statement.setInt(8, record.getEnrollmentYear());
        statement.setString(9, record.getStatus());
        statement.setString(10, record.getPhone());
        statement.setString(11, record.getEmail());
    }

    private static StudentRecord map(ResultSet rs) throws SQLException {
        return new StudentRecord(rs.getString("student_id"), rs.getString("student_name"),
                rs.getString("gender"), rs.getString("department_name"), rs.getString("major_name"),
                rs.getString("class_id"), rs.getInt("enrollment_year"), rs.getString("status"),
                rs.getString("phone"), rs.getString("email"));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath);
    }
}
