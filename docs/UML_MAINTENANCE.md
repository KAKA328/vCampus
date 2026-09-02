# UML 类图维护说明

入口文件是 [`whole-vCampus.puml`](whole-vCampus.puml)。它负责统一版式、引入模块片段和描述跨模块关系；模块片段位于 [`uml/`](uml/) 目录。

## 文件职责

| 文件 | 维护内容 |
| --- | --- |
| `whole-vCampus.puml` | 图的入口、样式、跨模块依赖和分层排布 |
| `uml/common.puml` | `Message`、`MessageType`、`ServiceResult`、角色等共享类型 |
| `uml/user-management.puml` | 账号、会话、权限、审计、密码重置和仓储接口 |
| `uml/course-selection.puml` | 课程目录、教学班、选课轮次、选课记录和课程命令 |
| `uml/student-management.puml` | 学籍档案、课程历史和学业审查 |
| `uml/library.puml` | 图书馆接口与图书模型 |
| `uml/store.puml` | 商品、订单、购物车、库存和商店命令 |
| `uml/server.puml` | Socket 服务端、消息处理器、工厂和 Access 适配器 |
| `uml/client.puml` | Swing 页面、远程服务适配器和 Socket 传输 |

## 修改规则

1. 新增或删除 Java 核心类时，先修改对应模块片段，再检查入口文件中的跨模块关系。
2. 类名和关系使用代码中的实际类型名；数据库表、Socket 消息名等非类元素只在注释或接口字段中说明。
3. 简单的 Swing 控件、测试类和 `vcampus/` 历史目录不纳入整体图，避免报告图失去可读性；需要审阅某个细节时再单独绘制模块图。
4. `..>` 表示依赖，`-->` 表示关联，`o--` 表示聚合，`*--` 表示组合，`<|..` 表示接口实现。

## 导出

在仓库根目录执行：

```powershell
java -jar plantuml.jar -tsvg docs/whole-vCampus.puml
java -jar plantuml.jar -tpdf docs/whole-vCampus.puml
```

SVG 适合网页和 Word 缩放，PDF 适合报告打印。导出的文件是构建产物，通常不提交；需要固定版本时再由文档负责人归档。

## 与代码同步检查

修改类图后至少执行：

```powershell
mvn clean test
```

然后打开 `whole-vCampus.puml` 检查 PlantUML 是否能解析；如果出现类名不存在或关系指向旧协议，优先更新模块片段和跨模块关系。
