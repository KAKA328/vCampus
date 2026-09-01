package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

// 默认商店业务，通过提供商品仓库和订单仓库以实现业务逻辑
public final class DefaultStoreService implements StoreService {
    private final ProductRepository products;// 商品仓库
    private final OrderRepository orders;// 订单仓库
    private final CartRepository cart;

    // 依赖注入
    public DefaultStoreService(ProductRepository products, OrderRepository orders) {
        this(products, orders, new InMemoryCartRepository());
    }

    public DefaultStoreService(ProductRepository products, OrderRepository orders, CartRepository cart) {
        if (products == null || orders == null || cart == null) {
            throw new IllegalArgumentException("store repositories must not be null");
        }
        this.products = products;
        this.orders = orders;
        this.cart = cart;
    }

    // 列出所有商品，使用serviceresult类的ok方法打包返回
    @Override
    public synchronized final ServiceResult<List<Product>> listProducts() {
        List<Product> result = new ArrayList<Product>();
        for (Product product : products.findAll()) {
            if (product.isActive()) result.add(product);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    // 购买方法
    @Override
    public synchronized final ServiceResult<Void> purchase(String userId, String productId, int quantity) {
        if (userId == null || userId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        Product toBuy = products.findById(productId);
        // 没有目标商品
        if (toBuy == null)
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 数量不合法
        else if (quantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Quantity must be positive");
        else if (!toBuy.isActive())
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 商品数量不够
        else if (toBuy.getStock() < quantity)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "No enough stock");
        // 商品存在且数量充足
        else {
            // 计算总价
            double totalPrice = toBuy.getPrice() * quantity;
            // 刷新库存
            if (!products.updateStock(productId, toBuy.getStock() - quantity))
                return ServiceResult.failure(StatusCode.CONFLICT, "Product stock changed; retry purchase");
            // 创建订单,使用随机订单编号
            try {
                Order newOrder = new Order(UUID.randomUUID().toString(), userId, productId, quantity, totalPrice,
                        LocalDateTime.now(), toBuy.getName(), toBuy.getPrice());
                if (!orders.create(newOrder)) {
                    products.updateStock(productId, toBuy.getStock());
                    return ServiceResult.failure(StatusCode.CONFLICT, "Could not create order");
                }
                return ServiceResult.ok(null);
            } catch (RuntimeException failure) {
                products.updateStock(productId, toBuy.getStock());
                return ServiceResult.failure(StatusCode.CONFLICT, "Could not create order");
            }
        }
    }

    // 根据用户ID查询订单
    @Override
    public final ServiceResult<List<Order>> findOrdersByUserId(String userId) {
        return ServiceResult.ok(orders.findByUserId(userId));
    }

    // 补货
    @Override
    public final ServiceResult<Void> restock(String userId, String productId, int additionalStock) {
        if (additionalStock <= 0) return ServiceResult.failure(StatusCode.BAD_REQUEST, "additionalStock must be positive");
        return products.addStock(productId, additionalStock)
                ? ServiceResult.ok(null)
                : ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
    }

    // 新增商品
    @Override
    public final ServiceResult<Product> addProduct(String name, double price, int stock, String description,
            String category) {
        if (!validPrice(price) || stock < 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "price must be a finite positive number and stock must not be negative");
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
        Product existing = products.findById(productId);
        if (existing == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        try {
            Product updated = new Product(productId, name, existing.getStock(), price, description, category,
                    existing.isActive());
            return products.updateProduct(updated) ? ServiceResult.ok(updated)
                    : ServiceResult.<Product>failure(StatusCode.CONFLICT, "Product changed; retry update");
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
    }

    // 下架商品
    @Override
    public final ServiceResult<Void> deactivateProduct(String userId, String productId) {
        Product existing = products.findById(productId);
        if (existing == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        Product deactivated = new Product(existing.getProductId(), existing.getName(), existing.getStock(),
                existing.getPrice(), existing.getDescription(), existing.getCategory(), false);
        return products.updateProduct(deactivated) ? ServiceResult.ok(null)
                : ServiceResult.failure(StatusCode.CONFLICT, "Product changed; retry update");
    }

    // 加入购物车
    @Override
    public final ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
        if (quantity <= 0) return ServiceResult.failure(StatusCode.BAD_REQUEST, "quantity must be positive");
        Product product = products.findById(productId);
        if (product == null || !product.isActive()) return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
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

    // 查询购物车
    @Override
    public final ServiceResult<List<CartItem>> getCart(String userId) {
        return ServiceResult.ok(cart.findByUserId(userId));
    }

    // 购物车结账
    @Override
    public final ServiceResult<Void> checkout(String userId) {
        List<CartItem> items = new ArrayList<CartItem>(cart.findByUserId(userId));
        if (items.isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart is empty");
        for (CartItem item : items) {
            Product product = products.findById(item.getProductId());
            if (product == null || !product.isActive() || product.getStock() < item.getQuantity())
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart contains unavailable stock");
        }
        List<Order> created = new ArrayList<Order>();
        List<Product> reserved = new ArrayList<Product>();
        for (CartItem item : items) {
            Product product = products.findById(item.getProductId());
            Product reservedProduct = new Product(product.getProductId(), product.getName(),
                    product.getStock() - item.getQuantity(), product.getPrice(), product.getDescription(),
                    product.getCategory(), product.isActive());
            if (!products.updateStock(product.getProductId(), reservedProduct.getStock())) {
                rollbackCheckout(created, reserved);
                return ServiceResult.failure(StatusCode.CONFLICT, "Product stock changed; checkout rolled back");
            }
            reserved.add(product);
            Order order;
            try {
                order = new Order(UUID.randomUUID().toString(), userId, product.getProductId(), item.getQuantity(),
                        product.getPrice() * item.getQuantity(), LocalDateTime.now(), product.getName(), product.getPrice());
                if (orders.create(order)) {
                    created.add(order);
                    continue;
                }
            } catch (RuntimeException failure) {
                rollbackCheckout(created, reserved);
                products.updateStock(product.getProductId(), product.getStock());
                return ServiceResult.failure(StatusCode.CONFLICT, "Could not create order; checkout rolled back");
            }
            rollbackCheckout(created, reserved);
            products.updateStock(product.getProductId(), product.getStock());
            return ServiceResult.failure(StatusCode.CONFLICT, "Could not create order; checkout rolled back");
        }
        cart.clearByUserId(userId);
        return ServiceResult.ok(null);
    }

    private void rollbackCheckout(List<Order> created, List<Product> reserved) {
        for (Order order : created) orders.deleteById(order.getOrderId());
        for (Product product : reserved) products.updateStock(product.getProductId(), product.getStock());
    }

    // 查询所有订单
    @Override
    public final ServiceResult<List<Order>> findAllOrders() {
        return ServiceResult.ok(orders.findAll());
    }

    // 热销商品排行
    @Override
    public final ServiceResult<List<Product>> listHotProducts(int limit) {
        if (limit <= 0) return ServiceResult.failure(StatusCode.BAD_REQUEST, "limit must be positive");
        List<Object[]> sales = orders.findSalesVolume();
        List<Product> result = new ArrayList<Product>();
        for (Object[] row : sales) {
            Product product = products.findById(String.valueOf(row[0]));
            if (product != null && product.isActive()) result.add(product);
            if (result.size() >= limit) break;
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    // 按分类列出商品
    @Override
    public final ServiceResult<List<Product>> listProducts(String category) {
        if (category == null || category.trim().isEmpty()) return listProducts();
        List<Product> result = new ArrayList<Product>();
        for (Product product : products.findAll()) {
            if (product.isActive() && category.trim().equals(product.getCategory())) result.add(product);
        }
        return ServiceResult.ok(Collections.unmodifiableList(result));
    }

    private static boolean validPrice(double price) {
        return Double.isFinite(price) && price > 0;
    }

    private static String newProductId() {
        return "P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
    }
}
