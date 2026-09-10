package cn.vcampus.server;

import cn.vcampus.store.DefaultStoreService;
import cn.vcampus.store.InMemoryStoreService;
import cn.vcampus.store.StoreService;
import cn.vcampus.store.WalletRepository;
import cn.vcampus.store.InMemoryProductRepository;
import cn.vcampus.store.InMemoryOrderRepository;
import cn.vcampus.store.InMemoryCartRepository;
import java.nio.file.Path;

/**
 * Creates the store service using the same persistence mode as the user
 * service.
 */
final class StoreServiceFactory {
    private StoreServiceFactory() {
    }

    static StoreService create(String[] args) {
        Path databasePath = UserServiceFactory.databasePath(args);
        return create(databasePath);
    }

    static StoreService create(Path databasePath) {
        if (databasePath == null)
            return new InMemoryStoreService();
        return new DefaultStoreService(
                new AccessProductRepository(databasePath),
                new AccessOrderRepository(databasePath),
                new AccessCartRepository(databasePath),
                new AccessWalletRepository(databasePath));
    }

    /** Campus integrations must share this wallet, rather than create another balance store. */
    static StoreService create(Path databasePath, WalletRepository wallet) {
        if (databasePath == null) {
            return new DefaultStoreService(new InMemoryProductRepository(),
                    new InMemoryOrderRepository(), new InMemoryCartRepository(), wallet);
        }
        return new DefaultStoreService(new AccessProductRepository(databasePath),
                new AccessOrderRepository(databasePath), new AccessCartRepository(databasePath), wallet);
    }
}
