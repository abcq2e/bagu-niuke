<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { register as registerApi } from '../api'

useHead({ title: '注册 - AI 技术面试官' })

const router = useRouter()
const username = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')  // 🔴 替代 alert()

const register = async () => {
  errorMsg.value = ''
  successMsg.value = ''

  if (!username.value.trim()) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (username.value.trim().length < 3) {
    errorMsg.value = '用户名至少 3 个字符'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }
  if (password.value.length < 6) {
    errorMsg.value = '密码至少 6 个字符'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    const res = await registerApi(username.value.trim(), password.value, nickname.value.trim() || undefined)
    if (res.data.code === 0) {
      successMsg.value = '注册成功！正在跳转登录…'
      setTimeout(() => router.push('/login'), 800)
    } else {
      errorMsg.value = res.data.message || '注册失败'
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
  <div class="register-page">
    <div class="register-card">
      <div class="card-header">
        <span class="card-logo">🧠</span>
        <h2>注册 AI 技术面试官</h2>
        <p>创建你的面试训练账号</p>
      </div>

      <div class="card-body">
        <div class="input-group">
          <label>用户名 <span class="required">*</span></label>
          <input
            v-model="username"
            type="text"
            placeholder="至少 3 个字符"
            @keydown.enter="register"
            autocomplete="username"
          />
        </div>

        <div class="input-group">
          <label>昵称</label>
          <input
            v-model="nickname"
            type="text"
            placeholder="选填，面试官会这样称呼你"
            @keydown.enter="register"
          />
        </div>

        <div class="input-group">
          <label>密码 <span class="required">*</span></label>
          <input
            v-model="password"
            type="password"
            placeholder="至少 6 个字符"
            @keydown.enter="register"
            autocomplete="new-password"
          />
        </div>

        <div class="input-group">
          <label>确认密码 <span class="required">*</span></label>
          <input
            v-model="confirmPassword"
            type="password"
            placeholder="再次输入密码"
            @keydown.enter="register"
            autocomplete="off"
          />
        </div>

        <p class="error-msg">{{ errorMsg || ' ' }}</p>
        <p class="success-msg">{{ successMsg || ' ' }}</p>

        <button class="register-btn" @click="register" :disabled="loading">
          <span class="btn-text">{{ loading ? '注册中...' : '注 册' }}</span>
        </button>
      </div>

      <div class="card-footer">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
  padding: 24px;
}

.register-card {
  width: 100%;
  max-width: 420px;
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
  font-size: 48px;
  display: block;
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
  margin-bottom: 18px;
}

.input-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}

.required {
  color: #ef4444;
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
  margin: -4px 0 8px;
  min-height: 20px;
  line-height: 20px;
}

.success-msg {
  font-size: 13px;
  color: #10b981;
  margin: -4px 0 8px;
  min-height: 20px;
  line-height: 20px;
}

.register-btn {
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
  margin-top: 4px;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  display: inline-block;
  min-width: 80px;
  text-align: center;
}

.register-btn:hover:not(:disabled) {
  background: #4338ca;
}

.register-btn:disabled {
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
