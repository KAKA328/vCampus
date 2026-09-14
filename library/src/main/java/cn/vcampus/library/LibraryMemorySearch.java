package cn.vcampus.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 共享锁内的内存馆藏检索；与实体册及借还状态维护分离。 */
final class LibraryMemorySearch {
    /** 规范化关键词与分类后检索馆藏条目。 */
    static List<Book> search(Map<String, Book> books, String keyword, String category) {
        String key = normalize(keyword);
        String categoryKey = category == null ? null : normalize(category);
        List<Book> found = new ArrayList<Book>();
        for (Book book : books.values()) {
            if (categoryKey != null && !normalize(book.getCategory()).equals(categoryKey)) continue;
            if (key.isEmpty() || contains(book.getBookId(), key) || contains(book.getTitle(), key)
                    || contains(book.getAuthor(), key) || contains(book.getIsbn(), key)
                    || contains(book.getCategory(), key)) found.add(book);
        }
        return found;
    }
    /** 用稳定区域规则规范化检索文字。 */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    /** 判断某一资料字段是否包含关键词。 */
    private static boolean contains(String value, String key) { return normalize(value).contains(key); }
}
