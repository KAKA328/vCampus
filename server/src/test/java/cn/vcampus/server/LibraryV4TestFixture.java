package cn.vcampus.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 只操作 JUnit 临时数据库；复用正式迁移 SQL，避免测试假表掩盖生产 DDL 问题。 */
final class LibraryV4TestFixture {
    static Path migration() {
        Path path = Paths.get("database/migrations/017_library_copies_v4.up.sql");
        if (!Files.exists(path)) path = Paths.get("../database/migrations/017_library_copies_v4.up.sql");
        return path;
    }
    static void create(Path database) throws Exception {
        LibraryLimitAccessFixture.create(database);
        enable(database);
    }
    static Connection open(Path database) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        return DriverManager.getConnection("jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }
    static void enable(Path database) throws Exception {
        StringBuilder ddl = new StringBuilder();
        for (String line : Files.readAllLines(migration(), StandardCharsets.UTF_8)) {
            if (!line.trim().startsWith("--")) ddl.append(line).append('\n');
        }
        try (Connection connection = open(database)) {
            for (String sql : ddl.toString().split(";")) {
                if (!sql.trim().isEmpty()) try (Statement statement = connection.createStatement()) { statement.execute(sql); }
            }
        }
    }
    static long count(Path database, String table) throws Exception {
        try (Connection connection = open(database); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next(); return rows.getLong(1);
        }
    }
    static void verify(Path database) throws Exception {
        try (Connection connection = open(database)) { LibraryCopyIntegrity.verify(connection); }
    }
    static void paymentTables(Path database) throws Exception {
        Path path = migration().getParent().getParent().resolve("schema.sql");
        String schema = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        try (Connection connection = open(database)) {
            for (String table : new String[] {"tblBankAccount", "tblWalletTransaction", "tblLibraryCompensation"}) {
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("CREATE TABLE " + table + " \\([\\s\\S]*?\\);").matcher(schema);
                if (!match.find()) throw new IllegalStateException("missing production table " + table);
                try (Statement statement = connection.createStatement()) { statement.execute(match.group()); }
            }
        }
    }
}
