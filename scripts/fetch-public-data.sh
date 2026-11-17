#!/usr/bin/env bash
# 公开数据获取 + 固定种子合成 + 完整性校验：可重跑、断网降级、幂等。
# 产物落 data/（被 .gitignore 排除），发布时只保留 data/README.md。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$ROOT/data"
PUBLIC="$DATA/public"
SEED="$ROOT/scripts/seed"

echo "数据目录：$DATA"

# ---- ① 公开样例下载（可访问时）；已存在即跳过，保证二次执行幂等 ----
mkdir -p "$PUBLIC"
DOWNLOAD_OK=0
if command -v curl >/dev/null 2>&1; then
  if [ ! -f "$PUBLIC/hl7-oru-sample.hl7" ]; then
    if curl -fsS --max-time 5 \
        -o "$PUBLIC/hl7-oru-sample.hl7" \
        "https://raw.githubusercontent.com/LinuxForHealth/hl7v2-fhir-converter/master/src/test/resources/hl7/ORU_R01.hl7" \
        >/dev/null 2>&1; then
      DOWNLOAD_OK=1
    fi
  else
    DOWNLOAD_OK=1
  fi
fi
if [ "$DOWNLOAD_OK" -eq 0 ]; then
  # 断网降级：复用仓库内已带样例，退出码仍为 0
  cp "$SEED/hl7-public-sample.hl7" "$PUBLIC/hl7-oru-sample.hl7"
  cp "$SEED/dicom-public-sample.json" "$PUBLIC/dicom-study-sample.json"
  echo "降级：使用随仓库样例（公开站点不可达，协议侧对照样例来自 scripts/seed/）"
else
  [ -f "$PUBLIC/dicom-study-sample.json" ] || cp "$SEED/dicom-public-sample.json" "$PUBLIC/dicom-study-sample.json"
  echo "公开样例已就绪：${PUBLIC} （已存在则复用，保证幂等）"
fi

# ---- ② 固定种子合成 EMR / 病案首页编码、DICOM JSON 检查元数据、HL7 ORU^R01 ----
python3 - "$DATA" <<'PY'
import hashlib, json, os, random, shutil, sys

data = sys.argv[1]
random.seed(20261109)  # 固定随机种子：两次执行产物逐字节一致
EMR_N, DICOM_N, HL7_N = 240, 120, 60

for sub in ("emr", "dicom", "hl7", "out"):
    os.makedirs(os.path.join(data, sub), exist_ok=True)
for sub in ("emr", "dicom", "hl7"):
    d = os.path.join(data, sub)
    for f in os.listdir(d):
        p = os.path.join(d, f)
        if os.path.isfile(p):
            os.remove(p)

icd10 = ["J18900", "J44900", "I50900", "E11900", "K80900", "N39000", "M54500", "C34900",
         "I63900", "J45900", "U07100", "A09900"]
icd9 = ["475100", "332400", "885600", "033100", "541200", "882700"]
names = ["社区获得性肺炎", "慢性阻塞性肺疾病", "心力衰竭", "2 型糖尿病", "胆囊结石",
         "泌尿系感染", "腰椎间盘疾患", "肺恶性肿瘤", "脑梗死", "支气管哮喘",
         "新型冠状病毒感染", "感染性腹泻"]
proc_names = ["腹腔镜阑尾切除术", "胸腔镜下肺楔形切除", "头颅 CT 平扫", "腰椎穿刺",
              "腹腔镜胆囊切除", "胸部 CT 增强"]
modalities = ["CT", "MR", "CR", "US", "DX"]
sites = ["胸部", "头颅", "腹部", "腰椎", "盆腔"]
conclusions = ["右下肺实变影，考虑感染性病变", "未见明显异常", "术前已诊断右下肺炎，术后复查吸收",
               "左下肺少许纤维条索", "双肺纹理增粗，未见明确实变", "胆囊壁增厚，考虑慢性胆囊炎"]

