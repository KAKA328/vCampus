package cn.vcampus.server;

import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.library.LibraryService;
import java.nio.file.Path;

/** Creates the library service in demo or Access persistence mode. */
final class LibraryServiceFactory {
    private LibraryServiceFactory() { }

    static LibraryService create(String[] args) { return create(UserServiceFactory.databasePath(args)); }

    static LibraryService create(Path databasePath) {
        return databasePath == null ? new InMemoryLibraryService()
                : new DefaultLibraryService(new AccessLibraryRepository(databasePath));
    }
}
