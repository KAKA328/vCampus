package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 防止独立 Access 测试库与内存演示馆藏在数量或元数据上出现差异。 */
class DemoCatalogTest {
    private static final String SQL_STRING = "'((?:[^']|'')*)'";
    private static final Pattern BOOK_INSERT = Pattern.compile(
            "INSERT\\s+INTO\\s+tblBook\\s*\\([^)]*\\)\\s*VALUES\\s*\\(\\s*"
            + SQL_STRING + "\\s*,\\s*" + SQL_STRING + "\\s*,\\s*" + SQL_STRING
            + "\\s*,\\s*" + SQL_STRING + "\\s*,\\s*" + SQL_STRING + "\\s*,\\s*"
            + SQL_STRING + "\\s*,\\s*(\\d+(?:\\.\\d+)?)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)"
            + "\\s*,\\s*" + SQL_STRING + "\\s*\\)\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void catalogHasFiftyUniqueTitlesAndNineCategories() {
        List<Book> books = InMemoryLibraryRepository.withDemoCatalog().search("", null);
        assertEquals(50, books.size());
        Set<String> identifiers = new HashSet<String>();
        Set<String> titles = new HashSet<String>();
        Set<String> isbns = new HashSet<String>();
        Set<String> categories = new TreeSet<String>();
        int totalCopies = 0;
        int availableCopies = 0;
        for (int index = 0; index < books.size(); index++) {
            Book book = books.get(index);
            assertEquals(String.format(Locale.ROOT, "B%03d", index + 1), book.getBookId());
            assertTrue(identifiers.add(book.getBookId()), "重复图书编号");
            assertTrue(titles.add(book.getTitle()), "重复书名");
            assertTrue(isbns.add(book.getIsbn()), "重复 ISBN / 演示编码");
            assertTrue(book.getTotalCopies() > 0);
            assertEquals(book.getTotalCopies(), book.getAvailableCopies());
            assertTrue(book.getPrice() > 0.0d);
            if (index >= 10) {
                assertEquals("DEMO-" + book.getBookId(), book.getIsbn());
                assertEquals("演示出版社", book.getPublisher());
            }
            categories.add(book.getCategory());
            totalCopies += book.getTotalCopies();
            availableCopies += book.getAvailableCopies();
        }
        assertEquals(new TreeSet<String>(Arrays.asList("文学", "科幻", "计算机", "历史",
                "教材", "哲学", "艺术", "经济", "自然科学")), categories);
        assertEquals(150, totalCopies);
        assertEquals(150, availableCopies);
    }

    @Test
    void interactiveCatalogPreservesLoansAndHas147AvailableCopies() {
        InMemoryLibraryRepository demo = InMemoryLibraryRepository.withDemoData();
        List<Book> books = demo.search("", null);
        List<BorrowRecord> records = demo.findAllBorrowHistory();
        assertEquals(50, books.size());
        assertEquals(4, records.size());
        assertEquals(3L, records.stream().filter(record -> !record.isReturned()).count());
        assertEquals(3, demo.findBorrowHistory("demo_student").size());
        assertEquals(1, demo.findBorrowHistory("demo_teacher").size());
        assertEquals(2, demo.findBook("B001").getAvailableCopies());
        assertEquals(1, demo.findBook("B002").getAvailableCopies());
        assertEquals(2, demo.findBook("B003").getAvailableCopies());
        assertEquals(3, demo.findBook("B004").getAvailableCopies());
        int totalCopies = 0;
        int availableCopies = 0;
        for (Book book : books) {
            long activeLoans = records.stream().filter(record ->
                    !record.isReturned() && record.getBookId().equals(book.getBookId())).count();
            assertEquals((long) book.getTotalCopies() - book.getAvailableCopies(), activeLoans,
                    "借阅与库存不一致：" + book.getBookId());
            totalCopies += book.getTotalCopies();
            availableCopies += book.getAvailableCopies();
        }
        assertEquals(150, totalCopies);
        assertEquals(147, availableCopies);
    }

    @Test
    void accessSeedMatchesInteractiveCatalogFieldForField() throws IOException {
        String sql = new String(Files.readAllBytes(findSeed()), StandardCharsets.UTF_8);
        Matcher matcher = BOOK_INSERT.matcher(sql);
        Map<String, Book> seeded = new LinkedHashMap<String, Book>();
        while (matcher.find()) {
            Book book = new Book(unquote(matcher.group(1)), unquote(matcher.group(2)),
                    unquote(matcher.group(3)), unquote(matcher.group(4)),
                    unquote(matcher.group(5)), unquote(matcher.group(6)),
                    Double.parseDouble(matcher.group(7)), Integer.parseInt(matcher.group(8)),
                    Integer.parseInt(matcher.group(9)), unquote(matcher.group(10)));
            assertTrue(!seeded.containsKey(book.getBookId()), "SQL 重复图书编号");
            seeded.put(book.getBookId(), book);
        }
        assertEquals(50, seeded.size(), "seed.sql 必须包含 50 条完整馆藏数据");
        List<Book> interactive = InMemoryLibraryRepository.withDemoData().search("", null);
        assertEquals(interactive.size(), seeded.size());
        for (Book book : interactive) {
            assertNotNull(seeded.get(book.getBookId()), book.getBookId());
            assertEquals(book, seeded.get(book.getBookId()), "SQL 与内存数据不同：" + book.getBookId());
        }
    }

    private static String unquote(String value) {
        return value.replace("''", "'");
    }

    private static Path findSeed() throws IOException {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            Path seed = directory.resolve("database").resolve("seed.sql");
            if (Files.isRegularFile(seed)) return seed;
            directory = directory.getParent();
        }
        throw new IOException("找不到项目 database/seed.sql，无法验证演示数据一致性");
    }
}

