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
    private final InMemoryLibraryRepository library;
    private final WalletRepository wallet;

    public InMemoryLibraryCompensationService(InMemoryLibraryRepository library, WalletRepository wallet) {
        this.library = Objects.requireNonNull(library, "library");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
    }

    @Override
    public ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        return library.declareLoss(operatorId, recordId);
    }

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

    @Override
    public ServiceResult<List<LibraryCompensation>> history(String userId) {
        if (userId != null && userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        return ServiceResult.ok(library.findCompensations(userId));
    }

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
