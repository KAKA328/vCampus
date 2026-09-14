package cn.vcampus.client.view;

/** Test-only stdin shutdown control; production requests still use ServerApplication.main. */
public final class NetworkAcceptanceServerProcess {
    public static void main(String[] args) throws Exception {
        Thread stop = new Thread(() -> {
            try {
                if (System.in.read() == '\n') System.exit(0);
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }, "acceptance-stop");
        stop.setDaemon(true);
        stop.start();
        cn.vcampus.server.ServerApplication.main(args);
    }
}
