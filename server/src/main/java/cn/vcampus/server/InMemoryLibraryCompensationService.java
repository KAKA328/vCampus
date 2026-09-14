package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.InMemoryLibraryRepository;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryCompensationService;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.WalletRepository;
import cn.vcampus.store.WalletTransactionType;
import java.util.List;
import java.util.Objects;

/** Demo bridge using the very same wallet instance as the store. Lock order: library, then wallet. */
public final class InMemoryLibraryCompensationService implements LibraryCompensationService {
    /** 共享图书仓库或业务服务。 */
    private final InMemoryLibraryRepository library;
    /** 与商店共用的钱包访问对象或查询结果。 */
    private final WalletRepository wallet;

    /** 绑定同一服务器复用的图书仓库和钱包，统一扣款与图书状态更新。 */
    public InMemoryLibraryCompensationService(InMemoryLibraryRepository library, WalletRepository wallet) {
        this.library = Objects.requireNonNull(library, "library");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
    }

    /** 登记遗失并按原价生成唯一赔偿账单，不直接扣读者余额。 */
    @Override
    public ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        return library.declareLoss(operatorId, recordId);
    }

    /** 校验账单归属后结清赔偿；账单、借阅、扣款和流水保持一致。 */
    @Override
    public ServiceResult<LibraryCompensation> pay(String userId, String compensationId) {
        synchronized (library) {
            synchronized (wallet) {
                try {
                    return library.payCompensation(userId, compensationId, cents -> wallet.debit(userId.trim(), cents,
                            WalletTransactionType.LIBRARY_LOSS, userId.trim(), "library compensation " + compensationId)
                            .isApplied());
                } catch (IllegalStateException storageFailure) {
                    return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to settle library compensation");
                }
            }
        }
    }

    /** 根据授权范围读取本人或全部赔偿／借阅记录。 */
    @Override
    public ServiceResult<List<LibraryCompensation>> history(String userId) {
        if (userId != null && userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        return ServiceResult.ok(library.findCompensations(userId));
    }

    /** 读取同一校园钱包的余额，单位为分。 */
    @Override
    public ServiceResult<Long> balance(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId is required");
        }
        try {
            synchronized (wallet) {
                BankAccount account = wallet.findByUserId(userId.trim());
                return ServiceResult.ok(account == null ? 0L : account.getBalanceCents());
            }
        } catch (IllegalStateException storageFailure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read campus wallet");
        }
    }
}
