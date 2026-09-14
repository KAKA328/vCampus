package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 保存本机读者的“今日已读”状态；不修改借阅记录或归还状态。
 * 服务器和用户独立存储；同一提醒次日恢复，新增或变化的提醒立即显示。
 * 使用普通本地文件，不访问可能因注册表权限问题阻塞的 Windows 偏好设置 API。
 */
final class LibraryReminderState {
    /** 按服务器和用户隔离的进程内提醒缓存。 */
    private static final Map<String, Map<String, String>> MEMORY =
            new HashMap<String, Map<String, String>>();

    /** 提醒状态存储；不可用时退化为进程内缓存。 */
    private Storage storage;
    /** 是否已尝试加载本机提醒文件。 */
    private boolean storageLoaded;
    /** 当前用户今日已读提醒的内存状态。 */
    private final Map<String, String> memory;

    /** 绑定已按读者隔离的缓存和可选文件存储，延迟加载持久化内容。 */
    private LibraryReminderState(Storage storage, Map<String, String> memory) {
        this.storage = storage;
        this.memory = memory;
    }

    /** 创建完全隔离、不访问本机配置的内存状态，供页面测试注入。 */
    static LibraryReminderState memoryOnly() {
        return new LibraryReminderState(null, new HashMap<String, String>());
    }

    /** 为当前服务器和读者创建入口；本地文件不可用时保留进程内状态。 */
    static LibraryReminderState forReader(String host, int port, String userId) {
        Path root = null;
        try {
            String homeDirectory = System.getProperty("user.home");
            if (homeDirectory != null && !homeDirectory.trim().isEmpty()) {
                root = Paths.get(homeDirectory, ".vcampus", "library-reminders");
            }
        } catch (SecurityException | InvalidPathException unavailable) {
            // 读取本机配置位置失败不能影响借还书。
        }
        return forReader(host, port, userId, root, MEMORY);
    }

    /** 测试入口：将文件和内存后备指向独立位置，避免污染真实用户配置。 */
    static LibraryReminderState forReader(String host, int port, String userId,
            Path root, Map<String, Map<String, String>> memoryRoot) {
        if (host == null || host.trim().isEmpty() || userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("host and userId must not be blank");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port");
        if (memoryRoot == null) throw new IllegalArgumentException("memoryRoot must not be null");
        String scope = digest(host.trim().toLowerCase(Locale.ROOT),
                Integer.toString(port), userId.trim());
        Map<String, String> memory;
        synchronized (memoryRoot) {
            memory = memoryRoot.get(scope);
            if (memory == null) {
                memory = new HashMap<String, String>();
                memoryRoot.put(scope, memory);
            }
        }
        return new LibraryReminderState(root == null ? null
                : new LibraryReminderFileStorage(root.resolve(scope + ".properties")), memory);
    }

    /** 故障测试入口：允许注入失败存储及可跨实例共享的内存后备。 */
    static LibraryReminderState withStorage(Storage storage, Map<String, String> memory) {
        if (memory == null) throw new IllegalArgumentException("memory must not be null");
        return new LibraryReminderState(storage, memory);
    }

    /** 返回尚未标为今日已读的临期或逾期记录，不修改原列表。 */
    List<BorrowRecord> unread(List<BorrowRecord> records, LocalDate today) {
        requireArguments(records, today);
        List<BorrowRecord> result = new ArrayList<BorrowRecord>();
        synchronized (memory) {
            loadOnce();
            String day = today.toString();
            removeOldMemory(day);
            for (BorrowRecord record : records) {
                String signature = reminderSignature(record, today);
                if (signature != null && !day.equals(memory.get(signature))) result.add(record);
            }
        }
        return result;
    }

    /** 将有效提醒标为今日已读；已还或非临期记录不会被记录。 */
    void acknowledge(List<BorrowRecord> records, LocalDate today) {
        requireArguments(records, today);
        String day = today.toString();
        synchronized (memory) {
            loadOnce();
            removeOldMemory(day);
            boolean hasReminder = false;
            for (BorrowRecord record : records) {
                String signature = reminderSignature(record, today);
                if (signature != null) {
                    memory.put(signature, day);
                    hasReminder = true;
                }
            }
            if (storage == null || !hasReminder) return;
            try {
                storage.save(new HashMap<String, String>(memory));
            } catch (IOException | SecurityException unavailable) {
                storage = null;
                // 先更新内存，因此写入失败时今日已读在本进程内仍然有效。
            }
        }
    }

    /** 最多尝试一次读取磁盘状态，不覆盖本进程刚更新的值。 */
    private void loadOnce() {
        if (storageLoaded || storage == null) return;
        storageLoaded = true;
        try {
            for (Map.Entry<String, String> entry : storage.load().entrySet()) {
                // 新实例不得用较旧的磁盘内容覆盖本进程刚写入的已读日期。
                if (!memory.containsKey(entry.getKey())) memory.put(entry.getKey(), entry.getValue());
            }
        } catch (IOException | SecurityException unavailable) {
            storage = null;
        }
    }

    /** 移除非当天的已读标记，使新一天重新提醒。 */
    private void removeOldMemory(String day) {
        Iterator<Map.Entry<String, String>> entries = memory.entrySet().iterator();
        while (entries.hasNext()) {
            if (!day.equals(entries.next().getValue())) entries.remove();
        }
    }

    /** 结合用户、借阅、期限和提醒阶段生成稳定标识。 */
    private static String reminderSignature(BorrowRecord record, LocalDate today) {
        if (record == null || record.getStatus() != BorrowStatus.BORROWED
                || record.getDueDate().isAfter(today.plusDays(LibraryDueReminder.WARNING_DAYS))) {
            return null;
        }
        String phase = record.getDueDate().isBefore(today) ? "OVERDUE" : "DUE_SOON";
        return digest(record.getUserId(), record.getOrderId(), record.getRecordId(),
                record.getBookId(), record.getDueDate().toString(), phase);
    }

    /** 拒绝空记录集合或空日期。 */
    private static void requireArguments(List<BorrowRecord> records, LocalDate today) {
        if (records == null || today == null) {
            throw new IllegalArgumentException("records and today must not be null");
        }
    }

    /** 使用 SHA-256 生成本机状态键，不把用户编号直接写入文件名。 */
    private static String digest(String... parts) {
        StringBuilder value = new StringBuilder();
        for (String part : parts) value.append(part.length()).append(':').append(part);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte element : bytes) {
                hex.append(Character.forDigit((element >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(element & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    /** 提醒文件的最小存储边界，允许测试读写异常而不访问系统配置。 */
    interface Storage {
        /** 读取本机提醒状态；文件不存在时返回空状态。 */
        Map<String, String> load() throws IOException;
        /** 通过同目录临时文件原子替换提醒状态，完成后清理临时文件。 */
        void save(Map<String, String> values) throws IOException;
    }

}
