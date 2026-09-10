package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongPredicate;

/** Thread-safe repository used by tests and server demo mode. */
public final class InMemoryLibraryRepository implements LibraryRepository {
    private final Map<String, Book> books = new LinkedHashMap<String, Book>();
    private final List<BorrowRecord> records = new ArrayList<BorrowRecord>();
    // Kept in the shared repository, so separate service instances cannot duplicate a bill.
    private final Map<String, LibraryCompensation> compensations = new LinkedHashMap<String, LibraryCompensation>();

    public InMemoryLibraryRepository() { }

    public InMemoryLibraryRepository(List<Book> initialBooks) {
        if (initialBooks == null) throw new IllegalArgumentException("initialBooks must not be null");
        for (Book book : initialBooks) {
            if (!addBook(book)) throw new IllegalArgumentException("duplicate book id: " + book.getBookId());
        }
    }

    public static InMemoryLibraryRepository withDemoCatalog() {
        List<Book> catalog = new ArrayList<Book>();
        catalog.add(new Book("B001", "Java核心技术（卷I）", "Cay S. Horstmann",
                "9787115547392", "计算机", "机械工业出版社", 129.00d, 3, 3, "A-01"));
        catalog.add(new Book("B002", "算法导论", "Thomas H. Cormen",
                "9787111407010", "计算机", "机械工业出版社", 128.00d, 2, 2, "A-02"));
        catalog.add(new Book("B003", "红楼梦", "曹雪芹",
                "9787020002207", "文学", "人民文学出版社", 59.70d, 2, 2, "B-01"));
        catalog.add(new Book("B004", "三体", "刘慈欣",
                "9787536692930", "科幻", "重庆出版社", 39.00d, 4, 4, "B-02"));
        catalog.add(new Book("B005", "高等数学（第七版）", "同济大学数学系",
                "9787040396638", "教材", "高等教育出版社", 56.80d, 5, 5, "C-01"));
        catalog.add(new Book("B006", "深入理解计算机系统", "Randal E. Bryant",
                "9787111544937", "计算机", "机械工业出版社", 139.00d, 3, 3, "A-03"));
        catalog.add(new Book("B007", "设计模式", "Erich Gamma",
                "9787111210340", "计算机", "机械工业出版社", 79.00d, 2, 2, "A-04"));
        catalog.add(new Book("B008", "计算机网络：自顶向下方法", "James F. Kurose",
                "9787111599715", "计算机", "机械工业出版社", 89.00d, 3, 3, "A-05"));
        catalog.add(new Book("B009", "活着", "余华",
                "9787530215593", "文学", "北京十月文艺出版社", 35.00d, 4, 4, "B-03"));
        catalog.add(new Book("B010", "人类简史", "尤瓦尔·赫拉利",
                "9787508647357", "历史", "中信出版社", 68.00d, 2, 2, "D-01"));
        // B011–B050 的书号、出版社、价格均为演示元数据，不代表真实出版版本。
        catalog.add(new Book("B011", "平凡的世界", "路遥",
                "DEMO-B011", "文学", "演示出版社", 79.00d, 3, 3, "B-04"));
        catalog.add(new Book("B012", "围城", "钱锺书",
                "DEMO-B012", "文学", "演示出版社", 48.00d, 3, 3, "B-05"));
        catalog.add(new Book("B013", "骆驼祥子", "老舍",
                "DEMO-B013", "文学", "演示出版社", 32.00d, 3, 3, "B-06"));
        catalog.add(new Book("B014", "朝花夕拾", "鲁迅",
                "DEMO-B014", "文学", "演示出版社", 28.00d, 3, 3, "B-07"));
        catalog.add(new Book("B015", "边城", "沈从文",
                "DEMO-B015", "文学", "演示出版社", 30.00d, 3, 3, "B-08"));
        catalog.add(new Book("B016", "老人与海", "欧内斯特·海明威",
                "DEMO-B016", "文学", "演示出版社", 26.00d, 3, 3, "B-09"));
        catalog.add(new Book("B017", "百年孤独", "加西亚·马尔克斯",
                "DEMO-B017", "文学", "演示出版社", 68.00d, 3, 3, "B-10"));
        catalog.add(new Book("B018", "傲慢与偏见", "简·奥斯汀",
                "DEMO-B018", "文学", "演示出版社", 39.00d, 3, 3, "B-11"));
        catalog.add(new Book("B019", "流浪地球", "刘慈欣",
                "DEMO-B019", "科幻", "演示出版社", 45.00d, 3, 3, "B-12"));
        catalog.add(new Book("B020", "球状闪电", "刘慈欣",
                "DEMO-B020", "科幻", "演示出版社", 38.00d, 3, 3, "B-13"));
        catalog.add(new Book("B021", "银河帝国：基地", "艾萨克·阿西莫夫",
                "DEMO-B021", "科幻", "演示出版社", 52.00d, 3, 3, "B-14"));
        catalog.add(new Book("B022", "海底两万里", "儒勒·凡尔纳",
                "DEMO-B022", "科幻", "演示出版社", 35.00d, 3, 3, "B-15"));
        catalog.add(new Book("B023", "时间机器", "H. G. 威尔斯",
                "DEMO-B023", "科幻", "演示出版社", 29.00d, 3, 3, "B-16"));
        catalog.add(new Book("B024", "数据结构（C语言版）", "严蔚敏",
                "DEMO-B024", "计算机", "演示出版社", 39.00d, 3, 3, "A-06"));
        catalog.add(new Book("B025", "代码整洁之道", "罗伯特·C. 马丁",
                "DEMO-B025", "计算机", "演示出版社", 79.00d, 3, 3, "A-07"));
        catalog.add(new Book("B026", "重构：改善既有代码的设计", "马丁·福勒",
                "DEMO-B026", "计算机", "演示出版社", 88.00d, 3, 3, "A-08"));
        catalog.add(new Book("B027", "计算机程序的构造和解释", "哈罗德·阿贝尔森",
                "DEMO-B027", "计算机", "演示出版社", 99.00d, 3, 3, "A-09"));
        catalog.add(new Book("B028", "数据库系统概念", "亚伯拉罕·西尔伯沙茨",
                "DEMO-B028", "计算机", "演示出版社", 109.00d, 3, 3, "A-10"));
        catalog.add(new Book("B029", "万历十五年", "黄仁宇",
                "DEMO-B029", "历史", "演示出版社", 49.00d, 3, 3, "D-02"));
        catalog.add(new Book("B030", "中国历代政治得失", "钱穆",
                "DEMO-B030", "历史", "演示出版社", 32.00d, 3, 3, "D-03"));
        catalog.add(new Book("B031", "全球通史", "L. S. 斯塔夫里阿诺斯",
                "DEMO-B031", "历史", "演示出版社", 96.00d, 3, 3, "D-04"));
        catalog.add(new Book("B032", "史记", "司马迁",
                "DEMO-B032", "历史", "演示出版社", 85.00d, 3, 3, "D-05"));
        catalog.add(new Book("B033", "中国近代史", "蒋廷黻",
                "DEMO-B033", "历史", "演示出版社", 39.00d, 3, 3, "D-06"));
        catalog.add(new Book("B034", "线性代数", "同济大学数学系",
                "DEMO-B034", "教材", "演示出版社", 39.00d, 3, 3, "C-02"));
        catalog.add(new Book("B035", "概率论与数理统计", "盛骤",
                "DEMO-B035", "教材", "演示出版社", 45.00d, 3, 3, "C-03"));
        catalog.add(new Book("B036", "大学物理", "程守洙",
                "DEMO-B036", "教材", "演示出版社", 62.00d, 3, 3, "C-04"));
        catalog.add(new Book("B037", "离散数学", "左孝凌",
                "DEMO-B037", "教材", "演示出版社", 49.00d, 3, 3, "C-05"));
        catalog.add(new Book("B038", "苏菲的世界", "乔斯坦·贾德",
                "DEMO-B038", "哲学", "演示出版社", 58.00d, 3, 3, "E-01"));
        catalog.add(new Book("B039", "理想国", "柏拉图",
                "DEMO-B039", "哲学", "演示出版社", 45.00d, 3, 3, "E-02"));
        catalog.add(new Book("B040", "论语", "孔子及其弟子",
                "DEMO-B040", "哲学", "演示出版社", 29.00d, 3, 3, "E-03"));
        catalog.add(new Book("B041", "道德经", "老子",
                "DEMO-B041", "哲学", "演示出版社", 26.00d, 3, 3, "E-04"));
        catalog.add(new Book("B042", "艺术的故事", "E. H. 贡布里希",
                "DEMO-B042", "艺术", "演示出版社", 168.00d, 3, 3, "F-01"));
        catalog.add(new Book("B043", "美的历程", "李泽厚",
                "DEMO-B043", "艺术", "演示出版社", 59.00d, 3, 3, "F-02"));
        catalog.add(new Book("B044", "谈美", "朱光潜",
                "DEMO-B044", "艺术", "演示出版社", 32.00d, 3, 3, "F-03"));
        catalog.add(new Book("B045", "经济学原理", "N. 格里高利·曼昆",
                "DEMO-B045", "经济", "演示出版社", 128.00d, 3, 3, "G-01"));
        catalog.add(new Book("B046", "国富论", "亚当·斯密",
                "DEMO-B046", "经济", "演示出版社", 79.00d, 3, 3, "G-02"));
        catalog.add(new Book("B047", "牛奶可乐经济学", "罗伯特·弗兰克",
                "DEMO-B047", "经济", "演示出版社", 42.00d, 3, 3, "G-03"));
        catalog.add(new Book("B048", "时间简史", "史蒂芬·霍金",
                "DEMO-B048", "自然科学", "演示出版社", 45.00d, 3, 3, "H-01"));
        catalog.add(new Book("B049", "从一到无穷大", "乔治·伽莫夫",
                "DEMO-B049", "自然科学", "演示出版社", 49.00d, 3, 3, "H-02"));
        catalog.add(new Book("B050", "物种起源", "查尔斯·达尔文",
                "DEMO-B050", "自然科学", "演示出版社", 58.00d, 3, 3, "H-03"));
        return new InMemoryLibraryRepository(catalog);
    }

