package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public final class AccessMajorDirectoryService implements MajorDirectoryService {
    private final Path database;
    public AccessMajorDirectoryService(Path database) { this.database = Objects.requireNonNull(database).toAbsolutePath(); }
    @Override public ServiceResult<List<MajorDirectoryEntry>> listActive() {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT major_id,major_name,department_name FROM tblMajor WHERE active=true");
                ResultSet result = statement.executeQuery()) {
            List<MajorDirectoryEntry> entries = new ArrayList<>();
            while (result.next()) entries.add(new MajorDirectoryEntry(result.getString("major_id"),
                    result.getString("major_name"), result.getString("department_name"), true));
            entries.sort(MajorDirectoryEntry.ORDER);
            return ServiceResult.ok(Collections.unmodifiableList(entries));
        } catch (SQLException | IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "专业目录不可用，请检查数据库专业目录表及数据");
        }
    }
}
