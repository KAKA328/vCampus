# 图书馆模块功能说明

> 文档状态：持续更新中，跟随代码进度同步修改。

## 一、模块职责

图书馆模块负责虚拟校园系统中“图书资源”的管理与借阅，包括：

- 图书检索与浏览
- 图书详情查看
- 图书入库（管理员/馆员）
- 借书 / 批量借书
- 还书
- 借阅历史查询

## 二、当前功能清单

| 功能 | 说明 | 状态 |
| --- | --- | --- |
| 图书搜索 | 支持按书名、作者、ISBN、分类、书号模糊搜索 | 已完成 |
| 图书详情 | 查看单本书的完整信息（出版社、库存、位置等） | 已完成 |
| 按分类浏览 | 按分类返回图书列表 | 已完成 |
| 管理员录入图书 | 新增图书，重复编号会被拒绝 | 已完成 |
| 单本借书 | 校验库存、重复借阅后扣减库存并生成借阅记录 | 已完成 |
| 批量借书 | 一次借多本，同一批共享一个借阅单号 orderId | 已完成 |
| 还书 | 将借出中的记录改为已还，恢复库存 | 已完成 |
| 借阅历史 | 查询某个用户的全部借还记录 | 已完成 |
| 教师借阅 | 借阅参数使用 userId，不再局限于学生 | 已完成 |
| 续借 | 延长借阅期限 | 待做（可选） |
| 角色权限 | 管理员/馆员与普通用户的操作权限区分 | 待服务器联调时做 |
| 客户端 UI | Swing 图书馆界面 | 计划第 3 周 |

## 三、功能示意图

### 整体调用流程

    客户端用户
      -> ServerApplication 消息分发
      -> LibraryMessageHandler
      -> LibraryService 接口
      -> InMemoryLibraryService
      -> 内存书架 books / 借阅账本 records
      -> 返回 ServiceResult
      -> 客户端

### 借书流程

    用户发起借书
      -> 用户号是否为空？ 是 -> 返回 BAD_REQUEST
      -> 书单是否为空？   是 -> 返回 BAD_REQUEST
      -> 书是否存在？     否 -> 返回 NOT_FOUND
      -> 是否还有库存？   否 -> 返回 CONFLICT
      -> 该用户是否已借同一本？ 是 -> 返回 CONFLICT
      -> 全部通过
      -> 生成 orderId
      -> 逐本扣减库存
      -> 逐本写入 BorrowRecord
      -> 返回 OK

## 四、核心代码结构

| 文件 | 职责 |
| --- | --- |
| Book | 图书数据模板 |
| BorrowStatus | 借阅状态枚举（借出中 / 已还） |
| BorrowRecord | 借阅记录数据模板（含 orderId / recordId） |
| BorrowRequest | 借书/还书消息参数 |
| LibraryService | 图书馆能力接口 |
| InMemoryLibraryService | 内存版实现，后续替换为 Access 数据库版 |
| InMemoryLibraryServiceTest | 12 个单元测试 |
| LibraryMessageHandler | 服务器端消息适配 |

## 五、数据库设计

- `tblBook`：图书表，一行是一本书，含书名、作者、ISBN、分类、出版社、库存、馆藏位置。
- `tblBorrowRecord`：借阅流水表，一行是“某用户借某本书”的一次记录。
- `tblBorrowRecord.order_id`：批量借阅单号。一次批量借 N 本共享一个 `order_id`，每本书有独立 `record_id`。
- `tblBorrowRenew`：续借记录表，为后续“申请延期/续借”预留，当前代码尚未使用。

设计脚本已写入 `database/schema.sql`，演示数据在 `database/seed.sql`。

## 六、当前进度记录

- 已完成核心业务逻辑与 12 个单元测试，项目 mvn test 通过。
- 已支持批量借阅（同一批共享 orderId）。
- 借阅模型已从 studentId 重构为 userId，兼容教师借阅。
- 服务器端图书馆消息分发已接入。
- 已完成 Access 数据库表结构设计（`tblBook`、`tblBorrowRecord`、`tblBorrowRenew`）和演示数据。
- 尚未把内存实现替换为 Access 实现、尚未做客户端 UI、角色权限控制。
