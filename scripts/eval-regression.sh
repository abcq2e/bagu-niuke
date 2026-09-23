#!/usr/bin/env bash
#
# 离线评测回归检查。
#
# 退出码语义（由 EvalReport.exitCode() 决定，这里只负责透传）：
#   0 = 无回归
#   1 = 有回归（分数下降超过阈值）
#
# 前置条件：需要真实 API Key + PostgreSQL(PGVector) + Redis 都在跑，
# 否则应用起不来，本脚本会以非 0 退出并提示。
set -uo pipefail

cd "$(dirname "$0")/.." || exit 9

if [ ! -f .env ]; then
  echo "❌ 缺少 .env —— 评测需要真实 API Key，请先按 .env.template 配置" >&2
  exit 9
fi

# ⚠️ 这里**刻意不** source .env。
#    .env 是给**容器化部署**用的：里面的 REDIS_HOST=redis 是 compose 的服务名。
#    本地是直接用 maven 跑应用（Redis 在 localhost:16379），一旦把 .env 注入环境，
#    应用就会去连一个解析不了的主机名 `redis`，反而跑不起来。
#    本地靠 application-local.yml 提供密钥、靠 application.yml 的默认值提供地址端口；
#    CI 里没有 application-local.yml，由 workflow 把变量注入进程环境
#    （见 .github/workflows/eval.yml 的「准备 .env」一步，它会同时写入 $GITHUB_ENV）。
#    这里的检查只是确保「本地跑的时候密钥文件确实存在」。

echo "════ 开始离线评测回归检查 ════"
echo "（这会真实调用 LLM，消耗 token 额度）"
echo

# -Peval 激活 eval profile（见 pom.xml 的 <profiles>）
./mvnw -q spring-boot:run -Peval
exit_code=$?

echo
if [ "$exit_code" -eq 0 ]; then
  echo "✅ 无回归"
else
  echo "🔴 检测到回归（退出码 $exit_code），请查看上方报告与 logs/eval/ 下的报告文件" >&2
fi

exit "$exit_code"
