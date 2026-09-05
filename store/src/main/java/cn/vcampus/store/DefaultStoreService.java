package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

// 默认商店业务，通过提供商品仓库和订单仓库以实现业务逻辑
public final class DefaultStoreService implements StoreService {
    // 补偿失败留痕上限：超出丢弃最旧一条，避免长期运行内存无限增长
    private static final int MAX_COMPENSATION_FAILURES = 500;
    // 本人充值护栏（演示口径：单笔 ≤ 2000 元、单日累计 ≤ 5000 元），防止无上限自助发币；包级可见供测试引用
    static final long MAX_SINGLE_RECHARGE_CENTS = 200_000L;
    static final long MAX_DAILY_RECHARGE_CENTS = 500_000L;
    private final ProductRepository products;// 商品仓库
    private final OrderRepository orders;// 订单仓库
    private final CartRepository cart;// 购物车仓库
    private final WalletRepository wallet;// 钱包仓库：余额与流水在同一事务/锁内原子读写
    // 补偿失败留痕：只在 purchase/checkout 的 synchronized 锁内写入，读取用快照，供运维人工对账或后续重试
    private final List<CompensationFailure> compensationFailures = new ArrayList<CompensationFailure>();

    // 依赖注入：2 参构造默认内存购物车 + 内存钱包
    public DefaultStoreService(ProductRepository products, OrderRepository orders) {
        this(products, orders, new InMemoryCartRepository());
    }

    // 3 参构造默认内存钱包
    public DefaultStoreService(ProductRepository products, OrderRepository orders, CartRepository cart) {
        this(products, orders, cart, new InMemoryWalletRepository());
    }

    // 4 参主构造：注入全部依赖
    public DefaultStoreService(ProductRepository products, OrderRepository orders, CartRepository cart,
            WalletRepository wallet) {
        if (products == null || orders == null || cart == null || wallet == null) {
            throw new IllegalArgumentException("store repositories must not be null");
        }
        this.products = products;
        this.orders = orders;
        this.cart = cart;
        this.wallet = wallet;
    }

