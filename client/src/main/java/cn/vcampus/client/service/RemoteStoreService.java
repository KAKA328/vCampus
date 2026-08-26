package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.store.StoreCommand;
import cn.vcampus.store.StoreOrderQueryCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Client adapter for store messages. */
public final class RemoteStoreService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteStoreService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message listProducts(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_QUERY, token);
    }

    public Message purchase(String token, String buyerId, String productId, int quantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PURCHASE, new StoreCommand(token, buyerId, productId, quantity));
    }

    public Message ownOrders(String token, String buyerId) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_QUERY, StoreOrderQueryCommand.ownOrders(token, buyerId));
    }

    public Message allOrders(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_QUERY, StoreOrderQueryCommand.allOrders(token));
    }

    private Message send(MessageType type, Object payload)
            throws IOException, ClassNotFoundException {
        return messages.send(Message.request("store-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
