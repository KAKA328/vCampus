package cn.vcampus.server;

import com.healthmarketscience.jackcess.*;
import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.store.BankAccount;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

public final class MigrationEvidence {
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static <T> T ok(ServiceResult<T> result) {
        require(result.getStatus() == StatusCode.OK, result.getMessage());
        return result.getData();
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("prepare")) prepare(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
        else if (args[0].equals("verify")) verify(Paths.get(args[1]), Paths.get(args[2]));
        else throw new IllegalArgumentException("prepare original NEW-source compensation.sql / verify source upgraded");
    }
    private static void prepare(Path original, Path source, Path compensationSql) throws Exception {
        require(!Files.exists(source), "prepared source must be new");
        String originalHash = sha(Files.readAllBytes(original));
        Files.createDirectories(source.toAbsolutePath().getParent());
        Files.copy(original, source);
        require(originalHash.equals(sha(Files.readAllBytes(source))), "copy bytes differ");
        System.out.println("ORIGINAL_SHA256=" + originalHash);
        System.out.println("EXISTING_BACKUP_COPY_BYTES_IDENTICAL=true");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + source.toAbsolutePath()
                + ";immediatelyReleaseResources=true")) {
            require(!LibraryCopySchema.available(connection), "source should be legacy, not V4");
            boolean exists;
            try (ResultSet tables = connection.getMetaData().getTables(null, null, "tblLibraryCompensation", null)) {
                exists = tables.next();
            }
            if (!exists) {
                StringBuilder ddl = new StringBuilder();
                for (String line : Files.readAllLines(compensationSql, StandardCharsets.UTF_8))
                    if (!line.trim().startsWith("--")) ddl.append(line).append('\n');
                try (Statement statement = connection.createStatement()) {
                    for (String sql : ddl.toString().split(";")) if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                System.out.println("PREPARATION_ONLY=added missing prerequisite 016 on COPY");
            }
        }
        AccessLibraryRepository repository = new AccessLibraryRepository(source);
        DefaultLibraryService library = new DefaultLibraryService(repository);
        AccessWalletRepository wallet = new AccessWalletRepository(source);
        require(wallet.save(new BankAccount("demo_student", 10000)), "test balance seed failed");
        AccessLibraryCompensationService losses = new AccessLibraryCompensationService(source, repository, wallet);
        for (String book : new String[] {"B049", "B050"}) {
            BorrowRecord loan = ok(library.borrow("demo_student", book)).get(0);
            LibraryCompensation bill = ok(losses.declareLoss("demo_librarian", loan.getRecordId()));
            if (book.equals("B050")) ok(losses.pay("demo_student", bill.getCompensationId()));
            System.out.println("PREPARED_CASE=" + book + " amountCents=" + bill.getAmountCents()
                    + " state=" + (book.equals("B050") ? "PAID" : "PENDING"));
        }
        require(originalHash.equals(sha(Files.readAllBytes(original))), "original was changed");
        System.out.println("ORIGINAL_UNCHANGED_AFTER_PREPARATION=true");
        System.out.println("PREPARED_SOURCE_SHA256=" + sha(Files.readAllBytes(source)));
        try (Database db = new DatabaseBuilder(source.toFile()).setReadOnly(true).open()) { metrics("BEFORE", db); }
    }
    private static void verify(Path source, Path destination) throws Exception {
        String sourceHash = sha(Files.readAllBytes(source));
        try (Database before = new DatabaseBuilder(source.toFile()).setReadOnly(true).open();
                Database after = new DatabaseBuilder(destination.toFile()).setReadOnly(true).open()) {
            for (String name : new TreeSet<String>(before.getTableNames())) {
                Table old = before.getTable(name), fresh = after.getTable(name);
                require(fresh != null, "missing old table " + name);
                List<String> a = rows(old), b = rows(fresh);
                require(a.equals(b), "existing table contents changed: " + name);
                System.out.println("TABLE_IDENTICAL " + name + " rows=" + a.size()
                        + " canonical_sha256=" + sha(a.toString().getBytes(StandardCharsets.UTF_8)));
            }
            metrics("BEFORE", before);
            metrics("AFTER", after);
            Table copies = after.getTable("tblBookCopy");
            Map<String, Integer> statuses = states(copies, "status");
            long total = sum(before.getTable("tblBook"), "total_copies");
            long available = sum(before.getTable("tblBook"), "available_copies");
            Map<String, Integer> loans = states(before.getTable("tblBorrowRecord"), "status");
            long lost = loans.getOrDefault("LOST", 0) + loans.getOrDefault("COMPENSATED", 0);
            require(copies.getRowCount() == total + lost, "physical copies mismatch");
            require(statuses.getOrDefault("AVAILABLE", 0) == available, "available mismatch");
            require(statuses.getOrDefault("BORROWED", 0).equals(loans.getOrDefault("BORROWED", 0)), "active mismatch");
            require(statuses.getOrDefault("LOST", 0) == lost, "lost physical copies mismatch");
            System.out.println("COPY_STATES=" + statuses);
            System.out.println("ALL_EXISTING_TABLE_ROWS_IDENTICAL=true");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + destination.toAbsolutePath()
                + ";immediatelyReleaseResources=true")) { LibraryCopyIntegrity.verify(connection); }
        require(sourceHash.equals(sha(Files.readAllBytes(source))), "source changed during verification");
        System.out.println("COPY_INTEGRITY_VERIFIED=true");
        System.out.println("SOURCE_SHA256=" + sourceHash);
        System.out.println("SOURCE_UNCHANGED=true");
        System.out.println("MIGRATION_EVIDENCE=PASS");
    }
    private static List<String> rows(Table table) {
        List<String> rows = new ArrayList<String>();
        for (Row row : table) {
            StringBuilder value = new StringBuilder();
            for (String key : new TreeSet<String>(row.keySet())) {
                Object item = row.get(key);
                String text = item == null ? "<NULL>" : item instanceof java.util.Date
                        ? Long.toString(((java.util.Date) item).getTime()) : item instanceof byte[]
                        ? Base64.getEncoder().encodeToString((byte[]) item) : item.toString();
                value.append(key).append(':').append(text.length()).append(':').append(text).append('|');
            }
            rows.add(value.toString());
        }
        Collections.sort(rows);
        return rows;
    }
    private static long sum(Table table, String column) {
        long result = 0;
        for (Row row : table) result += ((Number) row.get(column)).longValue();
        return result;
    }
    private static Map<String, Integer> states(Table table, String column) {
        Map<String, Integer> result = new TreeMap<String, Integer>();
        for (Row row : table) { String key = String.valueOf(row.get(column)); result.put(key, result.getOrDefault(key, 0) + 1); }
        return result;
    }
    private static void metrics(String prefix, Database db) throws Exception {
        Table books = db.getTable("tblBook");
        System.out.println(prefix + " books=" + books.getRowCount() + " total=" + sum(books, "total_copies")
                + " available=" + sum(books, "available_copies"));
        System.out.println(prefix + " loans=" + states(db.getTable("tblBorrowRecord"), "status"));
        System.out.println(prefix + " bills=" + states(db.getTable("tblLibraryCompensation"), "status")
                + " walletRows=" + db.getTable("tblBankAccount").getRowCount()
                + " ledgerRows=" + db.getTable("tblWalletTransaction").getRowCount());
    }
    private static String sha(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(bytes)) text.append(String.format("%02x", item & 255));
        return text.toString();
    }
}
