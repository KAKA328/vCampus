package cn.vcampus.student;

import java.io.Serializable;
import java.util.Comparator;

/** Stable catalogue identity; training plans still persist majorName during the transition. */
public final class MajorDirectoryEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final Comparator<MajorDirectoryEntry> ORDER = Comparator
            .comparing(MajorDirectoryEntry::getDepartmentName)
            .thenComparing(MajorDirectoryEntry::getMajorName)
            .thenComparing(MajorDirectoryEntry::getMajorId);
    private final String majorId;
    private final String majorName;
    private final String departmentName;
    private final boolean active;

    public MajorDirectoryEntry(String majorId, String majorName, String departmentName, boolean active) {
        this.majorId = required(majorId, 32);
        this.majorName = required(majorName, 64);
        this.departmentName = required(departmentName, 64);
        this.active = active;
    }
    private static String required(String value, int length) {
        if (value == null || value.trim().isEmpty() || value.length() > length) {
            throw new IllegalArgumentException("专业目录字段为空或超长");
        }
        return value.trim();
    }
    public String getMajorId() { return majorId; }
    public String getMajorName() { return majorName; }
    public String getDepartmentName() { return departmentName; }
    public boolean isActive() { return active; }
    @Override public String toString() { return majorId + " - " + majorName; }
}
