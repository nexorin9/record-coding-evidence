# 案例集

按 `domain/cases/README.md` 同构格式在项目内新建：`case_id` / `input` / `expect` / `basis` / `confidence`，每条 `basis` 指向官方文件条目（`domain/dictionaries/医保编码规则_ICD与药品.md`）或产品规格条款。

覆盖六类边界：**编码位数错**（C01–C03、C05）、**字典不存在**（C06，本地字典层必须降级）、**侧别缺失**（C09）、**报告陈旧**（C07）、**证据缺失**（C08）、**否定表述**（C11，另含既往 C12、部位缺失 C10、置信度不足 C13）。

执行：`java -jar target/record-coding-evidence.jar case-suite --cases cases`
输出逐条命中与不符清单；全命中才返回退出码 0。
