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
            new ModuleDescriptor("选课管理", "管理课程、教学班、选课轮次、培养方案并审核教师成绩。", "可用：教务选课管理与成绩审核已接入"),
            new ModuleDescriptor("图书管理", "管理图书信息、借阅、归还和借阅记录。", "可用：馆藏维护和全部借阅记录已接入"),
            new ModuleDescriptor("商店", "以消费者身份浏览商品、加入购物车并结算、查看本人订单和钱包。", "可用：商品查询、购买、购物车结算和校园钱包已接入"),
            new ModuleDescriptor("商店管理", "以管理者身份维护商品信息、库存、全部订单和钱包余额校正。", "可用：商品维护、库存补货、全部订单和余额校正已接入")));

    private static final List<ModuleDescriptor> STUDENT_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("学籍信息", "查看个人学号、班级、专业和基础学籍信息。", "可用：本人档案查询和联系方式维护已接入"),
            new ModuleDescriptor("选课系统", "查询课程、提交选课、退课并查看已选课程。", "可用：课程查询、选课和退课已接入"),
            new ModuleDescriptor("图书馆", "查询图书、办理借阅归还并查看借阅记录。", "可用：馆藏查询、批量借阅、归还和本人记录已接入"),
            new ModuleDescriptor("商店", "浏览商品、提交购买并查看个人购买记录。", "可用：商品查询、购买、购物车结算和校园钱包已接入")));

    private static final List<ModuleDescriptor> TEACHER_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("教师信息", "查看本人教师档案和在职状态。", "可用：本人教师档案查询已接入"),
            new ModuleDescriptor("选课系统", "查看本人教学班、学生名单并录入成绩。", "可用：教学班、名单、成绩草稿、文件导入和提交审核已接入"),
            new ModuleDescriptor("图书馆", "查询图书、办理借阅归还并查看本人借阅记录。", "可用：馆藏查询、批量借阅、归还和本人记录已接入"),
            new ModuleDescriptor("商店", "浏览商品、提交购买并查看个人购买记录。", "可用：商品查询、购买、购物车结算和校园钱包已接入")));

    private static final List<ModuleDescriptor> ACADEMIC_ADMIN_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("学籍管理", "维护学籍信息并执行学业审查。", "可用：学生档案查询和维护已接入"),
            new ModuleDescriptor("选课管理", "维护课程、教学班、选课轮次、培养方案并审核教师成绩。", "可用：课程维护和成绩审核已接入")));

    private static final List<ModuleDescriptor> LIBRARY_MODULES = Collections.singletonList(
            new ModuleDescriptor("图书馆", "维护图书资料、借阅归还和借阅记录。", "可用：馆藏维护和全部借阅记录已接入"));

    private static final List<ModuleDescriptor> STORE_MODULES = Collections.unmodifiableList(Arrays.asList(
            new ModuleDescriptor("商店", "以消费者身份浏览商品、加入购物车并结算、查看本人订单和钱包。", "可用：商品查询、购买、购物车结算和校园钱包已接入"),
            new ModuleDescriptor("商店管理", "以管理者身份维护商品信息、库存、全部订单和钱包余额校正。", "可用：商品维护、库存补货、全部订单和余额校正已接入")));

    public List<String> visibleModules(Role role) {
        List<String> titles = new ArrayList<String>();
        for (ModuleDescriptor module : visibleModuleCards(role)) {
            titles.add(module.getTitle());
        }
        return Collections.unmodifiableList(titles);
    }

    public List<ModuleDescriptor> visibleModuleCards(Role role) {
        if (role == Role.ADMIN)
            return ADMIN_MODULES;
        if (role == Role.STUDENT)
            return STUDENT_MODULES;
        if (role == Role.TEACHER)
            return TEACHER_MODULES;
        if (role == Role.ACADEMIC_ADMIN)
            return ACADEMIC_ADMIN_MODULES;
        if (role == Role.LIBRARIAN)
            return LIBRARY_MODULES;
        if (role == Role.STORE_MANAGER)
            return STORE_MODULES;
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
