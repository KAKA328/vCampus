# Common API

本模块是客户端和服务器端的唯一共享契约层。新增消息类型、字段或实体时必须同步更新设计说明书，并通过 Pull Request 审查。

选课完整流程统一使用 `COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`。课程目录、教学班和选课轮次维护统一使用 `COURSE_MANAGE`。

商店协议使用 token-only 命令：商品查询、购买、本人订单、购物车、商品维护、全量订单和热销排行分别由 `STORE_*` 消息及对应 `Store*Command`/`Cart*Command` 承载；服务端必须从会话解析用户身份并先完成权限校验。
