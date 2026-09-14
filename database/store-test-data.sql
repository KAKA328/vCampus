-- 商店模块专项测试数据：限量商品 + 并发买家钱包
-- 用法（仓库根目录执行，产出独立测试库，不覆盖 database/vCampus.accdb）：
--   .\database\rebuild.ps1 -DatabasePath database\store-test.accdb -AdditionalScript database\store-test-data.sql
-- 该命令按序执行 schema.sql → seed.sql → 本文件；库存被竞态测试消耗后重跑本命令即可一键复位。
-- 服务器必须带 --db 连本测试库，否则走内存演示数据、进程退出即丢：
--   java -jar server/target/vCampusServer.jar --db database/store-test.accdb --port 19090
--
-- 约定：
-- 1. 本文件不新增登录账号，只复用 seed.sql 已有演示账号（初始密码统一 Demo123）。
-- 2. 商品编号使用 P9xx 段，避开 seed.sql 的 P001..P105；AccessDatabaseSchemaTest 断言 seed 商品数为 105，本文件不影响该断言。
-- 3. 脚本以「;」切分逐条执行：值内不得出现「;」与单引号「'」，注释必须独占整行。
-- 4. 场景说明与预期状态码见 test-data/STORE_TEST_DATA.md。

-- 一、限量商品（并发、竞态与边界测试专用）
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P901', '限量盲盒 仅剩一件', 1, 9.9, '竞态测试 两人同时各买一件只允许一人成功 也用于一人买两件与一人买一件的顺序对照', '文创纪念品', 1, 0),
('P902', '秒杀保温杯 仅剩两件', 2, 39.0, '竞态测试 一人买两件与一人买一件同时提交 需求总量三件超过库存 验证不拆分成交', '日用品', 1, 0),
('P903', '限量球鞋 五双', 5, 99.0, '竞态测试 三人各买两双同时提交 最多两人成功 剩余一双', '体育用品', 1, 0),
('P904', '天价手办 回滚验证用', 3, 9999.0, '竞态测试 低余额买家触发预检拒绝或扣款失败回滚 已扣库存必须回补且不产生订单', '文创纪念品', 1, 0),
('P905', '已售罄限量徽章', 0, 5.0, '边界测试 库存为零 直购与结算均应返回库存不足 但仍可加入购物车', '文创纪念品', 1, 0),
('P906', '已下架限量海报', 10, 15.0, '边界测试 已下架商品 默认列表不可见 直购与结算均按商品不存在处理', '文创纪念品', 0, 0),
('P907', '竞态结算矿泉水 三瓶', 3, 2.0, '竞态测试 购物车子集批量结算 重复条目去重 以及结算时库存被他人抢走的回滚', '零食饮料', 1, 0),
('P908', '压测小饼干 二十包', 20, 0.5, '竞态测试 多人各买一包高频齐射 成功订单数必须等于库存扣减量', '零食饮料', 1, 0),
('P909', '半分糖果 金额换算边界', 2, 0.004, '边界测试 单价低于半分 买一件换算为零分应被拒绝 买两件换算为一分可成交', '零食饮料', 1, 0);

-- 二、并发买家钱包
-- seed.sql 只为 demo_student、demo_teacher、demo_admin、demo_store_manager 预置了账户；
-- 本文件给其余演示学生账号补账户，凑齐四位余额充足的并发买家。重复执行会因主键冲突失败，须先重建库。
INSERT INTO tblBankAccount(user_id, balance_cents) VALUES
('demo_student_new', 50000),
('demo_student_retake', 50000);

-- 低余额买家：用于「库存够但余额不足」的预检拒绝，以及并发把钱花光后的扣款失败回滚。
-- 1.50 元买得起三包 P908（单价 0.50 元），买不起 P904（9999 元）。
INSERT INTO tblBankAccount(user_id, balance_cents) VALUES
('demo_student_elective', 150),
('demo_student_cross', 100);

-- 预期结果速查（完整场景表见 test-data/STORE_TEST_DATA.md）：
-- P901 两人各买一件：恰好一人 OK、另一人 CONFLICT，库存最终为 0，绝无负库存或两笔订单。
-- P901 一人买两件 + 一人买一件：买两件者恒 CONFLICT（预检 1 小于 2），买一件者视先后可能 OK。
-- P902 买两件与买一件同时提交：至多一人成功；若买一件者先成功则库存剩一件，买两件者仍 CONFLICT。
-- P903 三人各买两双：最多两人成功，订单数 2，库存最终为 1。
-- P904 低余额买家直购：PAYMENT_REQUIRED；若库存在并发中被扣走则 CONFLICT 且库存回补。
-- P905 任意数量直购或结算：CONFLICT（库存不足）；加入购物车仍可成功，结算时被预检拦下。
-- P906 默认列表不可见（需勾选显示已下架），直购与结算均 NOT_FOUND。
-- P907 子集批量结算：重复 cartItemId 只结算一次；购物车清理失败时整单回滚并返回 CONFLICT。
-- P908 多人齐射：成功订单数 = 20 减去剩余库存，不超卖。
-- P909 买一件 BAD_REQUEST（换算为零分），买两件 OK（换算为一分）。
