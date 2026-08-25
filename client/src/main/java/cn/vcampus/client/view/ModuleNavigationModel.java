package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Decides which module entries should be visible for each role. */
public final class ModuleNavigationModel {
    public List<String> visibleModules(Role role) {
        if (role == Role.ADMIN) {
            return Arrays.asList("用户管理", "学籍管理", "选课管理", "图书管理", "商店管理");
        }
        if (role == Role.STUDENT) {
            return Arrays.asList("学籍信息", "选课系统", "图书馆", "商店");
        }
        if (role == Role.TEACHER) {
            return Arrays.asList("学籍查询", "选课系统");
        }
        if (role == Role.LIBRARIAN) {
            return Collections.singletonList("图书馆");
        }
        if (role == Role.STORE_MANAGER) {
            return Collections.singletonList("商店");
        }
        return Collections.emptyList();
    }
}