    /** Demo catalog plus dynamic loan samples for reminder and circulation acceptance checks. */
    public static InMemoryLibraryRepository withDemoData() {
        InMemoryLibraryRepository repository = withDemoCatalog();
        LocalDate today = LocalDate.now();
        repository.borrowBatch("demo_student", Collections.singletonList("B001"),
                today.minusDays(28), today.plusDays(2));
        repository.borrowBatch("demo_student", Collections.singletonList("B004"),
                today.minusDays(23), today.plusDays(7));
        ServiceResult<List<BorrowRecord>> returned = repository.borrowBatch(
                "demo_student", Collections.singletonList("B003"),
                today.minusDays(50), today.minusDays(20));
        repository.returnBook("demo_student", returned.getData().get(0).getRecordId(),
                today.minusDays(35));
        repository.borrowBatch("demo_teacher", Collections.singletonList("B002"),
                today.minusDays(32), today.minusDays(2));
        return repository;
    }

    @Override
    public synchronized List<Book> search(String keyword, String category) {
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

    @Override
    public synchronized Book findBook(String bookId) {
        return bookId == null ? null : books.get(bookId.trim());
    }

    @Override
    public synchronized boolean addBook(Book book) {
        if (book == null || books.containsKey(book.getBookId())) return false;
        books.put(book.getBookId(), book);
        return true;
    }

    @Override
    public synchronized ServiceResult<Book> restock(String bookId, int copies) {
        if (bookId == null || bookId.trim().isEmpty() || copies <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId and positive copies are required");
        }
        Book current = books.get(bookId.trim());
        if (current == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        final Book updated;
        try {
            updated = current.withAdditionalCopies(copies);
        } catch (IllegalArgumentException invalidStock) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidStock.getMessage());
        }
        books.put(current.getBookId(), updated);
        return ServiceResult.ok(updated);
    }

