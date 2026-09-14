package cn.vcampus.server;

import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.InMemoryLibraryRepository;
import cn.vcampus.library.LibraryCompensationService;
import cn.vcampus.library.LibraryService;
import cn.vcampus.store.InMemoryWalletRepository;
import cn.vcampus.store.StoreService;
import cn.vcampus.store.WalletRepository;
import java.nio.file.Path;

/** One shared wallet and library repository for all client connections and both modules. */
final class LibraryWalletRuntime {
    /** 共享图书仓库或业务服务。 */
    final LibraryService library;
    /** 赔偿记录或赔偿服务。 */
    final LibraryCompensationService compensations;
    /** 商店服务使用的共享钱包上下文。 */
    final StoreService store;
    /** 与商店共用的钱包访问对象或查询结果。 */
    final WalletRepository wallet;

    /** 保存由同一组图书仓库和钱包组装的服务实例，供所有客户端连接复用。 */
    private LibraryWalletRuntime(LibraryService library, LibraryCompensationService compensations,
            StoreService store, WalletRepository wallet) {
        this.library = library;
        this.compensations = compensations;
        this.store = store;
        this.wallet = wallet;
    }

    /** 按数据库路径创建内存或 Access 业务实现。 */
    static LibraryWalletRuntime create(Path databasePath, boolean demoLoans) {
        if (databasePath == null) {
            InMemoryLibraryRepository repository = demoLoans
                    ? InMemoryLibraryRepository.withDemoData() : InMemoryLibraryRepository.withDemoCatalog();
            WalletRepository wallet = new InMemoryWalletRepository();
            return new LibraryWalletRuntime(new DefaultLibraryService(repository),
                    new InMemoryLibraryCompensationService(repository, wallet),
                    StoreServiceFactory.create(null, wallet), wallet);
        }
        AccessLibraryRepository repository = new AccessLibraryRepository(databasePath);
        AccessWalletRepository wallet = new AccessWalletRepository(databasePath);
        return new LibraryWalletRuntime(new DefaultLibraryService(repository),
                new AccessLibraryCompensationService(databasePath, repository, wallet),
                StoreServiceFactory.create(databasePath, wallet), wallet);
    }
}
