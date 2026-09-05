package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StorePanel 的可测面只覆盖不依赖 Swing 事件线程的静态判定：管理权限门槛与状态码文案。
 * 商品/购物车/流水的行映射拆到 StoreRowMapper，由 StoreRowMapperTest 单独覆盖，
 * 这里对齐 LibraryPanelTest 的写法，只做纯静态断言，保证无头环境可跑。
 */
class StorePanelTest {
    @Test
    void onlyAdminAndStoreManagerCanManageCatalog() {
        assertTrue(StorePanel.canManage(Role.ADMIN));
        assertTrue(StorePanel.canManage(Role.STORE_MANAGER));
        assertFalse(StorePanel.canManage(Role.STUDENT));
        assertFalse(StorePanel.canManage(Role.TEACHER));
        assertFalse(StorePanel.canManage(Role.LIBRARIAN));
        assertFalse(StorePanel.canManage(Role.ACADEMIC_ADMIN));
    }

    @Test
    void statusMessageExplainsEachFailureInChinese() {
        assertEquals("请求数据不正确，请检查填写的数量或金额", StorePanel.statusMessage(StatusCode.BAD_REQUEST));
        assertEquals("登录状态已失效，请重新登录", StorePanel.statusMessage(StatusCode.UNAUTHORIZED));
        assertEquals("当前账号没有执行该商店操作的权限", StorePanel.statusMessage(StatusCode.FORBIDDEN));
        assertEquals("商品、订单或购物车条目不存在", StorePanel.statusMessage(StatusCode.NOT_FOUND));
        assertEquals("余额不足，请先充值", StorePanel.statusMessage(StatusCode.PAYMENT_REQUIRED));
        assertEquals("库存或余额已发生变化，请刷新后重试", StorePanel.statusMessage(StatusCode.CONFLICT));
    }

    @Test
    void statusMessageFallsBackToGenericServerError() {
        // OK 与 SERVER_ERROR 没有专属文案，统一走兜底提示，避免界面出现空白状态
        assertEquals("服务器处理商店请求失败", StorePanel.statusMessage(StatusCode.SERVER_ERROR));
        assertEquals("服务器处理商店请求失败", StorePanel.statusMessage(StatusCode.OK));
    }

    @Test
    void localFailureTextGivesChineseHintWithoutLeakingInternalMessage() {
        // C1：命令构造类可预期异常（如 A1 曾经的空说明 IAE）给中文提示，绝不裸奔内部英文 getMessage
        String illegalArg = StorePanel.localFailureText(new IllegalArgumentException("description cannot be empty"));
        assertEquals("提交的数据不完整或格式有误，请检查后重试", illegalArg);
        assertFalse(illegalArg.contains("description cannot be empty"));
        // 其它未知本地异常走通用中文兜底，同样不泄露 getMessage
        String other = StorePanel.localFailureText(new IllegalStateException("some internal detail"));
        assertEquals("商店请求失败，请稍后重试", other);
        assertFalse(other.contains("some internal detail"));
    }
}
