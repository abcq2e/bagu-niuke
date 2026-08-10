<template>
  <div class="home">
    <!-- 导航 -->
    <nav class="nav">
      <div class="nav-brand">
        <span class="nav-logo">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/><path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"/></svg>
        </span>
        <span class="nav-title">AI 技术面试官</span>
      </div>
      <a href="https://github.com" target="_blank" class="nav-link">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
        GitHub
      </a>
    </nav>

    <!-- 主内容 -->
    <div class="hero">
      <div class="hero-badge">
        <span class="badge-dot"></span>
        阿里 · 腾讯 · 字节 面试风格
      </div>

      <h1 class="hero-title">
        直面最<span class="text-gradient">严厉</span>的<br/>技术面试
      </h1>

      <p class="hero-desc">
        不再是简单的知识点问答，而是一场真正的大厂级高压面试。
        AI 面试官会追问底层原理、质疑你的回答、随时抛出场景题。
      </p>

      <button class="cta-btn" @click="$router.push('/chat')">
        <span>开始面试</span>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
      </button>

      <!-- 特性卡片 -->
      <div class="feature-grid">
        <div class="feature-card">
          <div class="fc-icon fc-icon-purple">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          </div>
          <h3>源码级追问</h3>
          <p>不满足于"会用"，追问 AQS 实现、Bean 生命周期、JVM 内存屏障等底层细节</p>
        </div>
        <div class="feature-card">
          <div class="fc-icon fc-icon-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
          </div>
          <h3>实时出题</h3>
          <p>随时中断追问，抛出高难度代码题和系统设计题，验证你的真实编码能力</p>
        </div>
        <div class="feature-card">
          <div class="fc-icon fc-icon-green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/></svg>
          </div>
          <h3>面试评分</h3>
          <p>面试结束给出综合评分和详细改进清单，帮你明确每个知识薄弱点</p>
        </div>
        <div class="feature-card">
          <div class="fc-icon fc-icon-orange">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <h3>批判性审视</h3>
          <p>对每个回答追问"为什么这样设计""有没有更好方案""生产环境会踩什么坑"</p>
        </div>
      </div>

      <!-- 考察范围 -->
      <div class="scope-section">
        <h2 class="scope-title">考察范围</h2>
        <div class="scope-tags">
          <span class="scope-tag">Java 并发编程</span>
          <span class="scope-tag">synchronized 原理</span>
          <span class="scope-tag">AQS 框架</span>
          <span class="scope-tag">线程池调优</span>
          <span class="scope-tag">Spring IoC/AOP</span>
          <span class="scope-tag">Bean 生命周期</span>
          <span class="scope-tag">事务源码</span>
          <span class="scope-tag">JVM 调优</span>
          <span class="scope-tag">GC 算法</span>
          <span class="scope-tag">数据结构</span>
          <span class="scope-tag">系统设计</span>
          <span class="scope-tag">分布式架构</span>
        </div>
      </div>
    </div>

    <!-- 底部 -->
    <footer class="footer">
      <span>Powered by DeepSeek + Spring AI + DashScope</span>
    </footer>
  </div>
</template>

<script setup>
import { useHead } from '@unhead/vue'
useHead({ title: 'AI 技术面试官', meta: [{ name: 'description', content: '大厂级严厉技术面试' }] })
</script>

<style scoped>
/* ===== 基础 ===== */
.home {
  min-height: 100vh;
  background: #fafbfc;
  color: #1a1a2e;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ===== 导航 ===== */
.nav {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 40px; max-width: 1100px; margin: 0 auto;
}
.nav-brand { display: flex; align-items: center; gap: 10px; }
.nav-logo { display: flex; align-items: center; color: #4f46e5; }
.nav-title { font-size: 17px; font-weight: 700; color: #1a1a2e; }
.nav-link {
  display: flex; align-items: center; gap: 6px; color: #64748b; font-size: 14px;
  text-decoration: none; padding: 8px 16px; border-radius: 8px; transition: all .2s;
  border: 1px solid #e2e8f0;
}
.nav-link:hover { color: #1a1a2e; border-color: #cbd5e1; background: #f8fafc; }

/* ===== 主内容 ===== */
.hero { text-align: center; padding: 80px 24px 80px; max-width: 960px; margin: 0 auto; }

/* 顶部 badge */
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px; padding: 6px 16px;
  border-radius: 20px; border: 1px solid #e0e7ff; background: #eef2ff;
  font-size: 13px; color: #4f46e5; margin-bottom: 32px; font-weight: 500;
}
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #4f46e5; animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .4; } }

/* 标题 */
.hero-title {
  font-size: 56px; font-weight: 800; color: #1a1a2e; margin: 0 0 24px;
  letter-spacing: -1.5px; line-height: 1.18;
}
.text-gradient {
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a855f7);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
}

.hero-desc {
  font-size: 17px; color: #64748b; line-height: 1.8; margin: 0 auto 40px; max-width: 540px;
}

/* CTA 按钮 */
.cta-btn {
  display: inline-flex; align-items: center; gap: 10px; padding: 16px 36px;
  background: #4f46e5; border: none; border-radius: 14px;
  color: #fff; font-size: 17px; font-weight: 600; cursor: pointer;
  transition: all .25s; box-shadow: 0 4px 24px rgba(79,70,229,.25);
  margin-bottom: 80px; font-family: inherit;
}
.cta-btn:hover { background: #4338ca; transform: translateY(-2px); box-shadow: 0 8px 32px rgba(79,70,229,.35); }
.cta-btn:active { transform: translateY(0); }

/* ===== 特性卡片 ===== */
.feature-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  margin-bottom: 80px; text-align: left;
}
.feature-card {
  background: #fff; border: 1px solid #e8ecf1; border-radius: 16px;
  padding: 28px 24px; transition: all .25s;
}
.feature-card:hover {
  border-color: #d0d5ff; box-shadow: 0 8px 32px rgba(0,0,0,.06);
  transform: translateY(-2px);
}
.fc-icon {
  width: 44px; height: 44px; border-radius: 12px; display: flex;
  align-items: center; justify-content: center; margin-bottom: 16px;
}
.fc-icon-purple { background: #eef2ff; color: #6366f1; }
.fc-icon-blue { background: #eff6ff; color: #3b82f6; }
.fc-icon-green { background: #ecfdf5; color: #10b981; }
.fc-icon-orange { background: #fffbeb; color: #f59e0b; }
.feature-card h3 { font-size: 15px; font-weight: 700; color: #1a1a2e; margin: 0 0 8px; }
.feature-card p { font-size: 13px; color: #64748b; line-height: 1.7; margin: 0; }

/* ===== 考察范围 ===== */
.scope-section { margin-bottom: 40px; }
.scope-title { font-size: 18px; font-weight: 700; color: #1a1a2e; margin: 0 0 20px; }
.scope-tags { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.scope-tag {
  padding: 7px 16px; border-radius: 8px; font-size: 13px; color: #475569;
  background: #fff; border: 1px solid #e2e8f0; transition: all .2s;
}
.scope-tag:hover { color: #4f46e5; border-color: #c7d2fe; background: #eef2ff; }

/* ===== 底部 ===== */
.footer { text-align: center; padding: 32px 24px; }
.footer span { font-size: 13px; color: #94a3b8; }

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .feature-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-title { font-size: 40px; }
}
@media (max-width: 560px) {
  .feature-grid { grid-template-columns: 1fr; }
  .hero-title { font-size: 30px; }
  .hero-desc { font-size: 15px; }
  .nav { padding: 16px 20px; }
  .cta-btn { padding: 14px 28px; font-size: 15px; }
}
</style>
