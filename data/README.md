# data/ 数据说明

本目录只放**运行时产物**，已被 `.gitignore` 排除（`data/*` + `!data/README.md`），发布到仓库时只保留本说明。

| 子目录 | 内容 | 来源与许可 | 字段口径 |
|---|---|---|---|
| `emr/` | 240 条合成 EMR / 病案首页编码样例 | 固定随机种子 `20261109` 本地合成；合成数据无隐私限制 | `visit_no` / `patient_id` / `main_diagnosis{code,name}` / `other_diagnosis[]` / `procedure{code,name}` / `gender` / `age` / `admission_time` / `discharge_time`，与 spec.md §2.3 对齐 |
| `dicom/` | 120 份合成 DICOM JSON 检查元数据 | 同上；字段形态参照 DICOM JSON PS3.18 F.2 | 仅 10 个证据 tag，不含像素数据、不取整包 |
| `hl7/` | 60 条合成 `ORU^R01` 报文（含转义 / 重复变体） | 同上；协议形态参照 HL7 v2 公开样例 | `MSH-10` / `PID-3` / `PV1-19` / `OBR-4` / `OBX-5` / `OBX-14` / `OBR-25` |
| `public/` | 公开样例报文与公开影像元数据样例 | 可访问时脚本化下载；断网时复用 `scripts/seed/` 随仓库样例。**仅用于协议侧对照** | 不含任何真实患者明细 |
| `out/` | 离线回放产物 `replay-table.csv` | 由 `replay` 子命令真实运行生成 | 来源系统 / 字段 / 取值 / 哈希 |
| `manifest.json` | 条数 + 逐文件 sha256 + 总 sha256 | 由 `scripts/fetch-public-data.sh` 生成 | 两次执行逐字节一致（幂等） |

**诚实标注**：本目录**不含**真实医院数据，也**不含**官方 ICD 明细字典。编码存在性判定在本地显式降级为 `dictionary_unavailable`；官方字典只做一次连通性验证（`NHSA_DICT_URL` / `NHSA_DICT_TOKEN` 未配置时标 `not_configured`）。合成编码仅用于结构校验与归一链路验证，**不得当作真实编码使用**。

重新生成：`bash scripts/fetch-public-data.sh`（可重跑、断网降级、退出码 0）。
