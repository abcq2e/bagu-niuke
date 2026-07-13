// ============================================
// Token 存取工具函数（前端）
// ============================================

const TOKEN_KEY = 'auth_token'
const NICKNAME_KEY = 'auth_nickname'

/** 保存登录信息到 localStorage */
export const saveAuth = (token, nickname) => {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NICKNAME_KEY, nickname)
}

/** 获取 Token */
export const getToken = () => {
  return localStorage.getItem(TOKEN_KEY)
}

/** 获取当前用户昵称 */
export const getNickname = () => {
  return localStorage.getItem(NICKNAME_KEY)
}

/** 是否已登录（是否有 Token） */
export const isLoggedIn = () => {
  return !!getToken()
}

/** 清除登录信息（退出登录） */
export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NICKNAME_KEY)
}
