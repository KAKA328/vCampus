package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.*;
import java.util.stream.Collectors;

public final class InMemoryMajorDirectoryService implements MajorDirectoryService {
    private final List<MajorDirectoryEntry> entries;
    public InMemoryMajorDirectoryService(List<MajorDirectoryEntry> entries) {
        this.entries = new ArrayList<>(Objects.requireNonNull(entries));
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (MajorDirectoryEntry entry : this.entries) {
            if (!ids.add(entry.getMajorId()) || !names.add(entry.getMajorName())) {
                throw new IllegalArgumentException("专业编号和名称必须唯一");
            }
        }
    }
    public static InMemoryMajorDirectoryService demo() {
        return new InMemoryMajorDirectoryService(Arrays.asList(
                new MajorDirectoryEntry("CS", "计算机科学与技术", "计算机科学与工程学院", true),
                new MajorDirectoryEntry("SE", "软件工程", "计算机科学与工程学院", true),
                new MajorDirectoryEntry("CN", "汉语言文学", "通识教育学院", true)));
    }
    @Override public ServiceResult<List<MajorDirectoryEntry>> listActive() {
        return ServiceResult.ok(Collections.unmodifiableList(entries.stream()
                .filter(MajorDirectoryEntry::isActive).sorted(MajorDirectoryEntry.ORDER).collect(Collectors.toList())));
    }
}