visits = []
for i in range(1, EMR_N + 1):
    visit = "VISIT-%06d" % i
    visits.append(visit)
    record = {
        "visit_no": visit,
        "patient_id": "PID-%06d" % i,
        "main_diagnosis": {"code": icd10[i % len(icd10)], "name": names[i % len(names)]},
        "other_diagnosis": [{"code": icd10[(i + 3) % len(icd10)], "name": names[(i + 3) % len(names)]}],
        "procedure": {"code": icd9[i % len(icd9)], "name": proc_names[i % len(proc_names)]},
        "gender": "男" if i % 2 else "女",
        "age": 18 + (i * 7) % 70,
        "admission_time": "2026%02d%02d090000" % (1 + i % 12, 1 + i % 28),
        "discharge_time": "2026%02d%02d160000" % (1 + i % 12, 3 + i % 25)
    }
    with open(os.path.join(data, "emr", "emr_%04d.json" % i), "w", encoding="utf-8") as f:
        json.dump(record, f, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def el(vr, value):
    return {"vr": vr, "Value": [value]}


for i in range(1, DICOM_N + 1):
    visit_idx = (i - 1) % EMR_N + 1
    study = {
        "00100020": el("LO", "PID-%06d" % visit_idx),
        "0020000D": el("UI", "1.2.840.10008.5.1.4.%d.%d" % (2026, i)),
        "0020000E": el("UI", "1.2.840.10008.5.1.4.%d.%d.1" % (2026, i)),
        "00080050": el("SH", "ACC-%06d" % i),
        "00200010": el("SH", "STUDY-%06d" % i),
        "0008103E": el("LO", "%s %s 平扫" % (sites[i % len(sites)], modalities[i % len(modalities)])),
        "00080060": el("CS", modalities[i % len(modalities)]),
        "00080020": el("DA", "2026%02d%02d" % (1 + i % 12, 1 + i % 28)),
        "00080030": el("TM", "10%02d00" % (i % 60)),
        "00081032": {"vr": "SQ", "Value": [{"00080100": el("SH", icd9[i % len(icd9)]),
                                             "00080102": el("SH", "医保版操作编码"),
                                             "00080104": el("LO", proc_names[i % len(proc_names)])}]}
    }
    with open(os.path.join(data, "dicom", "study_%04d.json" % i), "w", encoding="utf-8") as f:
        json.dump(study, f, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

for i in range(1, HL7_N + 1):
    visit = visits[i - 1]
    control = "MSG%06d" % i
    conclusion = conclusions[i % len(conclusions)]
    # 转义与重复样例：第 7 条含 \F\ 转义，第 11 条含 ~ 重复
    if i == 7:
        conclusion = "右下肺\\F\\左下肺 对照，考虑感染性病变"
    if i == 11:
        conclusion = "右下肺实变影~左下肺少许渗出"
    day, month = 1 + i % 28, 1 + i % 12
    msh = "MSH|^~\\&|RIS|HOSP|EMR|HOSP|2026%02d%02d101500||ORU^R01|%s|P|2.5" % (month, day, control)
    pid = "PID|1||PID-%06d||去标识患者|U|19800101|%s" % (i, "M" if i % 2 else "F")
    # 显式按 1-based 字段号构造，避免数竖线出错：PV1-19 住院号 / OBR-25 状态 / OBX-14 报告时间
    pv1_fields = ["PV1"] + [""] * 20
    pv1_fields[1], pv1_fields[2], pv1_fields[3] = "1", "I", "WARD^ROOM^BED"
    pv1_fields[19] = visit
    obr_fields = ["OBR"] + [""] * 25
    obr_fields[1] = "1"
    obr_fields[2] = "ORD-%06d" % i
    obr_fields[3] = "SRV-%06d" % i
    obr_fields[4] = "%s %s^检查" % (sites[i % len(sites)], modalities[i % len(modalities)])
    obr_fields[7] = "2026%02d%02d101500" % (month, day)
    obr_fields[25] = "F"
    obx_fields = ["OBX"] + [""] * 14
    obx_fields[1], obx_fields[2], obx_fields[3] = "1", "TX", "REPORT^影像报告"
    obx_fields[5] = conclusion
    obx_fields[11] = "F"
    obx_fields[14] = "2026%02d%02d103000" % (month, day)
    message = "\r".join([msh, pid, "|".join(pv1_fields), "|".join(obr_fields), "|".join(obx_fields)])
    with open(os.path.join(data, "hl7", "oru_%04d.hl7" % i), "w", encoding="utf-8", newline="") as f:
        f.write(message)

# ---- ③ 逐个完整性校验 + manifest（条数 + 每个文件 sha256 + 总 sha256） ----
def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

manifest = {"schema_version": 1, "generator": "scripts/fetch-public-data.sh", "seed": 20261109,
            "counts": {}, "files": {}, "sample_visit_no": visits[0], "visits": visits[:20]}
for sub in ("emr", "dicom", "hl7"):
    d = os.path.join(data, sub)
    files = sorted(os.listdir(d))
    manifest["counts"][sub] = len(files)
    for name in files:
        p = os.path.join(d, name)
        manifest["files"][sub + "/" + name] = digest(p)
        # 完整性校验：JSON 可解析 / HL7 至少含 MSH 段
        if sub in ("emr", "dicom"):
            with open(p, "r", encoding="utf-8") as f:
                json.load(f)
        else:
            with open(p, "r", encoding="utf-8") as f:
                assert f.read().startswith("MSH"), "HL7 样例缺 MSH 段：" + name
concat = "".join(k + "=" + v for k, v in sorted(manifest["files"].items()))
manifest["total_sha256"] = hashlib.sha256(concat.encode("utf-8")).hexdigest()
public_files = sorted(os.listdir(os.path.join(data, "public")))
manifest["public_samples"] = len(public_files)
with open(os.path.join(data, "manifest.json"), "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, sort_keys=True, indent=2)

print("合成完成：EMR %d 条 / DICOM JSON %d 份 / HL7 %d 条 / 公开样例 %d 份" %
      (EMR_N, DICOM_N, HL7_N, len(public_files)))
print("manifest total_sha256 = %s" % manifest["total_sha256"])
PY

echo "产物清单：data/manifest.json（条数 + 逐文件 sha256 + 总 sha256）"
