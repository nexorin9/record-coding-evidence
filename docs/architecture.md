# 架构、状态机与审计链

## 数据流

```
RIS (HL7 ORU^R01 / MLLP)  ──►  MllpServer ──► 证据快照(本机 SQLite)
PACS (QIDO-RS / DICOM JSON) ──► QidoRsClient ─┐
EMR  (REST / 病历首页)      ──► EmrClient ────┼──► EvidenceAssembler ──► 三栏对照表
                                              │        │
                                              │        ├─► CodingValidation（结构 / 字典 / 业务）
                        ModelGateway ─────────┴────────┴─► SemanticFinding（判定 + 归因 + 证据出处）
                                                                  │
                                                   编码员逐条采纳 / 驳回（人工唯一入口）
                                                                  │
                                              SubmitGate（闸门）──► QcReviewClient ──► 病案质控系统
```

线程模型：MLLP 监听线程只收包与分发；QIDO / EMR / QC / 模型调用走独立工作线程；界面刷新统一 `SwingUtilities.invokeLater`，**不在 EDT 上做网络 IO**。

## 批次状态机

`待复核 → 证据已取回 → 已判定 → 待确认 → 已确认 → 已提交`，合法迁移表显式枚举；跳跃 / 逆向迁移抛 `STATE_ILLEGAL_TRANSITION` 并记原状态与目标状态，**不静默改数据**。模型任何路径都不能把状态推到「已确认」——只有人工采纳 / 驳回动作可推进。

## 关键不变量

1. **幂等键**：`sha256(visit_no|field|evidence_hash)`，5xx 与超时退避重试时携带同一键，服务端不产生第二条复核记录。
2. **证据哈希**：字段顺序无关的稳定哈希，是对照表证据出处与 QC 回写 `evidence_hash` 的唯一来源。
3. **并发与重放防护**：MLLP 重投按 `MSH-10` 消息控制号去重；`BatchResume` 从最后成功批次重放，不重复写。
4. **状态校验**：提交闸门前置校验 + 网关层二次闸门；降级态直接拒绝且不发请求。
5. **失败不静默改数据**：单源不可达标 `unreachable` 并继续；字典不可用显式 `dictionary_unavailable`；脏数据产出可读错误码而非半条落库。

## 审计链

`audit_event` 只追加（append-only，无更新 / 删除接口），事件类型含批次创建 / 证据取回 / 判定生成 / 人工确认 / 提交 / 导出 / 降级 / 模型调用；**导出动作本身也生成一条事件**。日志与 SQLite 只落去标识字段，不落姓名 / 身份证 / 联系方式。
