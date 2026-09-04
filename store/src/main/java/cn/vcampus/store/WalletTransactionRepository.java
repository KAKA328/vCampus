package cn.vcampus.store;

import java.util.List;

/**
 * 校园钱包流水仓库契约，只追加、不修改、不删除。
 * 流水是审计副产物：Access 实现下每次 append 都是一条独立 INSERT，与余额写入不在同一事务内，
 * 因此调用方须容忍 append 失败（记日志后继续），不得因记账失败回滚一笔已成功的扣款。
 */
public interface WalletTransactionRepository {

    /** 追加一条流水；流水编号重复或写入失败返回 false。 */
    boolean append(WalletTransaction transaction);

    /** 按用户查询流水，按记账时间升序返回；无流水返回空列表而非 null。 */
    List<WalletTransaction> findByUserId(String userId);
}