    // 列出所有商品，使用serviceresult类的ok方法打包返回
    // 只读方法不与 purchase/checkout 抢写锁：商品仓库自身已保证读取安全（内存版返回新列表，
    // Access 版每次独立连接），加锁只会让浏览商品被一次慢购买阻塞
    @Override
    public final ServiceResult<List<Product>> listProducts() {
        List<Product> result = new ArrayList<Product>();
        for (Product product : products.findAll()) {
            if (product.isActive())
                result.add(product);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    // 购买方法：预检（仅提示）→ 原子扣库存 → 原子扣款 → 建单，任一步失败按序补偿
    @Override
    public synchronized final ServiceResult<Void> purchase(String userId, String productId, int quantity) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (productId == null || productId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "productId must not be blank");
        Product toBuy = products.findById(productId);
        // 商品不存在或已下架
        if (toBuy == null || !toBuy.isActive())
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 数量不合法
        if (quantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Quantity must be positive");
        // 预检（仅提示）：库存是否充足，真正裁决由原子 deductStock 决定
        // 库存不足属于「请求合法但资源状态冲突」，与原子扣减失败统一返回 CONFLICT
        if (toBuy.getStock() < quantity)
            return ServiceResult.failure(StatusCode.CONFLICT, "Insufficient stock");
        // 支付边界一次性换算：double 元 → long 分
        double totalPrice = toBuy.getPrice() * quantity;
        long totalCents = Math.round(totalPrice * 100);
        // 换算后非正（如单价低于半分的退化商品）直接拒绝，避免把 debit(0) 送进钱包触发契约异常
        if (totalCents <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "computed total must be positive");
        // 预检（仅提示）：余额是否充足，真正裁决由原子 debit 决定
        BankAccount account = wallet.findByUserId(userId);
        if ((account == null ? 0L : account.getBalanceCents()) < totalCents)
            return ServiceResult.failure(StatusCode.PAYMENT_REQUIRED, "Insufficient balance");
        // 原子扣库存：锁内 stock>=qty 才扣，false 说明被并发抢先
        if (!products.deductStock(productId, quantity))
            return ServiceResult.failure(StatusCode.CONFLICT, "Product stock changed; retry purchase");
        // 订单编号提前生成，供流水备注引用，实现「这笔扣款对应哪张订单」的双向追溯
        String orderId = UUID.randomUUID().toString();
        // 原子扣款 + 记流水：同一事务内 balance>=cents 才扣并写入 PURCHASE 流水
        WalletMutation debit;
        try {
            debit = wallet.debit(userId, totalCents, WalletTransactionType.PURCHASE, userId, "order " + orderId);
        } catch (IllegalStateException storageFailure) {
            // 余额与流水已一起回滚，但库存是独立资源，需回补，避免半成功
            if (!products.addStock(productId, quantity)) {
                // 回补失败即库存永久偏低，留痕并升级 SERVER_ERROR，不再当成可重试的普通 CONFLICT
                recordCompensationFailure("purchase", userId, orderId, productId, quantity, totalCents,
                        "restore_stock", "addStock returned false after wallet storage failure");
                return ServiceResult.failure(StatusCode.SERVER_ERROR,
                        "Wallet storage failed and stock restore failed; manual reconciliation required");
            }
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "Wallet storage failed; purchase rolled back");
        }
        // applied=false 即余额不足：回补库存
        if (!debit.isApplied()) {
            if (!products.addStock(productId, quantity)) {
                // 扣款未发生但库存已扣且回补失败，留痕并升级 SERVER_ERROR 交人工对账
                recordCompensationFailure("purchase", userId, orderId, productId, quantity, totalCents,
                        "restore_stock", "addStock returned false after debit rejected");
                return ServiceResult.failure(StatusCode.SERVER_ERROR,
                        "Insufficient balance and stock restore failed; manual reconciliation required");
            }
            return ServiceResult.failure(StatusCode.PAYMENT_REQUIRED, "Insufficient balance");
        }
        // 建单：UUID 唯一业务编号；false/异常则退款 + 回补库存
        try {
            Order newOrder = new Order(orderId, userId, productId, quantity, totalPrice,
                    LocalDateTime.now(), toBuy.getName(), toBuy.getPrice());
            if (!orders.create(newOrder))
                return compensatePurchase(userId, orderId, productId, quantity, totalCents, "Could not create order");
            return ServiceResult.ok(null);
        } catch (RuntimeException failure) {
            return compensatePurchase(userId, orderId, productId, quantity, totalCents, "Could not create order");
        }
    }

    // 购买失败的补偿：退款（原子入账 + 记 REFUND 流水）+ 回补库存，逐步检查返回值。
    // 返回 true 表示补偿全部成功，调用方可按普通 CONFLICT 让用户重试；
    // 返回 false 表示至少一步失败并已留痕，调用方须升级为 SERVER_ERROR 交人工对账，绝不静默吞掉不一致
    private boolean refundAndRestore(String userId, String orderId, String productId, int quantity, long cents) {
        boolean complete = true;
        try {
            WalletMutation refund = wallet.credit(userId, cents, WalletTransactionType.REFUND, userId,
                    "purchase compensation");
            if (!refund.isApplied()) {
                recordCompensationFailure("purchase", userId, orderId, productId, quantity, cents, "refund",
                        "credit returned applied=false");
                complete = false;
            }
        } catch (IllegalStateException storageFailure) {
            recordCompensationFailure("purchase", userId, orderId, productId, quantity, cents, "refund",
                    storageFailure.getMessage());
            complete = false;
        }
        if (!products.addStock(productId, quantity)) {
            recordCompensationFailure("purchase", userId, orderId, productId, quantity, cents, "restore_stock",
                    "addStock returned false");
            complete = false;
        }
        return complete;
    }

    // 购买补偿的统一出口：补偿成功按 cleanMessage 返回 CONFLICT（可重试）；补偿不完整则升级 SERVER_ERROR
    // 并点明需人工对账，避免「扣款成功、建单失败、退款又失败」的永久不一致被当成普通冲突静默放过
    private ServiceResult<Void> compensatePurchase(String userId, String orderId, String productId, int quantity,
            long cents, String cleanMessage) {
        return refundAndRestore(userId, orderId, productId, quantity, cents)
                ? ServiceResult.failure(StatusCode.CONFLICT, cleanMessage)
                : ServiceResult.failure(StatusCode.SERVER_ERROR,
                        cleanMessage + "; compensation incomplete, manual reconciliation required");
    }

    // 记录一步补偿失败：写入进程内有界留痕列表 + 打印结构化告警行（含全量对账字段）。
    // 只在 purchase/checkout 的 synchronized 锁内被调用，故对列表的写入天然串行
    private void recordCompensationFailure(String operation, String userId, String orderId, String productId,
            int quantity, long amountCents, String failedStep, String reason) {
        CompensationFailure failure = new CompensationFailure(operation, userId, orderId, productId, quantity,
                amountCents, failedStep, reason, LocalDateTime.now());
        if (compensationFailures.size() >= MAX_COMPENSATION_FAILURES)
            compensationFailures.remove(0);
        compensationFailures.add(failure);
        System.err.println(failure);
    }

    // 供运维/测试查询的补偿失败留痕快照（不可变），用于人工对账或后续重试
    public synchronized List<CompensationFailure> compensationFailures() {
        return Collections.unmodifiableList(new ArrayList<CompensationFailure>(compensationFailures));
    }

    // 根据用户ID查询订单
    @Override
    public final ServiceResult<List<Order>> findOrdersByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        return ServiceResult.ok(
                Collections.unmodifiableList(new ArrayList<Order>(orders.findByUserId(userId))));
    }

