package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StoreRestockCommand;
import cn.vcampus.store.StoreProductAddCommand;
import cn.vcampus.store.StoreProductUpdateCommand;
import cn.vcampus.store.StoreProductDeactivateCommand;
import cn.vcampus.store.StoreProductReactivateCommand;
import cn.vcampus.store.CartAddCommand;
import cn.vcampus.store.CartRemoveCommand;
import cn.vcampus.store.CartUpdateCommand;
import cn.vcampus.store.CartQueryCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
import cn.vcampus.store.StoreAccountQueryCommand;
import cn.vcampus.store.StoreAccountRechargeCommand;
import cn.vcampus.store.StoreAccountAdjustCommand;
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

    /** 按类别查询在售商品。 */
    public Message listProducts(String token, String category) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_QUERY, new StoreQueryCommand(token, category));
    }

    /** 管理员补充库存。 */
    public Message restock(String token, String productId, int additionalStock)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_RESTOCK, new StoreRestockCommand(token, productId, additionalStock));
    }

    /** 管理员新增商品。 */
    public Message addProduct(String token, String name, double price, int stock, String description,
            String category) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PRODUCT_ADD,
                new StoreProductAddCommand(token, name, price, stock, description, category));
    }

    /** 管理员更新商品。 */
    public Message updateProduct(String token, String productId, String name, double price, String description,
            String category) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PRODUCT_UPDATE,
                new StoreProductUpdateCommand(token, productId, name, price, description, category));
    }

    /** 管理员下架商品。 */
    public Message deactivateProduct(String token, String productId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PRODUCT_DEACTIVATE,
                new StoreProductDeactivateCommand(token, productId));
    }

    /** 管理员重新上架商品。 */
    public Message reactivateProduct(String token, String productId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_PRODUCT_REACTIVATE,
                new StoreProductReactivateCommand(token, productId));
    }

    /** 将商品加入当前用户购物车。 */
    public Message addToCart(String token, String productId, int quantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_ADD, new CartAddCommand(token, productId, quantity));
    }

    /** 删除当前用户购物车条目。 */
    public Message removeFromCart(String token, String cartItemId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_REMOVE, new CartRemoveCommand(token, cartItemId));
    }

    /** 修改当前用户购物车条目数量；条目归属由服务端校验。 */
    public Message updateCart(String token, String cartItemId, int newQuantity)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_UPDATE, new CartUpdateCommand(token, cartItemId, newQuantity));
    }

    /** 查询当前用户购物车。 */
    public Message cart(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_QUERY, new CartQueryCommand(token));
    }

    /** 查询当前用户购物车明细；服务端读取时与商品联表，响应为 List&lt;CartLine&gt;。 */
    public Message cartDetail(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_DETAIL, new CartQueryCommand(token));
    }

    /** 结算当前用户购物车。 */
    public Message checkout(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_CART_CHECKOUT, new CartCheckoutCommand(token));
    }

    /** 管理员查询全部订单。 */
    public Message allOrders(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ORDER_LIST_ALL, new StoreOrderListAllCommand(token));
    }

    /** 查询热销商品。 */
    public Message hotProducts(String token, int limit) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_HOT_PRODUCTS, new StoreHotProductsCommand(token, limit));
    }

    /** 查询当前用户余额（分）；身份由服务器从 token 解析。 */
    public Message balance(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ACCOUNT_QUERY, new StoreAccountQueryCommand(token));
    }

    /** 本人充值（分）；充值人由服务器从 token 解析。 */
    public Message recharge(String token, long cents) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ACCOUNT_RECHARGE, new StoreAccountRechargeCommand(token, cents));
    }

    /** 管理员校正目标用户余额（分）；管理员身份由服务器从 token 解析，targetUserId 取自参数。 */
    public Message adjustBalance(String token, String targetUserId, long newBalanceCents)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(token, targetUserId, newBalanceCents));
    }

    /** 查询当前用户钱包流水（分）；只能查本人，响应为 List&lt;WalletTransaction&gt;。 */
    public Message ledger(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STORE_ACCOUNT_LEDGER, new StoreAccountQueryCommand(token));
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("store-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