    @Override
    public synchronized ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bookId);
            if (book.getAvailableCopies() <= 0) {
                return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bookId);
            }
            if (findActive(userId, bookId) != null) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed: " + bookId);
            }
        }
        String orderId = id("BO");
        List<BorrowRecord> created = new ArrayList<BorrowRecord>();
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            books.put(bookId, book.withAvailableCopies(book.getAvailableCopies() - 1));
            BorrowRecord record = new BorrowRecord(orderId, id("BR"), userId, bookId,
                    borrowDate, dueDate, null, BorrowStatus.BORROWED);
            records.add(record);
            created.add(record);
        }
        return ServiceResult.ok(created);
    }

    @Override
    public synchronized ServiceResult<BorrowRecord> returnBook(
            String userId, String recordId, LocalDate returnDate) {
        for (int index = 0; index < records.size(); index++) {
            BorrowRecord record = records.get(index);
            if (!record.getRecordId().equals(recordId) || !record.getUserId().equals(userId)
                    || record.getStatus() != BorrowStatus.BORROWED) continue;
            Book book = books.get(record.getBookId());
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            BorrowRecord returned = record.returned(returnDate);
            records.set(index, returned);
            books.put(book.getBookId(), book.withAvailableCopies(book.getAvailableCopies() + 1));
            return ServiceResult.ok(returned);
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "active borrowing record not found");
    }

    @Override
    public synchronized List<BorrowRecord> findBorrowHistory(String userId) {
        List<BorrowRecord> history = new ArrayList<BorrowRecord>();
        for (BorrowRecord record : records) {
            if (record.getUserId().equals(userId)) history.add(record);
        }
        return history;
    }

    @Override
    public synchronized List<BorrowRecord> findAllBorrowHistory() {
        return new ArrayList<BorrowRecord>(records);
    }

    public synchronized ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        if (blank(operatorId) || blank(recordId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "operatorId and recordId are required");
        }
        for (LibraryCompensation bill : compensations.values()) {
            if (bill.getRecordId().equals(recordId.trim())) return ServiceResult.ok(bill);
        }
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            if (!record.getRecordId().equals(recordId.trim())) continue;
            if (record.getStatus() != BorrowStatus.BORROWED) {
                return ServiceResult.failure(StatusCode.CONFLICT, "only an active loan can be declared lost");
            }
            Book book = books.get(record.getBookId());
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            if (book.getTotalCopies() <= book.getAvailableCopies()) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book inventory is inconsistent");
            }
            final long cents;
            try {
                cents = LibraryCompensation.originalPriceCents(book.getPrice());
            } catch (IllegalArgumentException invalidPrice) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidPrice.getMessage());
            }
            LibraryCompensation bill = new LibraryCompensation(UUID.randomUUID().toString(), record.getRecordId(),
                    record.getUserId(), book.getBookId(), book.getTitle(), cents, CompensationStatus.PENDING,
                    operatorId, LocalDateTime.now(), null);
            Book remaining = new Book(book.getBookId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                    book.getCategory(), book.getPublisher(), book.getPrice(), book.getTotalCopies() - 1,
                    book.getAvailableCopies(), book.getLocation());
            BorrowRecord lost = record.lost();
            books.put(book.getBookId(), remaining);
            records.set(i, lost);
            compensations.put(bill.getCompensationId(), bill);
            return ServiceResult.ok(bill);
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "borrowing record not found");
    }

    /** Caller holds the shared wallet lock after the library lock; debit is atomic or throws without mutation. */
    public synchronized ServiceResult<LibraryCompensation> payCompensation(String userId, String compensationId,
            LongPredicate debit) {
        if (blank(userId) || blank(compensationId) || debit == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId and compensationId are required");
        }
        LibraryCompensation bill = compensations.get(compensationId.trim());
        if (bill == null || !bill.getUserId().equals(userId.trim())) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "compensation not found");
        }
        if (bill.getStatus() == CompensationStatus.PAID) return ServiceResult.ok(bill);
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            if (!record.getRecordId().equals(bill.getRecordId())) continue;
            if (record.getStatus() != BorrowStatus.LOST || !record.getUserId().equals(userId.trim())) {
                return ServiceResult.failure(StatusCode.CONFLICT, "lost record state changed");
            }
            // Validate every new immutable value before the irreversible in-memory wallet mutation.
            BorrowRecord settled = record.compensated();
            LibraryCompensation paid = bill.paid(LocalDateTime.now());
            if (bill.getAmountCents() > 0 && !debit.test(bill.getAmountCents())) {
                return ServiceResult.failure(StatusCode.PAYMENT_REQUIRED, "insufficient campus wallet balance");
            }
            records.set(i, settled);
            compensations.put(bill.getCompensationId(), paid);
            return ServiceResult.ok(paid);
        }
        return ServiceResult.failure(StatusCode.CONFLICT, "lost borrowing record missing");
    }

    public synchronized List<LibraryCompensation> findCompensations(String userId) {
        List<LibraryCompensation> result = new ArrayList<LibraryCompensation>();
        for (LibraryCompensation bill : compensations.values()) {
            if (userId == null || bill.getUserId().equals(userId.trim())) result.add(bill);
        }
        return result;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private BorrowRecord findActive(String userId, String bookId) {
        for (BorrowRecord record : records) {
            if (record.getStatus() == BorrowStatus.BORROWED
                    && record.getUserId().equals(userId) && record.getBookId().equals(bookId)) return record;
        }
        return null;
    }

    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString(); }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static boolean contains(String value, String key) { return normalize(value).contains(key); }
}