    // 补货：操作者身份由通信层 STORE_MANAGE 权限门槛保证，服务层不再接收无用参数
    @Override
    public final ServiceResult<Void> restock(String productId, int additionalStock) {
        if (productId == null || productId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "productId must not be blank");
        if (additionalStock <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "additionalStock must be positive");
        return products.addStock(productId, additionalStock)
                ? ServiceResult.ok(null)
                : ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
    }

    // 新增商品
    @Override
    public final ServiceResult<Product> addProduct(String name, double price, int stock, String description,
            String category) {
        if (!validPrice(price) || stock < 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "price must be a finite positive number and stock must not be negative");
        try {
            Product product = new Product(newProductId(), name, stock, price, description, category);
            products.save(product);
            return ServiceResult.ok(product);
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
    }

    // 更新商品非库存字段
    @Override
    public final ServiceResult<Product> updateProduct(String productId, String name, double price,
            String description, String category) {
        if (productId == null || productId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "productId must not be blank");
        Product existing = products.findById(productId);
        if (existing == null)
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        try {
            // 库存由仓储层保留（updateProduct 契约不碰 stock），此处 existing.getStock() 仅占位、不会覆盖并发扣减
            Product updated = new Product(productId, name, existing.getStock(), price, description, category,
                    existing.isActive());
            return products.updateProduct(updated) ? ServiceResult.ok(updated)
                    : ServiceResult.<Product>failure(StatusCode.CONFLICT, "Product changed; retry update");
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
    }

    // 下架商品：操作者身份由通信层 STORE_MANAGE 权限门槛保证，服务层不再接收无用参数
    @Override
    public final ServiceResult<Void> deactivateProduct(String productId) {
        if (productId == null || productId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "productId must not be blank");
        Product existing = products.findById(productId);
        if (existing == null)
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 库存由仓储层保留（updateProduct 契约不碰 stock），下架只翻转 active
        Product deactivated = new Product(existing.getProductId(), existing.getName(), existing.getStock(),
                existing.getPrice(), existing.getDescription(), existing.getCategory(), false);
        return products.updateProduct(deactivated) ? ServiceResult.ok(null)
                : ServiceResult.failure(StatusCode.CONFLICT, "Product changed; retry update");
    }

    // 加入购物车
    @Override
    public final ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
        if (quantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "quantity must be positive");
        Product product = products.findById(productId);
        if (product == null || !product.isActive())
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        return cart.addItem(new CartItem(UUID.randomUUID().toString(), userId, productId, quantity,
                LocalDateTime.now())) ? ServiceResult.ok(null)
                        : ServiceResult.failure(StatusCode.CONFLICT, "Could not update cart");
    }

    // 删除购物车条目
    @Override
    public final ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
        for (CartItem item : cart.findByUserId(userId)) {
            if (item.getCartItemId().equals(cartItemId)) {
                return cart.removeItem(cartItemId) ? ServiceResult.ok(null)
                        : ServiceResult.failure(StatusCode.NOT_FOUND, "Cart item not found");
            }
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "Cart item not found");
    }

