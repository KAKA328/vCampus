package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

/** 显式离线生成独立验收库；无 Socket 入口，不覆盖或修改任何已有数据库。 */
public final class LibraryAcceptanceData {
    /** 工具类不允许实例化。 */
    private LibraryAcceptanceData() { }

    /** 参数为 --new-demo、仓库根、全新输出文件和验收基准日期（ISO 格式）。 */
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !"--new-demo".equals(args[0])) {
            throw new IllegalArgumentException("Usage: --new-demo repository NEW.accdb YYYY-MM-DD");
        }
        create(Paths.get(args[1]), Paths.get(args[2]), LocalDate.parse(args[3]));
        System.out.println("Verified library acceptance database: " + Paths.get(args[2]).toAbsolutePath());
        System.out.println("Reminder base date: " + args[3] + "; demo-only password: Demo123");
    }

    /** 从正式 schema/seed 建库后追加专项样例；失败文件保留诊断，绝不自动删除。 */
    static void create(Path root, Path output, LocalDate date) throws Exception {
        Path database = output.toAbsolutePath().normalize();
        if (!database.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".accdb")
                || Files.exists(database)) throw new IllegalArgumentException("Output must be a NEW .accdb file");
        if (date == null) throw new IllegalArgumentException("base date is required");
        Path schema = root.resolve("database/schema.sql"), seed = root.resolve("database/seed.sql");
        if (!Files.isRegularFile(schema) || !Files.isRegularFile(seed)) {
            throw new IllegalArgumentException("repository schema/seed not found");
        }
        Files.createDirectories(database.getParent());
        // 原子占用输出名，拒绝并行生成器覆盖同一个目标。
        Files.createFile(database);
        try (com.healthmarketscience.jackcess.Database empty =
                com.healthmarketscience.jackcess.DatabaseBuilder.create(
                        com.healthmarketscience.jackcess.Database.FileFormat.V2010, database.toFile())) {
            // 初始化刚刚独占创建的空文件，不读取任何原演示库。
        }
        try (Connection connection = open(database)) {
            executeScript(connection, schema);
            executeScript(connection, seed);
            connection.setAutoCommit(false);
            LibraryCopySchema.initializeAll(connection);
            connection.commit();
        }
        LibraryAcceptanceAccounts.populate(database);
        new LibraryAcceptanceScenarios(database, date).populate();
        try (Connection connection = open(database)) { LibraryCopyIntegrity.verify(connection); }
    }

    /** 只运行仓库中的已审查建库 SQL，不作为任意用户 SQL 执行接口。 */
    private static void executeScript(Connection connection, Path script) throws Exception {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            if (!line.trim().startsWith("--")) sql.append(line).append('\n');
        }
        for (String command : sql.toString().split(";")) {
            if (!command.trim().isEmpty()) try (Statement statement = connection.createStatement()) {
                statement.execute(command);
            }
        }
    }

    /** 打开短生命周期的验收库连接。 */
    static Connection open(Path database) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        return DriverManager.getConnection("jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }

    /** 初始化失败立即终止，不能输出看似成功的部分数据。 */
    static <T> T require(ServiceResult<T> result) {
        if (result.getStatus() != StatusCode.OK) throw new IllegalStateException(result.getMessage());
        return result.getData();
    }
}
