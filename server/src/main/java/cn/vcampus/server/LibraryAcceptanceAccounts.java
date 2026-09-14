package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.store.BankAccount;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;

/** 专项演示账号及虚构档案，不包含真实个人资料；只用于新建的离线验收库。 */
final class LibraryAcceptanceAccounts {
    /** 普通场景账号；多机并发另有八个独立账号。 */
    static final String[] READERS = {"li_empty", "li_dates", "li_quota4", "li_quota5",
            "li_low", "li_exact", "li_rich", "li_free", "li_paid", "li_disabled", "li_teacher"};

    /** 创建账号、档案与初始钱包；保持正式 seed 中的账号和其他模块数据不变。 */
    static void populate(Path database) throws Exception {
        DefaultUserManagementService users = new DefaultUserManagementService(
                new AccessUserRepository(database), new SessionManager(), new InMemoryAuditLogRepository());
        AccessWalletRepository wallet = new AccessWalletRepository(database);
        int index = 0;
        for (String id : READERS) add(database, users, wallet, id, ++index);
        for (int i = 1; i <= 8; i++) add(database, users, wallet, String.format("li_race%02d", i), ++index);
        try (Connection connection = LibraryAcceptanceData.open(database);
                PreparedStatement statement = connection.prepareStatement("UPDATE tblUser SET active=0 WHERE user_id=?")) {
            statement.setString(1, "li_disabled");
            statement.executeUpdate();
        }
    }

    /** 按真实账户服务产生密码哈希；仅写入全新验收库的虚构档案和起始余额。 */
    private static void add(Path database, DefaultUserManagementService users, AccessWalletRepository wallet,
            String id, int index) throws Exception {
        Role role = "li_teacher".equals(id) ? Role.TEACHER : Role.STUDENT;
        LibraryAcceptanceData.require(users.provisionAccount(new UserCredentials(id, "Demo123", "验收-" + id, role.name())));
        try (Connection connection = LibraryAcceptanceData.open(database)) {
            String sql = role == Role.TEACHER
                    ? "INSERT INTO tblTeacher(teacher_id,user_id,teacher_name,department_name,title,active) VALUES(?,?,?,?,?,1)"
                    : "INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,major_name,"
                            + "class_id,enrollment_year,status,phone,email) VALUES(?,?,?,'未知',?,'计算机科学与技术',"
                            + "'CS2026-01',2026,'在读','','')";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, "LI" + String.format("%06d", index));
                statement.setString(2, id);
                statement.setString(3, "验收-" + id);
                statement.setString(4, "计算机科学与工程学院");
                if (role == Role.TEACHER) statement.setString(5, "讲师");
                statement.executeUpdate();
            }
        }
        long cents = id.equals("li_low") ? 3949 : id.equals("li_exact") ? 3950
                : id.equals("li_free") || id.equals("li_empty") ? 0 : 10000;
        if (!wallet.save(new BankAccount(id, cents))) throw new IllegalStateException("wallet seed failed");
    }
}
