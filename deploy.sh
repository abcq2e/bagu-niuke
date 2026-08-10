#!/bin/bash
# ============================================
# 千 AI Agent - 一键部署脚本
# 用法: chmod +x deploy.sh && ./deploy.sh
# ============================================
set -e

echo "=========================================="
echo "  千 AI Agent - 生产环境部署"
echo "=========================================="

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "❌ 错误: 未找到 .env 文件！"
    echo "请先执行: cp .env.production .env"
    echo "然后编辑 .env 填入真实的配置值"
    exit 1
fi

# 加载环境变量
set -a
source .env
set +a

# 检查必要的环境变量
REQUIRED_VARS=("MYSQL_PASSWORD" "NEO4J_PASSWORD" "PGVECTOR_PASSWORD" "SPRING_AI_OPENAI_API_KEY" "JWT_SECRET")
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ] || [[ "${!var}" == *"your-"* ]] || [[ "${!var}" == *"change-me"* ]]; then
        echo "❌ 错误: 环境变量 $var 未设置或仍为默认值，请在 .env 中配置"
        exit 1
    fi
done

echo "✅ 环境变量检查通过"

# 拉取最新代码（如果是从 Git 部署）
if [ -d .git ]; then
    echo ""
    echo "📦 拉取最新代码..."
    git pull origin master
fi

# 停止旧容器
echo ""
echo "🛑 停止旧容器..."
docker-compose -f docker-compose.prod.yml down

# 构建并启动所有服务
echo ""
echo "🔨 构建并启动所有服务..."
docker-compose -f docker-compose.prod.yml up -d --build

# 等待服务启动
echo ""
echo "⏳ 等待服务启动（30秒）..."
sleep 30

# 检查服务状态
echo ""
echo "📊 服务状态:"
docker-compose -f docker-compose.prod.yml ps

# 健康检查
echo ""
echo "🏥 健康检查..."

# 检查后端
if curl -s -f http://localhost:8123/api/actuator/health > /dev/null 2>&1; then
    echo "✅ 后端服务运行正常"
else
    echo "⚠️  后端服务可能还在启动中，请稍后检查: http://localhost:8123/api/actuator/health"
fi

# 检查前端
if curl -s -f http://localhost/ > /dev/null 2>&1; then
    echo "✅ 前端服务运行正常"
else
    echo "⚠️  前端服务可能还在启动中"
fi

echo ""
echo "=========================================="
echo "  🎉 部署完成！"
echo "  前端地址: http://<服务器IP>"
echo "  后端 API: http://<服务器IP>:8123/api"
echo "  健康检查: http://<服务器IP>:8123/api/actuator/health"
echo "  Neo4j Browser: http://<服务器IP>:7474"
echo "=========================================="
echo ""
echo "查看日志: docker-compose -f docker-compose.prod.yml logs -f"
