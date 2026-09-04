package cn.vcampus.store;

import java.time.LocalDateTime;

/**
 * 一条补偿失败留痕。购买/结账采用「按序执行、任一步失败按序回滚」的应用层补偿，
 * 当回滚补偿本身（退款 credit / 回补库存 addStock / 撤单 deleteById）也失败时，
 * 系统会进入「钱已扣、订单没建成、退款又失败」这类无法自动收敛的永久不一致状态。
 *
 * <p>
 * 过去这类失败只打印一行日志、调用方仍返回普通 CONFLICT，等于把不一致静默吞掉。
 * 现在每发生一步补偿失败都记录一条本对象：服务层据此把返回值升级为 SERVER_ERROR，
 * 并把明细同时写入结构化告警日志和进程内可查询的留痕列表，供运维人工对账或后续重试。
 *
 * <p>
 * 本对象不可变，字段含义：
 * <ul>
 * <li>{@code operation}：触发的业务，取值 {@code "purchase"} 或 {@code "checkout"}；</li>
 * <li>{@code orderId} / {@code productId}：便于定位的关联编号，无法确定时为 {@code null}；</li>
 * <li>{@code quantity} / {@code amountCents}：待回补的数量与待退款金额（分），供人工核对；</li>
 * <li>{@code failedStep}：失败的补偿步骤，取值 {@code "refund"} / {@code "restore_stock"}
 * / {@code "delete_order"}；</li>
 * <li>{@code reason}：失败原因（返回值语义或底层异常信息）。</li>
 * </ul>
 */
public final class CompensationFailure {
    private final String operation;// 触发补偿的业务：purchase / checkout
    private final String userId;// 受影响用户
    private final String orderId;// 关联订单编号，可为 null
    private final String productId;// 关联商品编号，可为 null
    private final int quantity;// 待回补数量
    private final long amountCents;// 待退款金额，单位分
    private final String failedStep;// 失败步骤：refund / restore_stock / delete_order
    private final String reason;// 失败原因
    private final LocalDateTime occurredAt;// 发生时间

    public CompensationFailure(String operation, String userId, String orderId, String productId, int quantity,
            long amountCents, String failedStep, String reason, LocalDateTime occurredAt) {
        this.operation = operation;
        this.userId = userId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.amountCents = amountCents;
        this.failedStep = failedStep;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String getOperation() {
        return operation;
    }

    public String getUserId() {
        return userId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    /** 结构化告警行：固定前缀 + 全量对账字段，便于日志采集与人工检索。 */
    @Override
    public String toString() {
        return "[COMPENSATION-FAILURE] operation=" + operation + " user=" + userId + " order=" + orderId
                + " product=" + productId + " qty=" + quantity + " amountCents=" + amountCents + " step=" + failedStep
                + " reason=" + reason + " at=" + occurredAt;
    }
}
