package cn.vcampus.client.view;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** 本机今日已读提醒的原子文件存储；临时文件与目标文件位于同一目录。 */
final class LibraryReminderFileStorage implements LibraryReminderState.Storage {

    /** 本机提醒状态文件。 */
    private final Path file;

    /** 绑定当前读者的提醒文件；创建时不读写磁盘。 */
    LibraryReminderFileStorage(Path file) {
        this.file = file;
    }

    /** 读取本机提醒状态；文件不存在时返回空状态。 */
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

    /** 通过同目录临时文件原子替换提醒状态，完成后清理临时文件。 */
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
