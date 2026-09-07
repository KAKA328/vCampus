# vCampus 客户端 UI 开发与对接规范

本文档约定 vCampus Swing 客户端后续页面的视觉和响应式开发方式。所有新增或重构的客户端页面应遵守本规范，避免各模块分别使用固定字号、固定窗口尺寸和不一致的按钮/表格样式。

## 1. 目标与适用范围

目标是让客户端在不同 Windows 显示缩放、不同分辨率和不同窗口大小下保持：

- 文字清晰，关键操作容易点击；
- 信息不会因窗口变窄而重叠或被压缩到无法阅读；
- 同类按钮、表格、提示信息的风格一致；
- 业务模块只关注业务流程，不重复实现主题和缩放逻辑。

本规范适用于 `client/src/main/java/cn/vcampus/client/view/` 下的 Swing 页面、对话框和可复用组件。它不改变 Socket 协议、服务端或业务模块的接口。

## 2. 统一 UI 基础设施

| 组件 | 位置 | 职责 | 使用要求 |
|---|---|---|---|
| `UiMetrics` | `client/.../view/UiMetrics.java` | 根据系统 DPI/显示缩放计算尺寸、边距和字号 | 新代码中的逻辑尺寸必须优先通过它转换 |
| `VCampusTheme` | `client/.../view/VCampusTheme.java` | 颜色、字体、按钮、输入框、表格、滚动条等统一主题 | 页面必须复用，不要自行复制颜色和边框代码 |
| `WrappingFlowLayout` | `client/.../view/WrappingFlowLayout.java` | 窄窗口时让操作按钮自动换行 | 适合筛选栏、工具栏、按钮组 |
| `ScrollablePagePanel` | `client/.../view/ScrollablePagePanel.java` | 页面内容过高时提供正确的滚动尺寸 | 长页面优先使用 |
| `VCampusTheme.pageScroll(...)` | `VCampusTheme` | 页面级纵向滚动容器 | 功能页面的主体区域优先用它包装 |

`UiMetrics` 中的参数都表示 **96 DPI、100% 缩放下的逻辑尺寸**，不是某一台电脑上的绝对像素。例如：

```java
setLayout(new BorderLayout(0, UiMetrics.px(16)));
panel.setBorder(VCampusTheme.padding(20, 24, 20, 24));
button.setPreferredSize(UiMetrics.dimension(140, 0));
label.setFont(VCampusTheme.font(Font.BOLD, 20));
```

不要在新页面中直接写 `new Dimension(300, 180)`、`new Insets(8, 8, 8, 8)`、`new Font(..., 14)` 或未经转换的固定间距；应分别改用 `UiMetrics.dimension(...)`、`UiMetrics.insets(...)`、`VCampusTheme.font(...)`、`UiMetrics.px(...)`。

## 3. 页面结构规范

### 3.1 功能页面推荐骨架

```java
public final class XxxPanel extends JPanel {
    public XxxPanel(/* 业务依赖 */) {
        setLayout(new BorderLayout(0, UiMetrics.px(16)));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);
    }

    private JPanel body() {
        JPanel body = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        body.setOpaque(false);
        body.add(queryBar(), BorderLayout.NORTH);
        body.add(contentPanel(), BorderLayout.CENTER);
        return body;
    }
}
```

说明：标题区放页面名称和简短说明；主体区域可滚动；查询栏、表格、编辑区等放入 `body()`。页面不能因为内容增加而超出主窗口后无法访问。

### 3.2 需要换行的工具栏

查询、筛选、导入、保存等按钮较多时，使用 `WrappingFlowLayout`，不要依赖一行固定宽度：

```java
JPanel actions = new JPanel(new WrappingFlowLayout(
        FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
VCampusTheme.panel(actions);
actions.add(queryButton);
actions.add(filterBox);
actions.add(saveButton);
```

窗口变窄时，控件会自动移到下一行；不应通过缩小按钮文字或隐藏重要操作来“塞进一行”。

### 3.3 表格页面

表格必须先应用统一主题：

```java
VCampusTheme.table(table);
```

如果表格包含课程名称、时间、地点、备注等较长字段，应保持关键列最小可读宽度，并允许横向滚动：

```java
table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
table.getColumnModel().getColumn(0).setPreferredWidth(UiMetrics.px(120));
table.getColumnModel().getColumn(1).setPreferredWidth(UiMetrics.px(220));
panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
```

不要在窄窗口里强制把所有列挤在可见区域内。表格的可读性比“完全不出现横向滚动条”更重要。

### 3.4 宽屏与窄屏布局

- 信息卡片、列表等可在宽屏并排显示；窄屏时应减少列数或改为上下堆叠。
- 可复用 `ResponsiveModuleGridPanel`、`ResponsiveCardRowPanel` 的布局方式；新增复杂布局时，应在自定义面板的 `doLayout()` 和 `getPreferredSize()` 中同时处理宽屏与窄屏。
- 用逻辑断点，例如 `UiMetrics.px(760)`；不要用裸露的 `760` 像素作为设备无关的判断条件。
- 不使用绝对坐标和 `setBounds(...)` 组织普通业务表单。只有确有必要的自定义响应式容器才可在内部计算组件位置。

### 3.5 对话框和窗口

- `JFrame` / `JDialog` 的默认尺寸、最小尺寸均使用 `UiMetrics.dimension(...)`；
- 表单字体、标题字体使用 `VCampusTheme.font(...)`；
- 对用户长时间停留的窗口（如登录页），可在窗口放大时额外调整局部字体，但必须设置上限，不能让字体无限变大；
- HTML 标签中的 `font-size: ...px` 也必须使用 `UiMetrics.px(...)` 计算，不能硬编码。

