package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductImportFileReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsChineseHeadersAndSkipsBlankLines() throws IOException {
        Path file = tempDir.resolve("products.csv");
        Files.write(file, String.join("\n",
                "名称,价格,库存,类别,说明",
                "演示笔记本,6.50,120,文具,横线内页",
                "",
                "演示马克杯,15,40,生活,陶瓷").getBytes(StandardCharsets.UTF_8));

        List<ProductImportRow> rows = new ProductImportFileReader().read(file);

        assertEquals(2, rows.size());
        assertEquals("演示笔记本", rows.get(0).getName());
        assertEquals(6.5d, rows.get(0).getPrice());
        assertEquals(120, rows.get(0).getStock());
        assertEquals("文具", rows.get(0).getCategory());
        assertEquals("横线内页", rows.get(0).getDescription());
        assertEquals("生活", rows.get(1).getCategory());
    }

    @Test
    void missingRequiredColumnFails() throws IOException {
        Path file = tempDir.resolve("bad.csv");
        Files.write(file, String.join("\n", "名称,价格", "演示,1").getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ProductImportFileReader().read(file));

        assertTrue(e.getMessage().contains("库存"));
    }

    @Test
    void badPriceFailsWithLineNumber() throws IOException {
        Path file = tempDir.resolve("price.csv");
        Files.write(file, String.join("\n", "名称,价格,库存", "演示,abc,1").getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ProductImportFileReader().read(file));

        assertTrue(e.getMessage().contains("第 2 行"));
    }
}
