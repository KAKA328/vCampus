package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端商店服务，把 Swing 页面操作转换为统一 Socket 消息。 */
public final class RemoteStoreService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteStoreService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    /** 查询全部商品。 */
    public Message listProducts() throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_QUERY, null);
    }

    /** 使用当前登录用户编号作为购买人编号提交购买请求。 */
    public Message purchase(String token, String buyerId, String productId, int quantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PURCHASE,
                new StorePurchaseCommand(token, buyerId, productId, quantity));
    }

    /** 查询当前登录用户的购买记录。 */
    public Message ordersFor(String token, String buyerId) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_QUERY, new StoreOrderQueryCommand(token, buyerId));
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("store-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
