package cn.vcampus.server;

import cn.vcampus.store.BankAccount;
import cn.vcampus.store.WalletMutation;
import cn.vcampus.store.WalletRepository;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Access 钱包仓库：把「余额」与「流水」当作同一个一致性单元，使用参数化 JDBC 语句。
 * 余额以「分」为单位的 long 存储（balance_cents BIGINT），流水金额同样以分为单位、类型以枚举名存 VARCHAR。
 *
 * <p>
 * debit/credit/setBalance 三个写原语都是 {@code synchronized} + **单连接**
 * {@code setAutoCommit(false)} 事务
 * （与 {@link AccessLibraryRepository} 同款写法，UCanAccess 4.0.4 支持单连接跨表事务）：
 * 先改余额、再 INSERT 流水、最后 {@code commit}；任何 {@link SQLException}（含流水表缺失/只读）→
 * {@code rollback}
 * 并抛 {@link IllegalStateException}，调用方据此补偿并返回错误，绝不出现「余额已变、流水却缺失」的中间态。
 * 全部写操作串行化在同一实例锁上，故并发校正被串行执行。
 *
 * <p>
 * setBalance 在事务内先读**实际旧余额**再据此计算差额记流水，因此两个管理员并发校正各自记录真实差额，
 * 逐笔流水金额累加恒等于最终余额，可直接对账（修评审「并发校正生成错误对账金额」问题）。
 *
 * <p>
 * 只读方法 findByUserId/findTransactionsByUserId 用独立只读连接，不与写操作抢锁；
 * save 仅置余额、**不记流水**，供种子数据与测试预置初始余额。
 */
public final class AccessWalletRepository implements WalletRepository {
    private final Path databasePath;

    public AccessWalletRepository(Path databasePath) {
        if (databasePath == null)
            throw new IllegalArgumentException("databasePath must not be null");
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
    public List<WalletTransaction> findTransactionsByUserId(String userId) {
        String sql = "SELECT transaction_id,user_id,transaction_type,amount_cents,balance_after_cents,"
                + "operator_id,note,created_at FROM tblWalletTransaction WHERE user_id=? "
                + "ORDER BY created_at,transaction_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                List<WalletTransaction> transactions = new ArrayList<WalletTransaction>();
                while (results.next()) {
                    transactions.add(readTransaction(results));
                }
                return transactions;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find wallet transactions by user", failure);
        }
    }

    @Override
    public synchronized boolean save(BankAccount account) {
        if (account == null)
            return false;
        // upsert：先 UPDATE，0 行再 INSERT；写操作串行化在同一实例锁上，故懒建户不会撞主键
        try (Connection connection = open()) {
            if (updateBalance(connection, account.getUserId(), account.getBalanceCents()) > 0)
                return true;
            insertBalance(connection, account.getUserId(), account.getBalanceCents());
            return true;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to save bank account", failure);
        }
    }

    @Override
    public synchronized WalletMutation debit(String userId, long cents, WalletTransactionType type, String operatorId,
            String note) {
        if (cents <= 0)
            throw new IllegalArgumentException("debit cents must be positive");
        Connection connection = null;
        try {
            connection = open();
            connection.setAutoCommit(false);
            // 守卫：balance_cents>=cents 才扣，0 行说明账户不存在或余额不足
            if (guardedDebit(connection, userId, cents) == 0) {
                long current = readBalance(connection, userId);
                rollback(connection);
                return WalletMutation.rejected(current);
            }
            long after = readBalance(connection, userId);
            insertTransaction(connection, userId, type, -cents, after, operatorId, note);
            connection.commit();
            return WalletMutation.applied(after + cents, after);
        } catch (SQLException failure) {
            rollback(connection);
            throw new IllegalStateException("failed to debit wallet", failure);
        } finally {
            close(connection);
        }
    }

    @Override
    public synchronized WalletMutation credit(String userId, long cents, WalletTransactionType type, String operatorId,
            String note) {
        if (cents <= 0)
            throw new IllegalArgumentException("credit cents must be positive");
        Connection connection = null;
        try {
            connection = open();
            connection.setAutoCommit(false);
            // 懒创建：UPDATE 累加，0 行说明账户不存在则 INSERT 建户（synchronized 下无并发撞主键）
            if (accumulate(connection, userId, cents) == 0)
                insertBalance(connection, userId, cents);
            long after = readBalance(connection, userId);
            insertTransaction(connection, userId, type, cents, after, operatorId, note);
            connection.commit();
            return WalletMutation.applied(after - cents, after);
        } catch (SQLException failure) {
            rollback(connection);
            throw new IllegalStateException("failed to credit wallet", failure);
        } finally {
            close(connection);
        }
    }

