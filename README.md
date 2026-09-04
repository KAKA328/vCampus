# vCampus 虚拟校园综合管理系统

一个基于 Java、Maven 多模块、Socket 客户端/服务器架构的虚拟校园课程实践项目。系统把用户登录、权限控制、学籍、选课、图书馆和校园商店拆分为独立模块，由服务器统一校验身份和数据范围。

## 项目能做什么

- **用户与权限**：管理员开户注册、发放初始密码、登录、登出、注销、授权和审计。
- **学籍管理**：学生档案、教师档案、专业班级、学籍状态、课程历史和学业审查数据模型。
- **选课系统**：课程查询、学生选课/退课、本人已选课程查询；教师和教务管理员使用不同的数据范围。
- **图书馆**：按关键词/分类查询、详情、批量借阅、归还、借阅记录和管理员馆藏维护。
- **校园商店**：商品查询、购买、个人订单查询，以及管理员商品/库存维护接口。
- **桌面客户端**：Java Swing 登录页、角色工作台和按角色显示的模块导航。

## 角色说明

系统当前定义六种身份：

| 身份 | 主要职责 |
|---|---|
| `ADMIN` 系统管理员 | 全部模块管理；唯一可以创建账号和发放初始密码 |
| `STUDENT` 学生 | 查看本人学籍，选课/退课，借还图书，购买商品 |
| `TEACHER` 教师 | 查询授课相关学生和课程，录入成绩，借还图书，购买商品 |
| `ACADEMIC_ADMIN` 教务管理员 | 维护学籍、课程、容量，执行学业审查和成绩复核 |
| `LIBRARIAN` 图书管理员 | 维护图书、库存、借阅和归还记录 |
| `STORE_MANAGER` 商店管理员 | 维护商品、库存、订单和购买记录 |

详细权限矩阵见 [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md)。

## 账号与注册规则

登录页不提供未登录用户的公开注册。账号由数据库初始化脚本预置，或由 `ADMIN` 登录后进入“用户管理”创建。创建成功后，管理员向用户发放初始密码；学生和教师账号还需维护对应业务档案。

## 本地测试账号

以下账号对应 [`database/seed.sql`](database/seed.sql) 中的演示数据，初始密码统一为 `Demo123`。这些是本地联调账号，不得用于生产环境：

| 账号 | 初始密码 | 身份 | 测试内容 |
|---|---|---|---|
| `demo_admin` | `Demo123` | 系统管理员 | 用户开户注册、全部模块入口和权限管理 |
| `demo_academic_admin` | `Demo123` | 教务管理员 | 学籍管理、选课管理、学业审查 |
| `demo_librarian` | `Demo123` | 图书管理员 | 图书馆管理 |
| `demo_store_manager` | `Demo123` | 商店管理员 | 商品、库存和订单管理 |
| `demo_student` | `Demo123` | 学生 | 本人学籍、选课、图书馆和商店 |
| `demo_teacher` | `Demo123` | 教师 | 学籍查询、课程和成绩入口 |

内存模式也可以通过环境变量临时创建管理员：

```powershell
$env:VCAMPUS_BOOTSTRAP_ADMIN_ID="admin001"
$env:VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD="Admin123"
$env:VCAMPUS_BOOTSTRAP_ADMIN_NAME="系统管理员"
```

## 环境要求

- JDK 8 或更高版本（源码按 Java 8 兼容目标编译）
- Maven 3.8 或更高版本
- Access 持久化需要 `.accdb` 文件；不指定数据库时使用内存演示数据。指定 `--db` 后图书馆使用 Access 保存馆藏、借阅订单和借阅明细；其他模块的持久化配置见各模块对接说明。

## 构建与测试

在仓库根目录执行：

```powershell
cd D:\codex\java协作
mvn clean test
mvn -DskipTests package
```

打包后生成 `server/target/vCampusServer.jar` 和 `client/target/vCampusClient.jar`。

## 启动程序

### 1. 启动服务器

第一个 PowerShell 窗口：

