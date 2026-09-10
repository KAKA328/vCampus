package cn.vcampus.client.service;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.LibraryCompensationListV3Command;
import cn.vcampus.library.LibraryCompensationPayV3Command;
import cn.vcampus.library.LibraryHistoryV3Command;
import cn.vcampus.library.LibraryLossDeclareV3Command;
import cn.vcampus.library.LibraryWalletQueryV3Command;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 真正的序列化往返，确保新状态只走显式 V3 协议。 */
class RemoteLibraryCompensationTest {
    @Test
    void allHistoryScopesUseV3AndPreserveSessionIdentity() throws Exception {
        exchange(3, (request, index) -> {
            assertEquals(MessageType.LIBRARY_HISTORY_V3, request.getType());
            LibraryHistoryV3Command command = (LibraryHistoryV3Command) request.getPayload();
            assertEquals("token", command.getToken());
            assertEquals(index == 2, command.isAllUsers());
            assertEquals(index == 1 ? "reader-target" : null, command.getTargetUserId());
            return Message.response(request, StatusCode.OK, Collections.emptyList());
        }, remote -> {
            assertEquals(StatusCode.OK, remote.ownHistory("token").getStatusCode());
            assertEquals(StatusCode.OK, remote.historyFor("token", "reader-target").getStatusCode());
            assertEquals(StatusCode.OK, remote.allHistory("token").getStatusCode());
        });
    }

    @Test
    void lossAndPaymentSendOnlyIdentifiersAndPreserveServerFailures() throws Exception {
        exchange(3, (request, index) -> {
            if (index == 0) {
                assertEquals(MessageType.LIBRARY_LOSS_DECLARE_V3, request.getType());
                LibraryLossDeclareV3Command command = (LibraryLossDeclareV3Command) request.getPayload();
                assertEquals("token", command.getToken());
                assertEquals("BR-1", command.getRecordId());
                return Message.response(request, StatusCode.FORBIDDEN, "only librarians");
            }
            assertEquals(MessageType.LIBRARY_COMPENSATION_PAY_V3, request.getType());
            LibraryCompensationPayV3Command command = (LibraryCompensationPayV3Command) request.getPayload();
            assertEquals("token", command.getToken());
            assertEquals("LC-1", command.getCompensationId());
            // 金额、借阅人不能由客户端提交或改写。
            assertEquals(0, java.util.Arrays.stream(command.getClass().getDeclaredFields())
                    .filter(field -> field.getName().equals("amountCents") || field.getName().equals("userId")).count());
            return Message.response(request, index == 1 ? StatusCode.PAYMENT_REQUIRED : StatusCode.OK,
                    index == 1 ? "insufficient balance" : "already paid");
        }, remote -> {
            assertEquals(StatusCode.FORBIDDEN, remote.declareLoss("token", "BR-1").getStatusCode());
            Message insufficient = remote.payCompensation("token", "LC-1");
            assertEquals(StatusCode.PAYMENT_REQUIRED, insufficient.getStatusCode());
            assertEquals("insufficient balance", insufficient.getPayload());
            assertEquals("already paid", remote.payCompensation("token", "LC-1").getPayload());
        });
    }

    @Test
    void listScopeAndOwnWalletQueryHaveSeparateVersionedCommands() throws Exception {
        exchange(3, (request, index) -> {
            if (index < 2) {
                assertEquals(MessageType.LIBRARY_COMPENSATION_LIST_V3, request.getType());
                LibraryCompensationListV3Command command = (LibraryCompensationListV3Command) request.getPayload();
                assertEquals("token", command.getToken());
                assertEquals(index == 1, command.isAllUsers());
                return Message.response(request, StatusCode.OK, Collections.emptyList());
            }
            assertEquals(MessageType.LIBRARY_WALLET_QUERY_V3, request.getType());
            assertEquals("token", ((LibraryWalletQueryV3Command) request.getPayload()).getToken());
            return Message.response(request, StatusCode.OK, Long.valueOf(12345));
        }, remote -> {
            remote.compensations("token", false);
            remote.compensations("token", true);
            assertEquals(Long.valueOf(12345), remote.walletBalance("token").getPayload());
        });
    }

    private static void exchange(int count, ServerResponse respond, ClientWork client) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        for (int index = 0; index < count; index++) {
                            Message request = (Message) input.readObject();
                            output.writeObject(respond.response(request, index));
                            output.flush();
                        }
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteLibraryService remote = new RemoteLibraryService("127.0.0.1", listener.getLocalPort())) {
                client.run(remote);
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    private interface ServerResponse { Message response(Message request, int index); }
    private interface ClientWork { void run(RemoteLibraryService remote) throws Exception; }
}
