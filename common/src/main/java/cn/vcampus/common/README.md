# Common API

本模块是客户端和服务器端的唯一共享契约层。新增消息类型、字段或实体时必须同步更新设计说明书，并通过 Pull Request 审查。

选课完整流程已经显式升级为 V2 协议：`COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`。旧的 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 保留为早期课程级协议名称，不得在不说明兼容策略的情况下继续塞入轮次、教学班、选课记录等新字段。

商店协议使用 token-only 命令：商品查询、购买、本人订单、购物车、商品维护、全量订单和热销排行分别由 `STORE_*` 消息及对应 `Store*Command`/`Cart*Command` 承载；服务端必须从会话解析用户身份并先完成权限校验。

学籍协议使用 token-first 命令：`STUDENT_QUERY` 对应 `StudentQueryCommand`，支持本人、按学号和按班级查询；`STUDENT_UPDATE` 对应 `StudentUpdateCommand`。学生本人查询必须由服务端按 `token -> user_id -> tblStudent.user_id` 定位档案，不能信任客户端传入的学号。