    // 修改购物车条目数量：先按归属校验，条目不属于本人一律按不存在处理，避免指定他人 cartItemId 越权改数量
    @Override
    public final ServiceResult<Void> updateCartQuantity(String userId, String cartItemId, int newQuantity) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (cartItemId == null || cartItemId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "cartItemId must not be blank");
        if (newQuantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "newQuantity must be positive");
        for (CartItem item : cart.findByUserId(userId)) {
            if (item.getCartItemId().equals(cartItemId)) {
                return cart.updateQuantity(cartItemId, newQuantity) ? ServiceResult.ok(null)
                        : ServiceResult.failure(StatusCode.NOT_FOUND, "Cart item not found");
            }
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "Cart item not found");
    }

    // 查询购物车
    @Override
    public final ServiceResult<List<CartItem>> getCart(String userId) {
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<CartItem>(cart.findByUserId(userId))));
    }

    // 购物车明细：读取时与商品实时联表，商品已被物理删除的行直接跳过（无名称与价格可用）；
    // 小计与 checkout 实扣同式，保证前端展示的合计与实际扣款一致
    @Override
    public final ServiceResult<List<CartLine>> getCartDetails(String userId) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        List<CartLine> lines = new ArrayList<CartLine>();
        for (CartItem item : cart.findByUserId(userId)) {
            Product product = products.findById(item.getProductId());
            if (product == null)
                continue;
            long unitPriceCents = Math.round(product.getPrice() * 100);
            long subtotalCents = Math.round(product.getPrice() * item.getQuantity() * 100);
            lines.add(new CartLine(item.getCartItemId(), product.getProductId(), product.getName(), unitPriceCents,
                    item.getQuantity(), subtotalCents, product.isActive(), item.getAddedAt()));
        }
        return ServiceResult.ok(Collections.unmodifiableList(lines));
    }

    // 购物车结账：加锁，与 purchase 共用同一把锁；逐项 原子扣库存 → 原子扣款 → 建单 → 清空
    @Override
    public synchronized final ServiceResult<Void> checkout(String userId) {
        List<CartItem> items = new ArrayList<CartItem>(cart.findByUserId(userId));
        if (items.isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart is empty");
        // 预检（仅提示）：库存 + 余额，真正裁决由逐项原子操作决定
        long estimatedCents = 0L;
        for (CartItem item : items) {
            Product product = products.findById(item.getProductId());
            // 商品不存在或已下架是 NOT_FOUND，库存不足是 CONFLICT，两种语义分开返回
            if (product == null || !product.isActive())
                return ServiceResult.failure(StatusCode.NOT_FOUND, "Cart contains unavailable product");
            if (product.getStock() < item.getQuantity())
                return ServiceResult.failure(StatusCode.CONFLICT, "Insufficient stock in cart");
            long itemCents = Math.round(product.getPrice() * item.getQuantity() * 100);
            // 换算后非正（退化商品）直接拒绝，避免把 debit(0) 送进钱包触发契约异常
            if (itemCents <= 0)
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "computed cart total must be positive");
            estimatedCents += itemCents;
        }
        BankAccount account = wallet.findByUserId(userId);
        if ((account == null ? 0L : account.getBalanceCents()) < estimatedCents)
            return ServiceResult.failure(StatusCode.PAYMENT_REQUIRED, "Insufficient balance");

        List<Order> created = new ArrayList<Order>();
        List<CartItem> deducted = new ArrayList<CartItem>();
        long debitedCents = 0L;
        for (CartItem item : items) {
            Product product = products.findById(item.getProductId());
            // 原子扣库存
            if (!products.deductStock(item.getProductId(), item.getQuantity())) {
                return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.CONFLICT,
                        "Product stock changed; checkout rolled back");
            }
            deducted.add(item);
            // 订单编号提前生成，供流水备注引用
            String orderId = UUID.randomUUID().toString();
            // 原子扣款 + 记 CHECKOUT 流水：同一事务内完成，回滚时再记一笔合计 REFUND 相抵
            long itemCents = Math.round(product.getPrice() * item.getQuantity() * 100);
            WalletMutation debit;
            try {
                debit = wallet.debit(userId, itemCents, WalletTransactionType.CHECKOUT, userId, "order " + orderId);
            } catch (IllegalStateException storageFailure) {
                // 余额与流水已一起回滚，但订单/库存是独立资源，需一并撤销，避免半成功
                return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.SERVER_ERROR,
                        "Wallet storage failed; checkout rolled back");
            }
            if (!debit.isApplied()) {
                return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.CONFLICT,
                        "Insufficient balance; checkout rolled back");
            }
            debitedCents += itemCents;
            // 建单
            try {
                Order order = new Order(orderId, userId, product.getProductId(),
                        item.getQuantity(), product.getPrice() * item.getQuantity(), LocalDateTime.now(),
                        product.getName(), product.getPrice());
                if (!orders.create(order)) {
                    return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.CONFLICT,
                            "Could not create order; checkout rolled back");
                }
                created.add(order);
            } catch (RuntimeException failure) {
                return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.CONFLICT,
                        "Could not create order; checkout rolled back");
            }
        }
        // 清空购物车失败也要回滚，否则用户重试会重复下单
        try {
            cart.clearByUserId(userId);
        } catch (RuntimeException failure) {
            return rollbackCheckoutResult(userId, created, deducted, debitedCents, StatusCode.CONFLICT,
                    "Could not clear cart; checkout rolled back");
        }
        return ServiceResult.ok(null);
    }

    // 结账补偿：撤销订单 + 回补库存 + 退款，逐步检查返回值。
    // 返回 true 表示补偿全部成功，调用方可按 cleanStatus 让用户重试；
    // 返回 false 表示至少一步失败并已留痕，调用方须升级为 SERVER_ERROR 交人工对账
    private boolean rollbackCheckout(String userId, List<Order> created, List<CartItem> deducted, long debitedCents) {
        boolean complete = true;
        for (Order order : created) {
            if (!orders.deleteById(order.getOrderId())) {
                recordCompensationFailure("checkout", userId, order.getOrderId(), order.getProductId(),
                        order.getQuantity(), 0L, "delete_order", "deleteById returned false");
                complete = false;
            }
        }
        for (CartItem item : deducted) {
            if (!products.addStock(item.getProductId(), item.getQuantity())) {
                recordCompensationFailure("checkout", userId, null, item.getProductId(), item.getQuantity(), 0L,
                        "restore_stock", "addStock returned false");
                complete = false;
            }
        }
        if (debitedCents > 0) {
            try {
                WalletMutation refund = wallet.credit(userId, debitedCents, WalletTransactionType.REFUND, userId,
                        "checkout compensation");
                if (!refund.isApplied()) {
                    recordCompensationFailure("checkout", userId, null, null, 0, debitedCents, "refund",
                            "credit returned applied=false");
                    complete = false;
                }
            } catch (IllegalStateException storageFailure) {
                recordCompensationFailure("checkout", userId, null, null, 0, debitedCents, "refund",
                        storageFailure.getMessage());
                complete = false;
            }
        }
        return complete;
    }

    // 结账补偿的统一出口：补偿成功按 cleanStatus/cleanMessage 返回（多为可重试的 CONFLICT）；补偿不完整则升级
    // SERVER_ERROR 并点明需人工对账，避免不一致被当成普通冲突静默放过
    private ServiceResult<Void> rollbackCheckoutResult(String userId, List<Order> created, List<CartItem> deducted,
            long debitedCents, StatusCode cleanStatus, String cleanMessage) {
        return rollbackCheckout(userId, created, deducted, debitedCents)
                ? ServiceResult.failure(cleanStatus, cleanMessage)
                : ServiceResult.failure(StatusCode.SERVER_ERROR,
                        cleanMessage + "; rollback incomplete, manual reconciliation required");
    }

    // 查询所有订单
    @Override
    public final ServiceResult<List<Order>> findAllOrders() {
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<Order>(orders.findAll())));
    }

    // 热销商品排行
    @Override
    public final ServiceResult<List<Product>> listHotProducts(int limit) {
        if (limit <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "limit must be positive");
        List<Object[]> sales = orders.findSalesVolume();
        List<Product> result = new ArrayList<Product>();
        for (Object[] row : sales) {
            Product product = products.findById(String.valueOf(row[0]));
            if (product != null && product.isActive())
                result.add(product);
            if (result.size() >= limit)
                break;
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    // 按分类列出商品
    @Override
    public final ServiceResult<List<Product>> listProducts(String category) {
        if (category == null || category.trim().isEmpty())
            return listProducts();
        List<Product> result = new ArrayList<Product>();
        for (Product product : products.findAll()) {
            if (product.isActive() && category.trim().equals(product.getCategory()))
                result.add(product);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    // 查询余额：无账户返回 0
    @Override
    public final long getBalance(String userId) {
        if (userId == null || userId.trim().isEmpty())
            return 0L;
        BankAccount account = wallet.findByUserId(userId);
        return account == null ? 0L : account.getBalanceCents();
    }

    // 本人充值：仅增加，cents 必须为正且受单笔/单日上限约束，走 credit（懒建户），入账后记一笔 RECHARGE 流水。
    // synchronized：与 purchase/checkout 共用同一把锁，串行化「查今日累计→入账」，防并发绕过单日上限
    @Override
    public synchronized final ServiceResult<Void> recharge(String userId, long cents) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (cents <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "recharge amount must be positive");
        if (cents > MAX_SINGLE_RECHARGE_CENTS)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "single recharge exceeds the allowed limit");
        // 单日累计上限：ledger 读故障（IllegalStateException）故意穿透，由通信层收敛为 SERVER_ERROR
        // （fail-closed：宁可拒绝也不放行无上限充值）
        if (sumTodayRecharges(userId) + cents > MAX_DAILY_RECHARGE_CENTS)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "daily recharge limit exceeded");
        // 原子入账 + 记 RECHARGE 流水：同一事务内完成，存储故障回滚余额并返回 SERVER_ERROR
        WalletMutation credit;
        try {
            credit = wallet.credit(userId, cents, WalletTransactionType.RECHARGE, userId, null);
        } catch (IllegalStateException storageFailure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "Wallet storage failed; recharge rolled back");
        }
        if (!credit.isApplied())
            return ServiceResult.failure(StatusCode.CONFLICT, "Could not recharge account");
        return ServiceResult.ok(null);
    }

    // 汇总某用户「今日」的 RECHARGE 流水金额（分）：RECHARGE 记正数，直接累加
    private long sumTodayRecharges(String userId) {
        LocalDate today = LocalDate.now();
        long sum = 0L;
        for (WalletTransaction transaction : wallet.findTransactionsByUserId(userId)) {
            if (transaction.getType() == WalletTransactionType.RECHARGE
                    && today.equals(transaction.getCreatedAt().toLocalDate())) {
                sum += transaction.getAmountCents();
            }
        }
        return sum;
    }

    // 管理员校正余额：目标余额非负，走 setBalance（绝对设置）；权限校验在 StoreMessageHandler，
    // 流水记差额并留下操作者编号，让「谁改的」不再丢失
    @Override
    public final ServiceResult<Void> adjustBalance(String adminId, String userId, long newBalanceCents) {
        if (adminId == null || adminId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "adminId must not be blank");
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (newBalanceCents < 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "balance must not be negative");
        // 绝对设置余额 + 记 ADJUST 流水：仓储在同一事务/锁内读实际旧值算差额，并发校正被串行化，
        // 逐笔流水累加恒等于最终余额；操作者编号一并落流水，让「谁改的」不再丢失
        WalletMutation adjust;
        try {
            adjust = wallet.setBalance(userId, newBalanceCents, WalletTransactionType.ADJUST, adminId, null);
        } catch (IllegalStateException storageFailure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "Wallet storage failed; adjust rolled back");
        }
        if (!adjust.isApplied())
            return ServiceResult.failure(StatusCode.CONFLICT, "Could not adjust balance");
        return ServiceResult.ok(null);
    }

    // 本人流水：按记账时间升序，无流水返回空列表
    @Override
    public final ServiceResult<List<WalletTransaction>> listTransactions(String userId) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        return ServiceResult.ok(Collections.unmodifiableList(
                new ArrayList<WalletTransaction>(wallet.findTransactionsByUserId(userId))));
    }

    private static boolean validPrice(double price) {
        return Double.isFinite(price) && price > 0;
    }

    private static String newProductId() {
        return "P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
    }
}
