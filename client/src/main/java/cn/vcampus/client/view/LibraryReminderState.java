package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 保存本机读者的“今日已读”状态；不修改借阅记录或归还状态。
 * 服务器和用户独立存储；同一提醒次日恢复，新增或变化的提醒立即显示。
 * 使用普通本地文件，不访问可能因注册表权限问题阻塞的 Windows 偏好设置 API。
 */
final class LibraryReminderState {
    private static final Map<String, Map<String, String>> MEMORY =
            new HashMap<String, Map<String, String>>();

    private Storage storage;
    private boolean storageLoaded;
    private final Map<String, String> memory;

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
                : new FileStorage(root.resolve(scope + ".properties")), memory);
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

    private void removeOldMemory(String day) {
        Iterator<Map.Entry<String, String>> entries = memory.entrySet().iterator();
        while (entries.hasNext()) {
            if (!day.equals(entries.next().getValue())) entries.remove();
        }
    }

    private static String reminderSignature(BorrowRecord record, LocalDate today) {
        if (record == null || record.getStatus() != BorrowStatus.BORROWED
                || record.getDueDate().isAfter(today.plusDays(LibraryDueReminder.WARNING_DAYS))) {
            return null;
        }
        String phase = record.getDueDate().isBefore(today) ? "OVERDUE" : "DUE_SOON";
        return digest(record.getUserId(), record.getOrderId(), record.getRecordId(),
                record.getBookId(), record.getDueDate().toString(), phase);
    }

    private static void requireArguments(List<BorrowRecord> records, LocalDate today) {
        if (records == null || today == null) {
            throw new IllegalArgumentException("records and today must not be null");
        }
    }

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
        Map<String, String> load() throws IOException;
        void save(Map<String, String> values) throws IOException;
    }

    private static final class FileStorage implements Storage {
        private final Path file;

        FileStorage(Path file) {
            this.file = file;
        }

        @Override public Map<String, String> load() throws IOException {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (NoSuchFileException firstUse) {
                return new HashMap<String, String>();
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Invalid reminder file", malformed);
            }
            Map<String, String> values = new HashMap<String, String>();
            for (String key : properties.stringPropertyNames()) values.put(key, properties.getProperty(key));
            return values;
        }

        @Override public void save(Map<String, String> values) throws IOException {
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".reminders-", ".tmp");
            try {
                Properties properties = new Properties();
                properties.putAll(values);
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    properties.store(output, "vCampus library reminder acknowledgements");
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
