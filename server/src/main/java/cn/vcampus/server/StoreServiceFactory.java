package cn.vcampus.server;

import cn.vcampus.store.DefaultStoreService;
import cn.vcampus.store.InMemoryStoreService;
import cn.vcampus.store.StoreService;
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
                new AccessBankAccountRepository(databasePath));
    }
}