## 4. 主题与可读性规范

### 4.1 必须使用的主题方法

| 控件 | 方法 |
|---|---|
| 主要提交操作（保存、确认、登录、选课） | `VCampusTheme.primaryButton(button)` |
| 次要操作（查询、取消、刷新、返回） | `VCampusTheme.secondaryButton(button)` |
| 输入框/密码框/下拉框外层输入组件 | `VCampusTheme.field(component)` |
| 普通内容卡片 | `VCampusTheme.panel(panel)` |
| 表格 | `VCampusTheme.table(table)` |
| 状态标签 | `VCampusTheme.statusPill(label, color)` |
| 页面和表格滚动区域 | `VCampusTheme.pageScroll(...)` / `VCampusTheme.scrollPane(...)` |

不要为某个模块重新定义一套蓝色、灰色、边框和圆角；若确实需要全系统新增语义颜色或组件，应先和负责客户端公共部分的同学沟通，再修改 `VCampusTheme`。

### 4.2 字体和提示信息

- 页面标题建议：`VCampusTheme.font(Font.BOLD, 24)`；
- 分区标题建议：`VCampusTheme.font(Font.BOLD, 16)`；
- 普通正文和输入内容建议：14–16 逻辑字号；
- 错误、成功、加载中信息使用现有的 `VCampusTheme.DANGER`、`SUCCESS`、`MUTED` 等语义颜色；
- 不能只依靠颜色表达成功或失败，提示文本必须写明结果。

## 5. 已完成的参考页面

后续开发应优先参考这些已接入规范的实现，而不是从旧页面复制固定像素代码：

| 场景 | 参考实现 | 已覆盖内容 |
|---|---|---|
| 全局缩放 | `UiMetrics`、`VCampusTheme` | 字号、间距、边框、导航、滚动条、主题控件 |
| 登录与注册 | `LoginFrame`、`RegisterDialog` | 窗口尺寸、表单间距、登录页动态字体 |
| 主框架 | `MainFrame` | 主窗口、侧栏、头部、工作台外壳 |
| 工作台 | `DashboardWorkbenchPanel`、`ResponsiveModuleGridPanel` | 卡片网格、统计卡片、窄屏堆叠、信息栏 |
| 选课页面 | `CourseSelectionPanel` | 换行工具栏、轮次选择、可横向滚动的教学班表格 |

旧页面中仍可能存在固定像素写法。它们只是待改造对象，不是新页面的参考标准。

## 6. 新增或修改页面的交接流程

1. 先确认页面属于哪个角色、需要哪些业务数据和操作；业务接口未稳定时，可以先搭建只读布局，但不要伪造正式业务结果。
2. 从本规范的页面骨架开始，复用 `UiMetrics` 与 `VCampusTheme`。
3. 为宽屏、窄屏和内容较多的场景选择布局：换行工具栏、页面滚动、表格横向滚动或卡片堆叠。
4. 不修改服务端逻辑、公共消息协议或数据库表结构来解决纯 UI 问题。
5. 为关键布局规则补充客户端测试；测试中同样使用 `UiMetrics.px(...)`，不要写死未经缩放的屏幕像素。
6. 提交前执行：

   ```powershell
   mvn -q -pl client -am test
   git diff --check
   ```

7. 手动验证至少三种情况：系统显示缩放 100% / 125% 或 150%，主窗口较窄，主窗口较宽。
8. PR 描述中说明：改造了哪些页面、是否只改变 UI、测试命令与结果。

## 7. 修改公共 UI 基础设施时的额外要求

以下文件会影响所有客户端模块，修改前应在组内沟通，并在 PR 中特别说明影响范围：

- `UiMetrics.java`
- `VCampusTheme.java`
- `WrappingFlowLayout.java`
- `ScrollablePagePanel.java`
- `ResponsiveModuleGridPanel.java`
- `ResponsiveCardRowPanel.java`

公共组件变更后必须运行客户端全量测试；不要只验证自己负责的页面。若新增公共组件，应提供简短中文 Javadoc 和对应测试。

## 8. 页面交付检查清单

- [ ] 页面没有新增未经 `UiMetrics` 转换的固定尺寸、边距或字号。
- [ ] 主要/次要按钮、输入框、表格已使用 `VCampusTheme`。
- [ ] 窄窗口下操作按钮不会重叠或消失。
- [ ] 内容超过窗口高度时可纵向滚动。
- [ ] 长字段表格在窄窗口下可横向滚动，而不是压缩成不可读内容。
- [ ] 不同角色只能看到其被授权的入口和操作。
- [ ] 没有为了 UI 修改业务规则、Socket 协议或数据库结构。
- [ ] `mvn -q -pl client -am test` 与 `git diff --check` 通过。

## 9. 当前待改造范围

接下来优先按“页面使用频率 + 信息密度”推进：

1. 教师成绩录入、教务成绩审核及文件导入页面；
2. 课程目录、教学班、培养方案、选课轮次等教务管理页面；
3. 学籍管理页面；
4. 图书馆、商店和用户管理页面；
5. 统一进行跨页面视觉检查与细节优化。

各模块同学可按本规范独立改造自己的页面；如改动涉及公共主题或布局工具，先沟通后再提交，避免多个分支同时修改同一公共文件造成冲突。
