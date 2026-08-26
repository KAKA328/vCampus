package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory store implementation for early integration and UI demos. */
public final class InMemoryStoreService implements StoreService {
    private final Map<String, Product> productsById = new LinkedHashMap<String, Product>();
    private final List<StoreOrder> orders = new ArrayList<StoreOrder>();

    public InMemoryStoreService() {
        this(Arrays.asList(
                new Product("P001", "校园笔记本", 20),
                new Product("P002", "黑色签字笔", 100),
                new Product("P003", "校园卡套", 30)));
    }

    public InMemoryStoreService(List<Product> products) {
        for (Product product : products) {
            productsById.put(product.getProductId(), product);
        }
    }

    @Override
    public synchronized ServiceResult<List<Product>> listProducts() {
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<Product>(productsById.values())));
    }

    @Override
    public synchronized ServiceResult<StoreOrder> purchase(String buyerId, String productId, int quantity) {
        String normalizedBuyerId = normalize(buyerId);
        String normalizedProductId = normalize(productId);
        if (normalizedBuyerId == null || normalizedProductId == null || quantity <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "buyerId, productId and positive quantity are required");
        }
        Product product = productsById.get(normalizedProductId);
        if (product == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "product not found");
        }
        if (product.getStock() < quantity) {
            return ServiceResult.failure(StatusCode.CONFLICT, "product stock is insufficient");
        }
        Product updated = new Product(product.getProductId(), product.getName(), product.getStock() - quantity);
        productsById.put(updated.getProductId(), updated);
        StoreOrder order = new StoreOrder(UUID.randomUUID().toString(),
                normalizedBuyerId, product.getProductId(), product.getName(), quantity);
        orders.add(order);
        return ServiceResult.ok(order);
    }

    @Override
    public synchronized ServiceResult<List<StoreOrder>> ordersFor(String buyerId) {
        String normalizedBuyerId = normalize(buyerId);
        if (normalizedBuyerId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "buyerId must not be blank");
        }
        List<StoreOrder> matches = new ArrayList<StoreOrder>();
        for (StoreOrder order : orders) {
            if (order.getBuyerId().equals(normalizedBuyerId)) {
                matches.add(order);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(matches));
    }

    @Override
    public synchronized ServiceResult<List<StoreOrder>> allOrders() {
        return ServiceResult.ok(Collections.unmodifiableList(new ArrayList<StoreOrder>(orders)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
