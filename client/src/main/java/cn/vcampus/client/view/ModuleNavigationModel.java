package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Decides which module entries should be visible for each role. */
public final class ModuleNavigationModel {
    private static final List<ModuleDescriptor> ADMIN_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("用户管理", "维护用户账号、角色权限、登录会话和注销审计。", "可用：用户管理核心流程已接入"),
            new ModuleDescriptor("学籍管理", "维护学生基本信息、班级、专业和联系方式。", "可用：学生档案查询和维护已接入"),
            new ModuleDescriptor("选课管理", "管理课程信息、学生选课、退课和已选课程查询。", "待接入：由选课模块负责人提供页面"),
            new ModuleDescriptor("图书管理", "管理图书信息、借阅、归还和借阅记录。", "可用：馆藏维护和全部借阅记录已接入"),
            new ModuleDescriptor("商店管理", "管理商品信息、库存、购买记录和订单查询。", "待接入：由商店模块负责人提供页面")
    ));

    private static final List<ModuleDescriptor> STUDENT_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("学籍信息", "查看个人学号、班级、专业和基础学籍信息。", "可用：本人档案查询和联系方式维护已接入"),
            new ModuleDescriptor("选课系统", "查询课程、提交选课、退课并查看已选课程。", "可用：课程查询、选课和退课已接入"),
            new ModuleDescriptor("图书馆", "查询图书、办理借阅归还并查看借阅记录。", "可用：馆藏查询、批量借阅、归还和本人记录已接入"),
            new ModuleDescriptor("商店", "浏览商品、提交购买并查看个人购买记录。", "可用：商品查询、购买和订单查询已接入")
    ));

    private static final List<ModuleDescriptor> TEACHER_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("学籍查询", "查看学生基础信息和班级专业信息。", "可用：学生档案只读查询已接入"),
            new ModuleDescriptor("选课系统", "查看授课课程、选课名单并录入成绩。", "可用：课程查询已接入，授课名单和成绩录入待选课模块补齐"),
            new ModuleDescriptor("图书馆", "查询图书、办理借阅归还并查看本人借阅记录。", "可用：馆藏查询、批量借阅、归还和本人记录已接入"),
            new ModuleDescriptor("商店", "浏览商品、提交购买并查看个人购买记录。", "可用：商品查询、购买和订单查询已接入")
    ));

    private static final List<ModuleDescriptor> ACADEMIC_ADMIN_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("学籍管理", "维护学籍信息并执行学业审查。", "可用：学生档案查询和维护已接入"),
            new ModuleDescriptor("选课管理", "维护课程目录、教学班状态和分类型容量。", "可用：课程目录和教学班维护已接入")
    ));

    private static final List<ModuleDescriptor> LIBRARY_MODULES = Collections.singletonList(
            new ModuleDescriptor("图书馆", "维护图书资料、借阅归还和借阅记录。", "可用：馆藏维护和全部借阅记录已接入")
    );

    private static final List<ModuleDescriptor> STORE_MODULES = Collections.singletonList(
            new ModuleDescriptor("商店", "维护商品资料、库存和购买记录。", "可用：商品查询、购买和订单查询已接入，商品维护待商店模块补齐")
    );

    public List<String> visibleModules(Role role) {
        List<String> titles = new ArrayList<String>();
        for (ModuleDescriptor module : visibleModuleCards(role)) {
            titles.add(module.getTitle());
        }
        return Collections.unmodifiableList(titles);
    }

    public List<ModuleDescriptor> visibleModuleCards(Role role) {
        if (role == Role.ADMIN) return ADMIN_MODULES;
        if (role == Role.STUDENT) return STUDENT_MODULES;
        if (role == Role.TEACHER) return TEACHER_MODULES;
        if (role == Role.ACADEMIC_ADMIN) return ACADEMIC_ADMIN_MODULES;
        if (role == Role.LIBRARIAN) return LIBRARY_MODULES;
        if (role == Role.STORE_MANAGER) return STORE_MODULES;
        return Collections.emptyList();
    }

    public ModuleDescriptor findModule(Role role, String title) {
        for (ModuleDescriptor module : visibleModuleCards(role)) {
            if (module.getTitle().equals(title)) {
                return module;
            }
        }
        return null;
    }
}
