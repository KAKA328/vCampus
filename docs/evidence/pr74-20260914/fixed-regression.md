# 修复提交完整回归

被测试的业务代码提交：`8808663693b43ef1e5a7985af4bee97910516e0d`。相对原提交 `c377c218` 只改 `MainFrame.java`、`StorePanel.java`，增加 `StoreWalletRefreshTest.java`。执行期间无生产/测试源码变更；其后仅归档文档和证据。

Windows、Temurin JDK 8u502、Maven 3.9.16，与原提交测试环境相同。完整执行：

```powershell
$env:MAVEN_OPTS = '-Dmaven.repo.local=C:\Users\ASUS\.m2\repository'
mvn -B -Dstyle.color=never package "-DforkCount=0"
```

实际退出码 **0**，`BUILD SUCCESS`，所有 Reactor 模块 `SUCCESS`。用时 **7:20**，完成于 **2026-09-14T17:11:25+08:00**。

| 模块 | 测试 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| common | 4 | 0 | 0 | 0 |
| user-management | 44 | 0 | 0 | 0 |
| student-management | 22 | 0 | 0 | 0 |
| course-selection | 78 | 0 | 0 | 0 |
| library | 49 | 0 | 0 | 0 |
| store | 180 | 0 | 0 | 0 |
| server | 339 | 0 | 0 | 0 |
| client | 213 | 0 | 0 | 0 |
| **合计** | **929** | **0** | **0** | **0** |

相比原提交 924 项，增加 5 项商店钱包页面刷新测试。完整回归也重新执行了 `LibraryAcceptanceDataTest` 和 `LibrarySocketConcurrencyTest`，不是只跑新增 UI 测试。

原始证据：[完整 Maven 输出](full-maven-fixed.txt)、[各模块 Surefire 汇总](full-fixed-counts.json)。这些是本次本地运行的结果，不代表 GitHub CI 状态或替代组长审查。

全量结束后保存各模块报告统计，没有再用单类测试覆盖此份统计。共享钱包组件联调再次运行时使用新验收库副本，支付和重复付款均通过；截图和输出见 [主报告](README.md#4-图书馆赔偿与商店钱包页面联调)。本次创建的独立测试服务器已停止。

## 证据文件完整性

[SHA256SUMS.txt](SHA256SUMS.txt) 覆盖本目录的原始 `.txt` / `.json` 输出及 `.png` 截图（不包含哈希清单自身）。`.gitattributes` 禁止 Git 对这些文本证据做换行转换，确保克隆后的字节哈希仍可对照；仅这些原始输出免除 whitespace 格式检查，保留 Windows CRLF 和 Maven 原有行尾空格，源码和 Markdown 仍正常检查。哈希用于核对附件完整性，不是第三方签名认证。
