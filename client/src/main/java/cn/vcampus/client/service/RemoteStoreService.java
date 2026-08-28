package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;

import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreOrderQueryCommand;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端商店服务，负责把界面操作转换为 Socket 商店消息。 */
public final class RemoteStoreService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();// 产生不同请求不同的序列号

    // 接受主机地址和端口
    public RemoteStoreService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    // 列出商品
    public Message listProducts(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_QUERY, new StoreQueryCommand(token));
    }

    // 购买商品
    public Message purchase(String token, String productId, int quantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PURCHASE, new StorePurchaseCommand(token, productId, quantity));
    }

    // 查询订单
    public Message findOrders(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_QUERY, new StoreOrderQueryCommand(token));
    }

    // 发送消息
    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("store-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
