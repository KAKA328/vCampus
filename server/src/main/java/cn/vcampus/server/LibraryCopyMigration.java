package cn.vcampus.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** 显式离线升级工具：只迁移新复制出来的数据库，从不覆盖或连接写入源数据库。 */
public final class LibraryCopyMigration {
    /** 工具类无需实例。 */
    private LibraryCopyMigration() { }
    /**
     * 使用已停止服务器的源文件创建升级副本。
     * @param args --source-stopped、源文件、全新输出文件、017 SQL 文件
     * @throws Exception 参数、复制或迁移失败；源文件始终保持不变
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !"--source-stopped".equals(args[0])) {
            throw new IllegalArgumentException("Stop all servers first. Usage: --source-stopped source.accdb NEW-output.accdb 017_library_copies_v4.up.sql");
        }
        migrateCopy(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
        System.out.println("Library V4 upgraded COPY: " + Paths.get(args[2]).toAbsolutePath().normalize());
        System.out.println("Source database was not modified. Point the matching new server at this output after verification.");
    }
    /** 输出必须不存在，DDL 或回填失败时留下失败副本供检查，绝不覆盖原文件。 */
    static void migrateCopy(Path source, Path output, Path migrationSql) throws Exception {
        Path original = source.toAbsolutePath().normalize();
        Path destination = output.toAbsolutePath().normalize();
        if (!Files.isRegularFile(original) || original.equals(destination) || Files.exists(destination)) {
            throw new IllegalArgumentException("source must exist and output must be a different NEW file");
        }
        String ddl = new String(Files.readAllBytes(migrationSql), StandardCharsets.UTF_8);
        Files.copy(original, destination);
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + destination + ";immediatelyReleaseResources=true")) {
            if (!LibraryCopySchema.available(connection)) executeDdl(connection, ddl);
            connection.setAutoCommit(false);
            try {
                LibraryCopySchema.initializeAll(connection);
                LibraryCopyIntegrity.verify(connection);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }
    /** 仅执行指定的本地迁移 SQL；不执行 seed，不删表，不自动猜测版本。 */
    private static void executeDdl(Connection connection, String ddl) throws SQLException {
        StringBuilder sql = new StringBuilder();
        for (String line : ddl.split("\\R")) {
            if (!line.trim().startsWith("--")) sql.append(line).append('\n');
        }
        for (String statement : sql.toString().split(";")) {
            if (!statement.trim().isEmpty()) {
                try (Statement command = connection.createStatement()) { command.execute(statement.trim()); }
            }
        }
    }
}
