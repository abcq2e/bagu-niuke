import axios from 'axios'
import { getToken, clearAuth } from '../utils/auth'

// API 基础 URL（开发走 Vite 代理，生产走 Nginx 代理）
const API_BASE_URL = '/api'

// Axios 实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 请求拦截器 —— 自动往每个请求头加上 Authorization
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 —— 401 时自动清除登录状态并跳转
request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// SSE 连接封装
// EventSource 不支持自定义请求头，所以 token 通过 URL 参数传递
export const connectSSE = (url, params) => {
  const token = getToken()
  const finalParams = { ...params }  // 🔴 不修改调用者的对象
  if (token) {
    finalParams.token = token
  }
  const queryString = Object.keys(finalParams)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(finalParams[key])}`)
    .join('&')
  return new EventSource(`${API_BASE_URL}${url}?${queryString}`)
}

// ===== 登录注册 API =====

export const login = (username, password) => {
  return request.post('/user/login', { username, password })
}

export const register = (username, password, nickname) => {
  return request.post('/user/register', { username, password, nickname })
}

// ===== AI 对话 API =====

// 统一对话（流式 + RAG + 工具调用 + 记忆）
export const chat = (message, chatId) => {
  return connectSSE('/ai/chat', { message, chatId })
}

// Agent 自主规划对话（ReAct 循环 + 工具调用）
export const agentChat = (message) => {
  return connectSSE('/ai/agent/chat', { message })
}

// 获取会话列表
export const getConversations = () => {
  return request.get('/ai/conversations')
}

// 获取指定会话的历史消息
export const getConversationMessages = (chatId) => {
  return request.get(`/ai/conversations/${chatId}`)
}

// 删除指定会话
export const deleteConversation = (chatId) => {
  return request.delete(`/ai/conversations/${chatId}`)
}

// 重命名会话标题
export const renameConversation = (chatId, title) => {
  return request.put(`/ai/conversations/${chatId}/title`, null, { params: { title } })
}

// ===== 错题复习 API =====

// 错题复习对话（SSE 流式）
export const reviewChat = (message, chatId, sourceChatId) => {
  return connectSSE('/ai/review/chat', { message, chatId, sourceChatId })
}

// 获取错题池预览
export const getReviewPool = (chatId) => {
  return request.get(`/ai/review/pool/${chatId}`)
}

// ===== 个性化画像 API =====

// 获取用户能力画像
export const getProfile = (chatId) => {
  return request.get(`/ai/quiz/profile/${chatId}`)
}

// 获取文字版考察总结
export const getProfileSummary = (chatId) => {
  return request.get(`/ai/quiz/profile/${chatId}/summary`)
}

// 重置画像
export const resetProfile = (chatId) => {
  return request.post(`/ai/quiz/profile/${chatId}/reset`)
}

// 强制清理跨方向弱点评
export const cleanupProfile = (chatId) => {
  return request.post(`/ai/quiz/profile/${chatId}/cleanup`)
}

export default { login, register, chat, agentChat, getConversations, getConversationMessages, deleteConversation, renameConversation, getProfile, getProfileSummary, resetProfile }
