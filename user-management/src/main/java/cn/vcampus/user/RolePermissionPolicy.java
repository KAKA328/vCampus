package cn.vcampus.user;

import cn.vcampus.common.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Central role-to-permission matrix for the user module. */
public final class RolePermissionPolicy {
    private final Map<Role, Set<Permission>> matrix = new EnumMap<Role, Set<Permission>>(Role.class);

    public RolePermissionPolicy() {
        matrix.put(Role.ADMIN, EnumSet.allOf(Permission.class));
        matrix.put(Role.STUDENT, EnumSet.of(
                Permission.USER_SELF_READ,
                Permission.STUDENT_READ,
                Permission.COURSE_READ,
                Permission.COURSE_SELECT,
                Permission.LIBRARY_READ,
                Permission.LIBRARY_BORROW,
                Permission.STORE_READ,
                Permission.STORE_PURCHASE));
        matrix.put(Role.TEACHER, EnumSet.of(
                Permission.USER_SELF_READ,
                Permission.STUDENT_READ,
                Permission.COURSE_READ,
                Permission.GRADE_WRITE,
                Permission.LIBRARY_READ,
                Permission.LIBRARY_BORROW,
                Permission.STORE_READ,
                Permission.STORE_PURCHASE));
        matrix.put(Role.ACADEMIC_ADMIN, EnumSet.of(
                Permission.STUDENT_READ,
                Permission.STUDENT_WRITE,
                Permission.COURSE_READ,
                Permission.COURSE_MANAGE,
                Permission.ACADEMIC_REVIEW));
        matrix.put(Role.LIBRARIAN, EnumSet.of(
                Permission.LIBRARY_READ,
                Permission.LIBRARY_BORROW,
                Permission.LIBRARY_MANAGE));
        matrix.put(Role.STORE_MANAGER, EnumSet.of(
                Permission.STORE_READ,
                Permission.STORE_PURCHASE,
                Permission.STORE_MANAGE));
    }

    public boolean isAllowed(Role role, Permission permission) {
        Set<Permission> permissions = matrix.get(role);
        return permissions != null && permission != null && permissions.contains(permission);
    }
}
