#!/usr/bin/env bash
# 可重复验证：装依赖 → 跑测试 → 跑离线回放 → 打印核对结论
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# 跟随 Maven 实际使用的 JDK，避免写死某台机器的 JDK 安装路径
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  DETECTED="$(mvn -v 2>/dev/null | sed -n 's/.*runtime: //p' | sed 's/,.*//' | head -1)"
  if [ -n "$DETECTED" ] && [ -x "$DETECTED/bin/java" ]; then
    export JAVA_HOME="$DETECTED"
  else
    echo "未找到可执行的 JDK，请显式设置 JAVA_HOME" >&2
    exit 1
  fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "== 1/4 生成公开数据与合成样例 =="
bash scripts/fetch-public-data.sh

echo "== 2/4 全量单元测试 =="
mvn -q clean test

echo "== 3/4 打包可运行单 jar =="
mvn -q -DskipTests package

echo "== 4/4 离线回放与导出核对 =="
java -jar target/record-coding-evidence.jar replay --data data --visit VISIT-000001 > /tmp/rce-replay.out
head -3 /tmp/rce-replay.out
tail -1 /tmp/rce-replay.out
test -f data/out/replay-table.csv && echo "replay-table.csv 已生成"
echo "验证完成：测试 + 打包 + 离线回放全部通过"
