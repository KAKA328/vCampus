package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
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

    /** 查询全部商品；身份由服务器从 token 解析。 */
    public Message listProducts(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_QUERY, new StoreQueryCommand(token));
    }

    /** 提交购买请求；购买人由服务器从 token 解析。 */
    public Message purchase(String token, String productId, int quantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PURCHASE,
                new StorePurchaseCommand(token, productId, quantity));
    }

    /** 查询当前登录用户的购买记录；用户编号由服务器从 token 解析。 */
    public Message ordersFor(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_QUERY, new StoreOrderQueryCommand(token));
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("store-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
