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
    final LibraryService library;
    final LibraryCompensationService compensations;
    final StoreService store;
    final WalletRepository wallet;

    private LibraryWalletRuntime(LibraryService library, LibraryCompensationService compensations,
            StoreService store, WalletRepository wallet) {
        this.library = library;
        this.compensations = compensations;
        this.store = store;
        this.wallet = wallet;
    }

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
