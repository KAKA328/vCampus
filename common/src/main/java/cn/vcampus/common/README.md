# Common API

本模块是客户端和服务器端的唯一共享契约层。新增消息类型、字段或实体时必须同步更新设计说明书，并通过 Pull Request 审查。

选课完整流程已经显式升级为 V2 协议：`COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`。旧的 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 保留为早期课程级协议名称，不得在不说明兼容策略的情况下继续塞入轮次、教学班、选课记录等新字段。
