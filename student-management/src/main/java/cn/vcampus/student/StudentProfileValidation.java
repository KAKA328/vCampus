package cn.vcampus.student;

import java.time.Year;
import java.util.Arrays;
import java.util.List;

/** Shared format rules for interactive student profile writes; not a migration of stored history. */
public final class StudentProfileValidation {
    private StudentProfileValidation() { }
    public static final int PHONE_LENGTH = 11;
    private static final List<String> STATUSES = Arrays.asList("在读", "休学", "退学", "毕业");
    private static final List<String> GENDERS = Arrays.asList("男", "女", "未知");

    public static void profile(StudentRecord record) {
        if (record == null) throw new IllegalArgumentException("学生档案不能为空");
        identifier(record.getStudentId(), "学号", true);
        identifier(record.getUserId(), "账号", false);
        text(record.getName(), "姓名", 64, true);
        text(record.getDepartmentName(), "院系", 64, false);
        text(record.getMajorName(), "专业", 64, false);
        text(record.getClassId(), "班级", 32, false);
        if (!STATUSES.contains(record.getStatus())) {
            throw new IllegalArgumentException("学籍状态只能为在读、休学、退学或毕业");
        }
        if (!empty(record.getGender()) && !GENDERS.contains(record.getGender())) {
            throw new IllegalArgumentException("性别只能为男、女或未知");
        }
        int maxYear = Year.now().getValue() + 1;
        if (record.getEnrollmentYear() < 1900 || record.getEnrollmentYear() > maxYear) {
            throw new IllegalArgumentException("入学年份应为1900至" + maxYear + "之间的四位年份");
        }
        contacts(record.getPhone(), record.getEmail());
    }

    public static void contacts(String phone, String email) {
        if (!empty(phone) && !phone.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("手机号必须为11位数字，不含空格、字母或符号");
        }
        text(email, "邮箱", 100, false);
        if (!empty(email) && !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")) {
            throw new IllegalArgumentException("邮箱格式不正确，例如 student@example.com");
        }
        if (!empty(email)) {
            String local = email.substring(0, email.indexOf('@'));
            if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
                throw new IllegalArgumentException("邮箱格式不正确");
            }
        }
    }

    private static void identifier(String value, String label, boolean required) {
        text(value, label, 32, required);
        if (!empty(value) && !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(label + "只能包含英文字母、数字、下划线或短横线");
        }
    }
    private static void text(String value, String label, int maximum, boolean required) {
        if (required && (value == null || value.trim().isEmpty())) throw new IllegalArgumentException(label + "不能为空");
        if (value == null) return;
        if (value.length() > maximum) throw new IllegalArgumentException(label + "不能超过" + maximum + "个字符");
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException(label + "不能包含换行或控制字符");
        }
    }
    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
