package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryCompensationService;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.WalletMutation;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static cn.vcampus.server.LibraryCompensationSql.*;

/** Loss inventory, borrowing status, bill, balance and ledger use one Access transaction. */
public final class AccessLibraryCompensationService implements LibraryCompensationService {
    /** Access 数据库文件路径。 */
    private final Path databasePath;
    /** 共享图书仓库或业务服务。 */
    private final AccessLibraryRepository library;
    /** 与商店共用的钱包访问对象或查询结果。 */
    private final AccessWalletRepository wallet;
    /** 可替换的数据库连接来源。 */
    private final ConnectionFactory connections;

    /** 仅用于隔离数据库与故障回滚测试的连接工厂。 */
    interface ConnectionFactory {
        /** 打开属于本次操作的连接。 */
        Connection open() throws SQLException;
    }

    /** 绑定共享图书与钱包仓库，确保赔偿和商店使用同一钱包上下文。 */
    public AccessLibraryCompensationService(Path databasePath, AccessLibraryRepository library,
            AccessWalletRepository wallet) {
        this(databasePath, library, wallet, null);
    }

    /** 额外注入可替换连接来源，用于验证付款事务失败时的完整回滚。 */
    AccessLibraryCompensationService(Path databasePath, AccessLibraryRepository library,
            AccessWalletRepository wallet, ConnectionFactory connections) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.library = Objects.requireNonNull(library, "library");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.connections = connections;
    }

    /** 在共享图书锁下登记遗失；重复请求返回原账单。 */
    @Override
    public ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        synchronized (library) {
            return LibraryLossDeclaration.execute(this::open, operatorId, recordId);
        }
    }

    /** 校验账单归属后结清赔偿；账单、借阅、扣款和流水保持一致。 */
    @Override
    public ServiceResult<LibraryCompensation> pay(String userId, String compensationId) {
        if (blank(userId) || blank(compensationId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId and compensationId are required");
        }
        synchronized (library) {
            synchronized (wallet) {
                Connection connection = null;
                try {
                    connection = open();
                    connection.setAutoCommit(false);
                    LibraryCompensation bill = findBill(connection, "compensation_id", compensationId.trim());
                    // Ownership is enforced here as well as at the token-authenticated endpoint.
                    if (bill == null || !bill.getUserId().equals(userId.trim())) {
                        return fail(connection, StatusCode.NOT_FOUND, "compensation not found");
                    }
                    if (bill.getStatus() == CompensationStatus.PAID) { rollback(connection); return ServiceResult.ok(bill); }
                    LibraryCompensation paid = bill.paid(LocalDateTime.now());
                    if (updateBorrowStatus(connection, bill.getRecordId(), userId.trim(), "LOST", "COMPENSATED") != 1) {
                        return fail(connection, StatusCode.CONFLICT, "lost borrowing record state changed");
                    }
                    if (bill.getAmountCents() > 0) {
                        WalletMutation mutation = wallet.debitInTransaction(connection, userId.trim(), bill.getAmountCents(),
                                WalletTransactionType.LIBRARY_LOSS, userId.trim(),
                                "library compensation " + bill.getCompensationId(), bill.getCompensationId());
                        if (!mutation.isApplied()) {
                            return fail(connection, StatusCode.PAYMENT_REQUIRED, "insufficient campus wallet balance");
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE tblLibraryCompensation SET status=?,paid_at=? WHERE compensation_id=? AND status=?")) {
                        statement.setString(1, "PAID");
                        statement.setTimestamp(2, Timestamp.valueOf(paid.getPaidAt()));
                        statement.setString(3, bill.getCompensationId());
                        statement.setString(4, "PENDING");
                        if (statement.executeUpdate() != 1) {
                            return fail(connection, StatusCode.CONFLICT, "compensation state changed");
                        }
                    }
                    connection.commit();
                    return ServiceResult.ok(paid);
                } catch (SQLException | RuntimeException storageFailure) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to settle library compensation");
                } finally { close(connection); }
            }
        }
    }

    /** 根据授权范围读取本人或全部赔偿／借阅记录。 */
    @Override
    public ServiceResult<List<LibraryCompensation>> history(String userId) {
        if (userId != null && blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId is blank");
        synchronized (library) {
            String sql = "SELECT " + COLUMNS + " FROM tblLibraryCompensation"
                    + (userId == null ? "" : " WHERE user_id=?") + " ORDER BY created_at,compensation_id";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                if (userId != null) statement.setString(1, userId.trim());
                try (ResultSet result = statement.executeQuery()) {
                    List<LibraryCompensation> bills = new ArrayList<LibraryCompensation>();
                    while (result.next()) bills.add(readBill(result));
                    return ServiceResult.ok(bills);
                }
            } catch (SQLException | RuntimeException storageFailure) {
                return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read library compensations");
            }
        }
    }

    /** 读取同一校园钱包的余额，单位为分。 */
    @Override
    public ServiceResult<Long> balance(String userId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId is required");
        try {
            synchronized (wallet) {
                BankAccount account = wallet.findByUserId(userId.trim());
                return ServiceResult.ok(account == null ? 0L : account.getBalanceCents());
            }
        } catch (IllegalStateException storageFailure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read campus wallet");
        }
    }

    /** 打开可替换的短生命周期 Access 连接。 */
    private Connection open() throws SQLException {
        if (connections != null) return connections.open();
        try { Class.forName("net.ucanaccess.jdbc.UcanaccessDriver"); }
        catch (ClassNotFoundException missing) { throw new SQLException("UCanAccess driver missing", missing); }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath + ";immediatelyReleaseResources=true");
    }

}
