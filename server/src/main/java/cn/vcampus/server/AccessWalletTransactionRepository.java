package cn.vcampus.server;

import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionRepository;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Access 钱包流水仓库，使用参数化 JDBC 语句，只追加、不修改、不删除。
 * 金额以「分」为单位的 BIGINT 存储，流水类型以枚举名存 VARCHAR；
 * 每次 append 都是一条独立 INSERT，与余额写入不在同一事务内（Access 无跨表事务）。
 * 本仓库故意不抛 IllegalStateException（区别于其他 Access 仓库）：契约要求「写入失败返回 false」，
 * 因为流水是尽力而为的审计副产物，调用方只需记日志，绝不得因记账失败回滚一笔已成功的资金变动。
 */
public final class AccessWalletTransactionRepository implements WalletTransactionRepository {
    private final Path databasePath;

    public AccessWalletTransactionRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public boolean append(WalletTransaction transaction) {
        if (transaction == null)
            return false;
        String sql = "INSERT INTO tblWalletTransaction"
                + "(transaction_id,user_id,transaction_type,amount_cents,balance_after_cents,"
                + "operator_id,note,created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transaction.getTransactionId());
            statement.setString(2, transaction.getUserId());
            statement.setString(3, transaction.getType().name());
            statement.setLong(4, transaction.getAmountCents());
            statement.setLong(5, transaction.getBalanceAfterCents());
            statement.setString(6, transaction.getOperatorId());
            // 备注可空，显式写 SQL NULL 而不是空串，便于区分「没写备注」与「备注是空串」
            if (transaction.getNote() == null)
                statement.setNull(7, Types.VARCHAR);
            else
                statement.setString(7, transaction.getNote());
            statement.setTimestamp(8, Timestamp.valueOf(transaction.getCreatedAt()));
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            // 主键重复或库不可写都当作「没记上」，返回 false 交由调用方记日志，不上抛
            System.err.println("[ledger] append failed for user " + transaction.getUserId()
                    + ", type=" + transaction.getType() + ": " + failure.getMessage());
            return false;
        }
    }

    @Override
    public List<WalletTransaction> findByUserId(String userId) {
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
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }
}
