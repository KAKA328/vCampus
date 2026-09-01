package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 默认商店业务，通过提供商品仓库和订单仓库以实现业务逻辑
public final class DefaultStoreService implements StoreService {
    private final ProductRepository products;// 商品仓库
    private final CartRepository cart;// 购物车仓库
    private final OrderRepository orders;// 订单仓库

    public DefaultStoreService(ProductRepository products, OrderRepository orders, CartRepository cart) {
        this.products = products;
        this.orders = orders;
        this.cart = cart;
    }

    // 依赖注入
    public DefaultStoreService(ProductRepository products, OrderRepository orders) {
        this(products, orders, null);
    }

    // 列出所有商品，使用serviceresult类的ok方法打包返回
    @Override
    public final ServiceResult<List<Product>> listProducts() {
        List<Product> productsList = products.findAll();
        List<Product> activatedProducts = new ArrayList<Product>();
        for (Product product : productsList) {
            if (product.isActive()) {
                activatedProducts.add(product);
            }
        }
        return ServiceResult.ok(activatedProducts);
    }

    // 购买方法
    @Override
    public final ServiceResult<Void> purchase(String userId, String productId, int quantity) {
        ServiceResult<Void> tryBuyResult = tryBuy(userId, productId, quantity);
        if (tryBuyResult.getStatus() != StatusCode.OK) {
            return tryBuyResult;
        }
        // 商品存在且数量充足
        else {
            Product toBuy = products.findById(productId);
            // 计算总价
            double totalPrice = toBuy.getPrice() * quantity;
            // 刷新库存
            products.updateStock(productId, toBuy.getStock() - quantity);
            // 创建订单,使用随机订单编号
            Order newOrder = new Order(UUID.randomUUID().toString(), userId, productId, quantity, totalPrice,
                    LocalDateTime.now(), toBuy.getName(), toBuy.getPrice());
            orders.create(newOrder);
            // 返回成功
            return ServiceResult.ok(null);
        }
    }

    // 尝试购买，但是不买，检验是否能够购买成功
    private final ServiceResult<Void> tryBuy(String userId, String productId, int quantity) {
        if (userId == null || userId.trim().isEmpty() || productId == null || productId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Invalid user ID or product ID");
        Product toBuy = products.findById(productId);
        // 没有目标商品
        if (toBuy == null)
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 数量不合法
        else if (quantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Quantity must be positive");
        // 商品数量不够
        else if (toBuy.getStock() < quantity)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "No enough stock");
        return ServiceResult.ok(null);

    }

    // 根据用户ID查询订单
    @Override
    public final ServiceResult<List<Order>> findOrdersByUserId(String userId) {
        return ServiceResult.ok(orders.findByUserId(userId));
    }

    // 补货
    @Override
    public final ServiceResult<Void> restock(String userId, String productId, int additionalStock) {
        // 无效货物数量
        if (additionalStock <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Additional stock must be positive");
        else if (!products.addStock(productId, additionalStock))
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        else
            return ServiceResult.ok(null);
    }

    // 新增商品
    @Override
    public final ServiceResult<Product> addProduct(String name, double price, int stock, String description,
            String category) {
        // 检查参数合法性
        if (name == null || name.trim().isEmpty() || price < 0 || stock < 0 || description == null
                || description.trim().isEmpty() || category == null
                || category.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "Invalid product parameters, please check the name, price, stock, description, and category");
        } else {
            Product newProduct = new Product(UUID.randomUUID().toString(), name, stock, price, description, category);
            products.save(newProduct);
            return ServiceResult.ok(newProduct);
        }
    }

    // 更新商品非库存字段
    @Override
    public final ServiceResult<Product> updateProduct(String productId, String name, double price,
            String description, String category) {
        Product product = products.findById(productId);
        // 检查参数合法性
        if (name == null || name.trim().isEmpty() || price < 0 || description == null || description.trim().isEmpty()
                || category == null
                || category.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "Invalid product parameters, please check the name, price, description, and category");
        }
        // 如果商品不存在
        else if (product == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        } else {

            Product updatedProduct = new Product(productId, name, product.getStock(), price, description, category,
                    product.isActive());
            products.updateProduct(updatedProduct);
            return ServiceResult.ok(updatedProduct);
        }
    }

