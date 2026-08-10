<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { saveAuth } from '../utils/auth'
import { login as loginApi } from '../api'

useHead({ title: '登录 - AI 技术面试官' })

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

const login = async () => {
  errorMsg.value = ''

  if (!username.value.trim()) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true
  try {
    const res = await loginApi(username.value.trim(), password.value)
    if (res.data.code === 0) {
      saveAuth(res.data.data.token, res.data.data.nickname)
      router.push('/')
    } else {
      errorMsg.value = res.data.message || '登录失败'
    }
  } catch (e) {
    const status = e.response?.status
    if (status === 429) errorMsg.value = '请求过于频繁，请稍后再试'
    else if (status >= 500) errorMsg.value = '服务器错误，请稍后重试'
    else errorMsg.value = '网络错误，请检查网络连接后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="card-header">
        <span class="card-logo">
          <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="#4f46e5" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/><path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"/><path d="M17.599 6.5a3 3 0 0 0 .399-1.375"/><path d="M6.003 5.125A3 3 0 0 0 6.401 6.5"/></svg>
        </span>
        <h2>登录 AI 技术面试官</h2>
        <p>开启你的大厂级面试训练</p>
      </div>

      <div class="card-body">
        <div class="input-group">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            @keydown.enter="login"
            autocomplete="username"
          />
        </div>

        <div class="input-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            @keydown.enter="login"
            autocomplete="current-password"
          />
        </div>

        <p class="error-msg">{{ errorMsg || ' ' }}</p>

        <button class="login-btn" @click="login" :disabled="loading">
          <span class="btn-text">{{ loading ? '登录中...' : '登 录' }}</span>
        </button>
      </div>

      <div class="card-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 40px rgba(0, 0, 0, .06);
  overflow: hidden;
}

.card-header {
  text-align: center;
  padding: 40px 32px 0;
}

.card-logo {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
}

.card-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.card-header p {
  font-size: 14px;
  color: #94a3b8;
  margin: 0;
}

.card-body {
  padding: 32px;
}

.input-group {
  margin-bottom: 20px;
}

.input-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}

.input-group input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  font-size: 15px;
  color: #1a1a2e;
  outline: none;
  transition: border-color .2s, box-shadow .2s;
  font-family: inherit;
  box-sizing: border-box;
}

.input-group input::placeholder {
  color: #94a3b8;
}

.input-group input:focus {
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, .08);
}

.error-msg {
  font-size: 13px;
  color: #ef4444;
  margin: -8px 0 16px;
  min-height: 20px;        /* 固定占位，防止按钮跳动 */
  line-height: 20px;
  transition: color .15s;  /* 颜色平滑过渡 */
}

.login-btn {
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 12px;
  background: #4f46e5;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s;
  font-family: inherit;
  min-height: 48px;         /* 固定高度，防止 loading 时按钮变形 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  display: inline-block;
  min-width: 80px;          /* 固定最小宽度，文字变化不跳动 */
  text-align: center;
}

.login-btn:hover:not(:disabled) {
  background: #4338ca;
}

.login-btn:disabled {
  background: #a5b4fc;
  cursor: not-allowed;
}

.card-footer {
  text-align: center;
  padding: 0 32px 32px;
  font-size: 14px;
  color: #64748b;
}

.card-footer a {
  color: #4f46e5;
  font-weight: 600;
}

.card-footer a:hover {
  text-decoration: underline;
}
</style>
