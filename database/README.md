# 数据库目录

最终提交时放入机房兼容版本的 `vCampus.accdb`，并补充 `schema.sql` 与 `seed.sql`（如 Access 版本不支持某条 SQL，以实际建库结果为准）。

## 表说明

| 表名 | 用途 |
|---|---|
| `tblUser` | 用户（管理员 / 学生 / 教师），由用户管理模块维护 |
| `tblBook` | 图书馆图书目录，一行是一本书 |
| `tblBorrowRecord` | 借阅流水，一行是“某用户借某本书”的一次记录 |
| `tblBorrowRenew` | 续借记录，暂未启用，为后续“申请延期”预留 |

## 借阅单号设计

- 一次借一本书：生成一个 `order_id`，同时生成一条 `record_id`。
- 一次批量借 N 本书：同一批共享同一个 `order_id`，但每本书都有一条独立的 `record_id`。
- 这样既能在界面上按“借阅单”显示一次操作，也能按“每一本书”分别还书、分别查看状态。

示例：学生一次性借了《红楼梦》和《三体》，会得到：

| record_id | order_id | book_id | status |
|---|---|---|---|
| BR2 | BO2 | B003 | BORROWED |
| BR3 | BO2 | B004 | BORROWED |

同一个 `order_id=BO2`，但两条 `record_id`，还书时可以只还其中一本。
