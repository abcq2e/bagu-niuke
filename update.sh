#!/bin/bash
# ============================================
# 千 AI Agent - 增量更新脚本
# 仅重新构建有变更的服务，不需要重启数据库
# 用法: ./update.sh [backend|frontend|all]
# ============================================
set -e

TARGET=${1:-all}

update_backend() {
    echo "🔨 重新构建后端..."
    docker-compose -f docker-compose.prod.yml build qian-backend
    echo "🔄 重启后端服务..."
    docker-compose -f docker-compose.prod.yml up -d --no-deps qian-backend
    echo "✅ 后端更新完成"
}

update_frontend() {
    echo "🔨 重新构建前端..."
    docker-compose -f docker-compose.prod.yml build qian-frontend
    echo "🔄 重启前端服务..."
    docker-compose -f docker-compose.prod.yml up -d --no-deps qian-frontend
    echo "✅ 前端更新完成"
}

case $TARGET in
    backend)
        echo "=========================================="
        echo "  更新后端服务"
        echo "=========================================="
        update_backend
        ;;
    frontend)
        echo "=========================================="
        echo "  更新前端服务"
        echo "=========================================="
        update_frontend
        ;;
    all)
        echo "=========================================="
        echo "  更新所有应用服务"
        echo "=========================================="

        # 拉取最新代码
        if [ -d .git ]; then
            echo "📦 拉取最新代码..."
            git pull origin master
        fi

        update_backend
        update_frontend

        echo ""
        echo "🎉 全部更新完成！"
        ;;
    *)
        echo "用法: ./update.sh [backend|frontend|all]"
        exit 1
        ;;
esac

echo ""
echo "📊 当前服务状态:"
docker-compose -f docker-compose.prod.yml ps