```powershell
cd D:\codex\java协作
$env:VCAMPUS_BOOTSTRAP_ADMIN_ID="admin001"
$env:VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD="Admin123"
$env:VCAMPUS_BOOTSTRAP_ADMIN_NAME="系统管理员"
java -jar .\server\target\vCampusServer.jar --port 19090
```

看到 `vCampus server listening on port 19090` 表示服务器已启动，并保持该窗口运行。

### 2. 启动客户端

第二个 PowerShell 窗口：

```powershell
cd D:\codex\java协作
java -jar .\client\target\vCampusClient.jar --host 127.0.0.1 --port 19090
```

使用 `admin001 / Admin123` 登录后，在“用户管理 → 创建账号”开户注册。登录页不会显示“注册新用户”。如果没有设置 bootstrap 环境变量，也可以使用内存模式预置的 `demo_admin / Demo123` 登录。

### 3. Socket 冒烟演示

服务器运行时，在第二个窗口执行：

```powershell
java -jar .\client\target\vCampusClient.jar --demo --host 127.0.0.1 --port 19090
```

该演示使用管理员会话创建临时学生账号，再测试登录、课程授权和登出。

### 4. 使用 Access 数据库

```powershell
java -jar .\server\target\vCampusServer.jar --db D:\data\vCampus.accdb --port 19090
```

数据库说明、表结构和初始化数据见 [`database/README.md`](database/README.md)、[`database/schema.sql`](database/schema.sql) 和 [`database/seed.sql`](database/seed.sql)。

## 代码结构

```text
common/              共享消息、角色、状态码和数据载荷
user-management/     用户、会话、密码、权限和审计
student-management/  学生/教师档案、课程历史和学业审查模型
course-selection/    课程、开课、选课、退课和成绩接口
library/             图书、借阅和归还接口
store/               商品、购买、订单和库存接口
server/              Socket 服务端、线程池和消息分发
client/              Swing 登录页、工作台和业务页面
database/            Access 建表脚本、初始化数据和说明
docs/                设计基线、权限矩阵、接口和外部项目调研
```

服务器端是权限和数据范围的最终裁判。客户端隐藏导航按钮只是界面优化，不能代替服务器端授权检查。

## 当前完成情况

- 用户登录、登出、注销、会话校验和角色权限矩阵已接入。
- 管理员开户注册协议已接入，普通未登录用户不能调用 `REGISTER`。
- 选课模块已接入课程查询、选课、退课和本人已选课程查询。
- 商店客户端已接入商品查询、购买和本人订单查询。
- 图书馆已接入 Swing 页面、V2 消息协议、服务器权限处理、内存服务和 Access 原子借还事务。
- 图书馆已与当前学籍、选课和商店主线代码完成构建级对接，完整共享数据库及多人界面联调仍需小组验收。

## 设计与协作资料

- [`docs/EXTERNAL_VCAMPUS_RESEARCH.md`](docs/EXTERNAL_VCAMPUS_RESEARCH.md)：近期 GitHub 上其他 SEU/vCampus 项目调研及本项目设计决策
- [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md)：角色与权限矩阵
- [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md)：系统设计说明
- [`docs/INTERFACES.md`](docs/INTERFACES.md)：消息和模块接口
- [`docs/MODULE_INTEGRATION_GUIDE.md`](docs/MODULE_INTEGRATION_GUIDE.md)：模块接入约定
- [`docs/ACCEPTANCE_CHECKLIST.md`](docs/ACCEPTANCE_CHECKLIST.md)：验收清单

## 常见问题

**登录页为什么没有注册按钮？** 这是预期设计。只有系统管理员可以开户注册，普通用户需要从管理员处获得账号和初始密码。

**登录后看不到学籍信息怎么办？** 账号与学籍档案分开保存，请确认 `user_id` 已绑定 `tblStudent` 或 `tblTeacher`，并补齐课程历史数据。

**客户端无法连接服务器怎么办？** 确认服务器窗口仍在运行，并检查客户端和服务器使用相同端口（默认 `19090`）。

**如何停止服务器？** 回到服务器窗口按 `Ctrl + C`。

## 协作方式

项目使用 GitHub Flow。功能从最新 `main` 创建分支，通过 Pull Request 合并；提交前至少运行：

```powershell
mvn clean test
```
