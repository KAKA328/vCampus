package cn.vcampus.server;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Keeps UCanAccess from shutting down its shared mirror between concurrent requests. */
final class AccessDatabaseLease implements Closeable {
    private final Connection connection;
    private final Thread shutdownHook;
    private AccessDatabaseLease(Connection connection) {
        this.connection = connection;
        shutdownHook = new Thread(() -> {
            try { closeConnection(); }
            catch (IOException failure) { System.err.println(failure.getMessage()); }
        }, "access-database-release");
        if (connection != null) Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    static AccessDatabaseLease open(Path database) throws IOException {
        if (database == null) return new AccessDatabaseLease(null);
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            return new AccessDatabaseLease(DriverManager.getConnection("jdbc:ucanaccess://"
                    + database.toAbsolutePath().normalize() + ";immediatelyReleaseResources=true"));
        } catch (SQLException | ClassNotFoundException failure) {
            throw new IOException("Unable to open server database", failure);
        }
    }
    @Override public void close() throws IOException {
        if (connection != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException shuttingDown) { /* The registered hook owns shutdown cleanup. */ }
        }
        closeConnection();
    }
    private synchronized void closeConnection() throws IOException {
        if (connection == null) return;
        try { connection.close(); }
        catch (SQLException failure) { throw new IOException("Unable to release server database", failure); }
    }
}
