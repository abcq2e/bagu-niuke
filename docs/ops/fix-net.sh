#!/bin/bash
# ============================================
# qian 服务器 · 网络自修复脚本
# 用途：每次 docker compose 重建 backend/frontend 后执行，
#       把后端依赖(pgvector/neo4j/mysql/redis)和前端全部并入
#       qian-ai-agent_qian-net 网络，避免 UnknownHost / 502。
# 用法：把本文件放到服务器 /opt/qian-ai-agent/fix-net.sh
#       chmod +x fix-net.sh && ./fix-net.sh
# ============================================
set -u
NET=qian-ai-agent_qian-net

# 网络必须存在（先 compose up 一次才有）
if ! docker network inspect "$NET" >/dev/null 2>&1; then
  echo "❌ 网络 $NET 不存在，请先执行: bash scripts/update.sh backend"
  exit 1
fi

echo "== 将 pgvector / neo4j / frontend 并入 $NET =="
for c in qian-pgvector qian-neo4j qian-frontend; do
  if docker network inspect "$NET" --format '{{range .Containers}}{{.Name}} {{end}}' | grep -qw "$c"; then
    echo "  = $c 已接入"
  else
    docker network connect "$NET" "$c" && echo "  + $c 已接入"
  fi
done

echo "== mysql / redis 以后端期望的名字(qian-mysql/qian-redis)接入 =="
for pair in "mysql:qian-mysql" "redis:qian-redis"; do
  real="${pair%%:*}"; alias="${pair##*:}"
  if docker network inspect "$NET" --format '{{range .Containers}}{{.Name}} {{end}}' | grep -qw "$alias"; then
    echo "  = $real 已以 $alias 接入"
  else
    docker network disconnect "$NET" "$real" 2>/dev/null
    docker network connect --alias "$alias" "$NET" "$real" && echo "  + $real 已以别名 $alias 接入"
  fi
done

echo "== 重启应用容器让解析生效 =="
docker restart qian-backend qian-frontend

echo "✅ done。验证:"
echo "   curl -s http://localhost:8123/api/actuator/health | head -c 120"
echo "   (非 502 即可；DOWN 通常只是 deepSeek key 未生效，不影响登录)"
