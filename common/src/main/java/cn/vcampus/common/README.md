# Common API

学籍专业目录：`STUDENT_MAJOR_DIRECTORY_QUERY_V1` + `MajorDirectoryQueryV1Command(token)`，返回 `List<MajorDirectoryEntry>`，仅返回有效专业，教务和系统管理员可读；培养方案仍保存名称。参见 `docs/MAJOR_DIRECTORY_INTEGRATION.md`。

本模块是客户端和服务器端的唯一共享契约层。新增消息类型、字段或实体时必须同步更新设计说明书，并通过 Pull Request 审查。

学籍已有档案更新使用 `STUDENT_UPDATE_V2` 与 `StudentUpdateV2Command(token, record, expected)`，expected 是页面最初加载的完整快照。冲突返回 `CONFLICT`；旧 `STUDENT_UPDATE` 的既有档案更新不再允许。详见 `docs/STUDENT_STALE_FORM_UPDATE.md`，客户端和服务端需配套升级。

选课完整流程统一使用 `COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`。课程目录、教学班和选课轮次维护统一使用 `COURSE_MANAGE`。培养方案维护使用 `COURSE_TRAINING_PLAN_MANAGE_V2` 与 `TrainingPlanManagementCommand`。

商店协议使用 token-only 命令：商品查询、购买、本人订单、购物车、商品维护、全量订单和热销排行分别由 `STORE_*` 消息及对应 `Store*Command`/`Cart*Command` 承载；服务端必须从会话解析用户身份并先完成权限校验。
