package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleNavigationModelTest {
    @Test
    void studentSeesStudyLibraryAndStoreEntries() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertTrue(model.visibleModules(Role.STUDENT).contains("学籍信息"));
        assertTrue(model.visibleModules(Role.STUDENT).contains("选课系统"));
        assertTrue(model.visibleModules(Role.STUDENT).contains("图书馆"));
        assertTrue(model.visibleModules(Role.STUDENT).contains("商店"));
        assertFalse(model.visibleModules(Role.STUDENT).contains("用户管理"));
    }

    @Test
    void adminSeesAllManagementEntries() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertTrue(model.visibleModules(Role.ADMIN).contains("用户管理"));
        assertTrue(model.visibleModules(Role.ADMIN).contains("学籍管理"));
        assertTrue(model.visibleModules(Role.ADMIN).contains("选课管理"));
        assertTrue(model.visibleModules(Role.ADMIN).contains("图书管理"));
        assertTrue(model.visibleModules(Role.ADMIN).contains("商店管理"));
    }

    @Test
    void studentModuleCardsHaveReadableDescriptions() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        List<ModuleDescriptor> modules = model.visibleModuleCards(Role.STUDENT);

        assertEquals(model.visibleModules(Role.STUDENT).size(), modules.size());
        for (ModuleDescriptor module : modules) {
            assertFalse(module.getTitle().trim().isEmpty());
            assertTrue(module.getSummary().length() >= 10);
            assertTrue(module.getStatus().contains("待接入") || module.getStatus().contains("可用"));
        }
    }

    @Test
    void userManagementVisibleActionsIncludeAccountCancellation() {
        assertTrue(UserManagementActions.SELF_UNREGISTER.contains("注销账号"));
        assertTrue(UserManagementActions.ADMIN_UNREGISTER.contains("管理员注销"));
    }
}
