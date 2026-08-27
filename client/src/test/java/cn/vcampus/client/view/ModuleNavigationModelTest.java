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
    void teacherSeesTeachingLibraryAndStoreButNotManagementEntries() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertTrue(model.visibleModules(Role.TEACHER).contains("学籍查询"));
        assertTrue(model.visibleModules(Role.TEACHER).contains("选课系统"));
        assertTrue(model.visibleModules(Role.TEACHER).contains("图书馆"));
        assertTrue(model.visibleModules(Role.TEACHER).contains("商店"));
        assertFalse(model.visibleModules(Role.TEACHER).contains("用户管理"));
    }

    @Test
    void academicAdminSeesOnlyAcademicManagementEntries() {
        ModuleNavigationModel model = new ModuleNavigationModel();
        Role academicAdmin = Role.valueOf("ACADEMIC_ADMIN");

        assertEquals(2, model.visibleModules(academicAdmin).size());
        assertTrue(model.visibleModules(academicAdmin).contains("学籍管理"));
        assertTrue(model.visibleModules(academicAdmin).contains("选课管理"));
        assertFalse(model.visibleModules(academicAdmin).contains("图书馆"));
        assertFalse(model.visibleModules(academicAdmin).contains("商店"));
        assertFalse(model.visibleModules(academicAdmin).contains("用户管理"));
    }

    @Test
    void storeManagerCannotSeeCourseSelectionEntry() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertEquals(1, model.visibleModules(Role.STORE_MANAGER).size());
        assertTrue(model.visibleModules(Role.STORE_MANAGER).contains("商店"));
        assertFalse(model.visibleModules(Role.STORE_MANAGER).contains("选课系统"));
        assertFalse(model.visibleModules(Role.STORE_MANAGER).contains("选课管理"));
    }

    @Test
    void studentsAndTeachersUseCourseSelectionPanel() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertTrue(MainFrame.useCourseSelectionPanel(
                Role.STUDENT, model.findModule(Role.STUDENT, "选课系统")));
        assertTrue(MainFrame.useCourseSelectionPanel(
                Role.TEACHER, model.findModule(Role.TEACHER, "选课系统")));
        assertFalse(MainFrame.useCourseSelectionPanel(
                Role.ACADEMIC_ADMIN, model.findModule(Role.ACADEMIC_ADMIN, "选课管理")));
        assertFalse(MainFrame.useCourseSelectionPanel(
                Role.ADMIN, model.findModule(Role.ADMIN, "选课管理")));
    }

    @Test
    void buyerRolesUseStorePanel() {
        ModuleNavigationModel model = new ModuleNavigationModel();

        assertTrue(MainFrame.useStorePanel(Role.STUDENT, model.findModule(Role.STUDENT, "商店")));
        assertTrue(MainFrame.useStorePanel(Role.TEACHER, model.findModule(Role.TEACHER, "商店")));
        assertTrue(MainFrame.useStorePanel(Role.STORE_MANAGER, model.findModule(Role.STORE_MANAGER, "商店")));
        assertFalse(MainFrame.useStorePanel(Role.ACADEMIC_ADMIN, model.findModule(Role.ACADEMIC_ADMIN, "商店")));
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
}