    // 下架商品
    @Override
    public final ServiceResult<Void> deactivateProduct(String userId, String productId) {
        Product product = products.findById(productId);
        if (product == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        } else if (!product.isActive())
            return ServiceResult.ok(null);
        else {
            Product updatedProduct = new Product(productId, product.getName(), product.getStock(), product.getPrice(),
                    product.getDescription(), product.getCategory(), false);
            products.updateProduct(updatedProduct);
            return ServiceResult.ok(null);
        }
    }

    // 加入购物车
    @Override
    public final ServiceResult<Void> addToCart(String userId, String productId, int quantity) {

        if (cart == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart not enabled");
        }
        if (userId == null || userId.trim().isEmpty() || productId == null || productId.trim().isEmpty()
                || quantity <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Invalid parameters");
        }
        Product product = products.findById(productId);
        if (product == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        }
        if (!product.isActive()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Product is not active");
        }
        CartItem newItem = new CartItem(UUID.randomUUID().toString(), userId, productId, quantity, LocalDateTime.now());
        cart.addItem(newItem);
        return ServiceResult.ok(null);
    }

    // 删除购物车条目
    @Override
    public final ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
        if (cart == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart not enabled");
        }
        if (userId == null || userId.trim().isEmpty() || cartItemId == null || cartItemId.trim().isEmpty())
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Invalid parameters");
        for (CartItem item : cart.findByUserId(userId)) {
            if (item.getCartItemId().equals(cartItemId)) {
                cart.removeItem(cartItemId);
                return ServiceResult.ok(null);
            }
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "Cart item not found");
    }

    // 查询购物车
    @Override
    public final ServiceResult<List<CartItem>> getCart(String userId) {
        if (cart == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart not enabled");
        }
        if (userId == null || userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Invalid parameters");
        }
        return ServiceResult.ok(cart.findByUserId(userId));
    }

    // 购物车结账
    @Override
    public final ServiceResult<Void> checkout(String userId) {
        if (cart == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Cart not enabled");
        }
        if (userId == null || userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Invalid parameters");
        }
        List<String> failedProducts = new ArrayList<>();// 失败名单
        List<CartItem> cartItems = cart.findByUserId(userId);
        for (CartItem item : cartItems) {
            // 使用私有函数先模拟购买，检验是否能够购买成功
            ServiceResult<Void> result = this.tryBuy(userId, item.getProductId(), item.getQuantity());
            if (result.getStatus() != StatusCode.OK) {
                Product p = products.findById(item.getProductId());
                String name = p != null ? p.getName() : item.getProductId(); // 查不到就用编号顶名字
                failedProducts.add(name);
            }
        }
        // 如果没有失败的购买
        if (failedProducts.isEmpty()) {
            for (CartItem item : cartItems) {
                this.purchase(userId, item.getProductId(), item.getQuantity());
            }
            cart.clearByUserId(userId);// 全部购买成功，清空购物车
            return ServiceResult.ok(null);
        } else {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "Failed to purchase: " + String.join(", ", failedProducts));
        }
    }

    // 查询所有订单
    @Override
    public final ServiceResult<List<Order>> findAllOrders() {
        return ServiceResult.ok(orders.findAll());
    }

    // 热销商品排行，按销量取前 limit 名，只展示上架商品
    @Override
    public final ServiceResult<List<Product>> listHotProducts(int limit) {
        if (limit <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Limit must be positive");
        List<Map.Entry<String, Integer>> volumeList = orders.findSalesVolume();
        List<Product> hotProducts = new ArrayList<Product>();
        // 取前 limit 名，榜单不足时全取
        int top = Math.min(limit, volumeList.size());
        for (int i = 0; i < top; i++) {
            String productId = volumeList.get(i).getKey();
            Product product = products.findById(productId);
            // 商品已删除或已下架，跳过
            if (product == null || !product.isActive()) {
                continue;
            }
            hotProducts.add(product);
        }
        return ServiceResult.ok(hotProducts);
    }

    // 按分类列出商品category为null时不过滤分类
    @Override
    public final ServiceResult<List<Product>> listProducts(String category) {
        List<Product> productsList = products.findAll();
        List<Product> matchedProducts = new ArrayList<Product>();
        for (Product product : productsList) {
            // 分类为 null不挑，非null才比较是否相等
            if (product.isActive() && (category == null || category.equals(product.getCategory()))) {
                matchedProducts.add(product);
            }
        }
        return ServiceResult.ok(matchedProducts);
    }
}
