# 输出样例

> 第一节是 `replay` 的真实命令产物节选；复核记录导出、建议文本两节是按 `ReviewRecordExporter` 字段口径书写的格式样例，取值为示例，不冒充实测数据。实测截图见 `docs/screenshots/`。

## 离线回放对照表草案（真实命令产物）

```bash
java -jar target/record-coding-evidence.jar replay --data data --visit VISIT-000001
```

```csv
visit_no,source_system,field,field_locator,value,hash8
VISIT-000001,PACS·QIDO-RS,00100020,00100020,PID-000001,93016ae0
VISIT-000001,PACS·QIDO-RS,0020000D,0020000D,1.2.840.10008.5.1.4.2026.1,fc2e921b
VISIT-000001,PACS·QIDO-RS,00081032,00081032,332400^胸腔镜下肺楔形切除,f3d05c72
VISIT-000001,RIS·HL7-MLLP,report_conclusion,OBX-5,术前已诊断右下肺炎，术后复查吸收,32a396fb
```

完整产物：`data/out/replay-table.csv`（本次实测 1680 行 = 120 份 DICOM JSON × 10 tag + 60 条 HL7 × 8 角色，每行带来源系统 / 字段定位 / 取值 / 哈希）。上列为该文件真实节选行，哈希为程序实测输出。

## 复核记录导出 CSV

```csv
visit_no,home_code,suggested_codes,verdict,attribution,evidence_refs,confirmed_by,confirmed_at
VISIT-000001,J18900,J18900-建议复核,conflict,side_missing,RIS·HL7-MLLP:OBX-5:4a10c7e2,编码员A,2026-11-15T09:20:31Z
```

## 可粘贴编码修改建议文本

```
住院号 VISIT-000001 编码复核意见（批次 B0001）
说明：以下为待确认建议，编码员逐条核对后再录入病案系统。
1. 首页编码 J18900 → 建议复核编码 J18900-建议复核；判定：冲突；原因：侧别缺失
   证据出处：RIS·HL7-MLLP 报告结论（哈希 4a10c7e2）
   状态：待确认；请核对后决定是否采纳。
本工具不给出诊断或治疗建议，编码结论由编码员确认并承担责任。
```

## 实测截图

见 `docs/screenshots/`：`terminal-replay.png`（终端回显）、`main-window.png`（界面渲染）、`export-sample.png`（导出片段）。三者均由当日真实运行实例采集，说明见 README「实测证据」小节。
