package cn.vcampus.server;

import cn.vcampus.store.BankAccount;
import cn.vcampus.store.BankAccountRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Access 银行账户仓库，使用参数化 JDBC 语句。
 * 余额以「分」为单位的 long 存储（balance_cents BIGINT）。
 * credit 采用「懒创建防并发 upsert」：先 UPDATE 累加，0 行再 INSERT，撞主键再 UPDATE；
 * debit 用 WHERE balance_cents>=? 守卫，0 行即返回 false 且不改余额。
 */
public final class AccessBankAccountRepository implements BankAccountRepository {
    private final Path databasePath;

    public AccessBankAccountRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public BankAccount findByUserId(String userId) {
        String sql = "SELECT user_id,balance_cents FROM tblBankAccount WHERE user_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next())
                    return null;
                return new BankAccount(results.getString("user_id"), results.getLong("balance_cents"));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find bank account", failure);
        }
    }

    @Override
    public boolean save(BankAccount account) {
        if (account == null)
            return false;
        // upsert：先 UPDATE，0 行再 INSERT，撞主键再 UPDATE
        if (updateBalance(account.getUserId(), account.getBalanceCents()))
            return true;
        try {
            return insert(account.getUserId(), account.getBalanceCents());
        } catch (IllegalStateException conflict) {
            return updateBalance(account.getUserId(), account.getBalanceCents());
        }
    }

    @Override
    public boolean credit(String userId, long cents) {
        // 懒创建防并发：UPDATE 累加 → 0 行则 INSERT → 撞主键再 UPDATE
        if (accumulate(userId, cents))
            return true;
        try {
            return insert(userId, cents);
        } catch (IllegalStateException conflict) {
            return accumulate(userId, cents);
        }
    }

    @Override
    public boolean debit(String userId, long cents) {
        // 守卫：balance_cents>=cents 才扣，0 行即返回 false 且不改余额（防透支）
        String sql = "UPDATE tblBankAccount SET balance_cents=balance_cents-? WHERE user_id=? AND balance_cents>=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            statement.setLong(3, cents);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to debit bank account", failure);
        }
    }

    @Override
    public boolean setBalance(String userId, long cents) {
        // 绝对设置：UPDATE，0 行则 INSERT
        if (updateBalance(userId, cents))
            return true;
        try {
            return insert(userId, cents);
        } catch (IllegalStateException conflict) {
            return updateBalance(userId, cents);
        }
    }

    private boolean accumulate(String userId, long cents) {
        String sql = "UPDATE tblBankAccount SET balance_cents=balance_cents+? WHERE user_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to credit bank account", failure);
        }
    }

    private boolean updateBalance(String userId, long cents) {
        String sql = "UPDATE tblBankAccount SET balance_cents=? WHERE user_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to set bank account balance", failure);
        }
    }

    private boolean insert(String userId, long cents) {
        String sql = "INSERT INTO tblBankAccount(user_id,balance_cents) VALUES(?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setLong(2, cents);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to insert bank account", failure);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }
}