    @Override
    public synchronized WalletMutation setBalance(String userId, long newBalanceCents, WalletTransactionType type,
            String operatorId, String note) {
        if (newBalanceCents < 0)
            throw new IllegalArgumentException("balance must not be negative");
        Connection connection = null;
        try {
            connection = open();
            connection.setAutoCommit(false);
            // 事务内先读实际旧余额：并发校正被 synchronized 串行化，各自记录真实差额，累加恒等于最终余额
            long before = readBalance(connection, userId);
            if (updateBalance(connection, userId, newBalanceCents) == 0)
                insertBalance(connection, userId, newBalanceCents);
            insertTransaction(connection, userId, type, newBalanceCents - before, newBalanceCents, operatorId, note);
            connection.commit();
            return WalletMutation.applied(before, newBalanceCents);
        } catch (SQLException failure) {
            rollback(connection);
            throw new IllegalStateException("failed to set wallet balance", failure);
        } finally {
            close(connection);
        }
    }

    // 事务内读余额：账户不存在按 0 计（懒创建/校正的起点）
    private long readBalance(Connection connection, String userId) throws SQLException {
        String sql = "SELECT balance_cents FROM tblBankAccount WHERE user_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getLong("balance_cents") : 0L;
            }
        }
    }

    // 带守卫的扣款：balance_cents>=cents 才扣，返回受影响行数（0 表示余额不足或账户不存在）
    private int guardedDebit(Connection connection, String userId, long cents) throws SQLException {
        String sql = "UPDATE tblBankAccount SET balance_cents=balance_cents-? WHERE user_id=? AND balance_cents>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            statement.setLong(3, cents);
            return statement.executeUpdate();
        }
    }

    // 累加入账：balance_cents+=cents，返回受影响行数（0 表示账户尚不存在）
    private int accumulate(Connection connection, String userId, long cents) throws SQLException {
        String sql = "UPDATE tblBankAccount SET balance_cents=balance_cents+? WHERE user_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            return statement.executeUpdate();
        }
    }

    // 绝对设置余额：balance_cents=?，返回受影响行数（0 表示账户尚不存在）
    private int updateBalance(Connection connection, String userId, long cents) throws SQLException {
        String sql = "UPDATE tblBankAccount SET balance_cents=? WHERE user_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cents);
            statement.setString(2, userId);
            return statement.executeUpdate();
        }
    }

    // 新建账户行
    private void insertBalance(Connection connection, String userId, long cents) throws SQLException {
        String sql = "INSERT INTO tblBankAccount(user_id,balance_cents) VALUES(?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setLong(2, cents);
            statement.executeUpdate();
        }
    }

    // 事务内追加一条流水：流水编号用 UUID、记账时间取当前时刻；备注可空时显式写 SQL NULL
    private void insertTransaction(Connection connection, String userId, WalletTransactionType type, long amountCents,
            long balanceAfterCents, String operatorId, String note) throws SQLException {
        String sql = "INSERT INTO tblWalletTransaction"
                + "(transaction_id,user_id,transaction_type,amount_cents,balance_after_cents,"
                + "operator_id,note,created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId);
            statement.setString(3, type.name());
            statement.setLong(4, amountCents);
            statement.setLong(5, balanceAfterCents);
            statement.setString(6, operatorId);
            if (note == null)
                statement.setNull(7, Types.VARCHAR);
            else
                statement.setString(7, note);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private static WalletTransaction readTransaction(ResultSet results) throws SQLException {
        return new WalletTransaction(
                results.getString("transaction_id"),
                results.getString("user_id"),
                WalletTransactionType.valueOf(results.getString("transaction_type")),
                results.getLong("amount_cents"),
                results.getLong("balance_after_cents"),
                results.getString("operator_id"),
                results.getString("note"),
                results.getTimestamp("created_at").toLocalDateTime());
    }

    private Connection open() throws SQLException {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException missingDriver) {
            throw new IllegalStateException("UCanAccess driver is missing", missingDriver);
        }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void rollback(Connection connection) {
        if (connection == null)
            return;
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void close(Connection connection) {
        if (connection == null)
            return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
