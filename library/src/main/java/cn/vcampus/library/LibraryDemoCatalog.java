package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 50 种演示馆藏及相对日期借阅样例；与正式数据库数据隔离。 */
final class LibraryDemoCatalog {
    /** 创建独立的50种演示馆藏。 */
    static InMemoryLibraryRepository withDemoCatalog() {
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

    /** 在独立演示馆藏中加入相对当前日期的借阅提醒样例。 */
    static InMemoryLibraryRepository withDemoData() {
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
}
