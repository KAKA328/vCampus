package cn.vcampus.server;

import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.library.LibraryService;
import java.nio.file.Path;

/** Creates the library service in demo or Access persistence mode. */
final class LibraryServiceFactory {
    /** 业务实现工厂不需要实例。 */
    private LibraryServiceFactory() { }

    /** 按数据库路径创建内存或 Access 业务实现。 */
    static LibraryService create(String[] args) { return create(UserServiceFactory.databasePath(args)); }

    /** 按数据库路径创建内存或 Access 业务实现。 */
    static LibraryService create(Path databasePath) {
        return databasePath == null ? InMemoryLibraryService.withDemoData()
                : new DefaultLibraryService(new AccessLibraryRepository(databasePath));
    }
}
