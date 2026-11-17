# 运维 CLI 子命令速查

> CLI 只作运维与排障通道；日常使用走桌面窗口（窗口 + 系统托盘）。

| 子命令 | 用途 | 关键参数 | 退出码 |
|---|---|---|---|
| `gui` | 启动桌面程序（真实端点；缺配置即 fail-fast） | 无 | 0 / 3 |
| `config-check` | 校验环境变量凭据位，缺项列出缺失变量名 | 无 | 0 / 3 |
| `conn-check` | 连接状态 / 待处理条数 / 今日已提交数 / 降级态 / 最近错误 | 无 | 0 |
| `replay` | 离线回放本地样例，产出对照表草案 | `--data <dir>` `--visit <visit_no>` | 0 / 3 |
| `batch` | 数百条级批量跑批，输出条数 / 耗时 / 吞吐 / 一致率 | `--data <dir>` | 0 / 3 |
| `case-suite` | 逐条执行 `cases/` 案例集 | `--cases <dir>` | 0 / 3 |
| `capture` | 采集真实运行截图到 `docs/screenshots/` | `--data <dir>` `--out <dir>` | 0 / 3 |
| `--version` | 打印产品名与版本号 | 无 | 0 |
| `--help` | 打印用法 | 无 | 0 |

退出码约定：**0 成功 / 2 参数错 / 3 前置条件缺失**。

示例：

```bash
java -jar target/record-coding-evidence.jar config-check
java -jar target/record-coding-evidence.jar replay --data data --visit VISIT-000001
java -jar target/record-coding-evidence.jar batch --data data
java -jar target/record-coding-evidence.jar case-suite --cases cases
```
