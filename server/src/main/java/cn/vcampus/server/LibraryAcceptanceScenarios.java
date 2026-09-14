package cn.vcampus.server;

import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.LibraryCompensation;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import static cn.vcampus.server.LibraryAcceptanceData.require;

/** 用正式借还、遗失、付款、编辑流程构造一致的边界数据，而非伪造孤立状态行。 */
final class LibraryAcceptanceScenarios {
    /** 验收当天；到期样例均由它计算，不随日历失效而不自知。 */
    private final LocalDate date;
    /** 单一共享仓库。 */
    private final AccessLibraryRepository repository;
    /** 正式馆藏服务。 */
    private final DefaultLibraryService library;
    /** 与图书仓库共用锁的赔偿服务。 */
    private final AccessLibraryCompensationService compensations;

    /** 只绑定已创建的新验收库。 */
    LibraryAcceptanceScenarios(Path database, LocalDate date) {
        this.date = date;
        this.repository = new AccessLibraryRepository(database);
        this.library = new DefaultLibraryService(repository);
        this.compensations = new AccessLibraryCompensationService(database, repository, new AccessWalletRepository(database));
    }

    /** 保留 50 本基础目录，另加 30 种独立场景图书及 21 条借阅。 */
    void populate() {
        book("LI-NORMAL", "普通借还与补库存", 39.50, 6);
        book("LI-ZERO", "零元赠书", 0, 3);
        book("LI-LAST", "并发争抢最后一册", 39.50, 1);
        book("LI-MULTI", "并发争抢三册", 39.50, 3);
        book("LI-EMPTY", "零库存待补充", 39.50, 0);
        book("LI-EDIT", "借阅时的原书名", 39.50, 3);
        book("LI-LONG", "验收长标题：多客户端环境下图书馆借阅归还、实体副本与钱包原价赔偿的完整业务流程展示", 0.01, 2);
        book("LI-ROUND", "三位小数价格舍入", 39.505, 2);
        book("LI-RETURN", "已归还历史示例", 39.50, 2);
        int[] offsets = {-1, 0, 1, 3, 4};
        for (int i = 0; i < offsets.length; i++) {
            String id = "LI-DUE" + i;
            book(id, "到期边界 T" + (offsets[i] >= 0 ? "+" : "") + offsets[i], 20, 2);
            loan("li_dates", id, offsets[i]);
        }
        for (int i = 1; i <= 8; i++) {
            String id = "LI-Q" + i;
            book(id, "额度与批量借阅 " + i, 10, 5);
            if (i <= 4) loan("li_quota4", id, 20);
            if (i <= 5) loan("li_quota5", id, 20);
        }
        BorrowRecord returned = loan("li_teacher", "LI-RETURN", -2);
        require(repository.returnBook("li_teacher", returned.getRecordId(), date.minusDays(3)));
        loan("li_teacher", "LI-EDIT", 20);
        Book before = repository.findBook("LI-EDIT");
        require(repository.updateBook("demo_librarian", before, new Book(before.getBookId(), "编辑后的当前书名",
                before.getAuthor(), before.getIsbn(), before.getCategory(), before.getPublisher(),
                49.50, before.getTotalCopies(), before.getAvailableCopies(), before.getLocation())));
        loss("li_low", "LI-LOSS-LOW", 39.50, false);
        loss("li_exact", "LI-LOSS-EXACT", 39.50, false);
        loss("li_rich", "LI-LOSS-RICH", 39.50, false);
        loss("li_free", "LI-LOSS-ZERO", 0, false);
        loss("li_paid", "LI-LOSS-PAID", 39.50, true);
        book("LI-RACE-RETURN", "重复归还竞争专用", 39.50, 1);
        book("LI-RACE-LOSS", "归还与遗失竞争专用", 39.50, 1);
        book("LI-RACE-PAY", "重复支付竞争专用", 39.50, 1);
    }

    /** 新增图书及相同数量实体册；编号固定便于手工定位，实体册使用正常 UUID。 */
    private void book(String id, String title, double price, int copies) {
        require(library.addBook(new Book(id, title, "虚构测试作者", "DEMO-" + id, "计算机",
                "验收专用出版社", price, copies, copies, "验收区-" + id)));
    }

    /** 通过仓库原子接口保存历史日期，同时创建真实副本关联和书名快照。 */
    private BorrowRecord loan(String user, String id, int dueOffset) {
        return require(repository.borrowBatch(user, Collections.singletonList(id),
                date.plusDays(dueOffset).minusDays(30), date.plusDays(dueOffset))).get(0);
    }

    /** 真实确认遗失，按需付款；账单和库存与钱包流水保持业务规则一致。 */
    private void loss(String user, String id, double price, boolean paid) {
        book(id, "原价赔偿-" + user, price, 2);
        BorrowRecord record = loan(user, id, 20);
        LibraryCompensation bill = require(compensations.declareLoss("demo_librarian", record.getRecordId()));
        if (paid) require(compensations.pay(user, bill.getCompensationId()));
    }
}
