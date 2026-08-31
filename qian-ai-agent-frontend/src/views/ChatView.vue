<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header" @click="$router.push('/')">
        <span class="s-logo">
          <!-- brain circuit icon -->
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/>
            <path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/>
            <path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"/><path d="M17.599 6.5a3 3 0 0 0 .399-1.375"/><path d="M6.003 5.125A3 3 0 0 0 6.401 6.5"/><path d="M3.477 10.896a4 4 0 0 1 .585-.396"/><path d="M19.938 10.5a4 4 0 0 1 .585.396"/><path d="M6 18a4 4 0 0 1-1.967-.516"/><path d="M19.967 17.484A4 4 0 0 1 18 18"/>
          </svg>
        </span>
        <span class="s-title">{{ agentMode ? 'AI Agent' : 'AI 面试官' }}</span>
      </div>

      <div class="sidebar-body">
        <button class="new-chat-btn" @click="newChat">+ 新对话</button>

        <!-- 🆕 错题复习（独立区块） -->
        <div v-if="reviewConvVisible" class="review-sidebar-section">
          <div class="review-sidebar-label">错题复习</div>
          <div
            :class="['review-sidebar-card', { 'review-sidebar-active': reviewConvActive }]"
            @click="switchToReviewConv"
          >
            <div class="review-sidebar-icon">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            </div>
            <div class="review-sidebar-info">
              <div class="review-sidebar-title">错题复习</div>
              <div class="review-sidebar-meta">{{ reviewConvDirCount }} 方向 · {{ reviewConvTotalCount }} 道错题</div>
            </div>
            <span class="review-sidebar-badge">{{ reviewConvTotalCount }}</span>
          </div>
        </div>

        <div v-if="reviewConvVisible" class="review-sidebar-divider"></div>
        <div class="sidebar-section-label">面试对话</div>

        <!-- 会话列表 -->
        <div class="conv-list" v-if="conversations.length > 0">
          <div
            v-for="conv in conversations"
            :key="conv.chatId"
            :class="['conv-item', { 'conv-active': conv.chatId === chatId }]"
            @click="switchConversation(conv.chatId)"
          >
            <div class="conv-title" v-if="editingChatId !== conv.chatId" @dblclick.stop="startRename(conv)">{{ conv.title }}</div>
            <input
              v-else
              v-model="editTitle"
              class="title-input"
              @keydown.enter="confirmRename"
              @keydown.escape="cancelRename"
              @blur="confirmRename"
              @click.stop
            />
            <div class="conv-row">
              <span class="conv-meta">{{ conv.messageCount }} 条 · {{ formatTime(conv.lastModified) }}</span>
              <button class="conv-del" @click.stop="removeConversation(conv.chatId)" title="删除对话">×</button>
            </div>
          </div>
        </div>
        <p class="sidebar-hint" v-else>暂无对话记录</p>
      </div>

      <div class="sidebar-footer">
        <div class="mode-toggle" @click="agentMode = !agentMode">
          <span :class="['mode-label', { 'mode-active': !agentMode }]">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-2px;margin-right:3px"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            对话
          </span>
          <span class="mode-switch">
            <span class="mode-knob" :style="{ transform: agentMode ? 'translateX(100%)' : 'translateX(0)' }"></span>
          </span>
          <span :class="['mode-label', { 'mode-active': agentMode }]">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-2px;margin-right:3px"><circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/></svg>
            Agent
          </span>
        </div>
        <span class="s-model">DeepSeek · DashScope</span>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="main">
      <!-- 🆕 错题复习顶栏（极简精英蓝） -->
      <div v-if="reviewConvActive" class="review-topbar">
        <div class="review-topbar-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        </div>
        <span class="review-topbar-title">错题复习</span>
        <span v-if="reviewCurrentDir" class="review-topbar-dir">· {{ reviewCurrentDir }}</span>
        <span class="review-topbar-spacer"></span>
        <span class="review-topbar-stat">
          <span class="review-topbar-steps">
            <span v-for="i in reviewConvTotalCount" :key="'dot-'+i"
              :class="['review-topbar-dot', { done: i <= reviewAnsweredCount, current: i === reviewAnsweredCount + 1 }]"
            ></span>
          </span>
          <span class="review-topbar-count">{{ reviewAnsweredCount }}</span>
          <span class="review-topbar-total">/ {{ reviewConvTotalCount }}</span>
        </span>
      </div>

      <!-- 消息区 -->
      <div class="msg-area" ref="msgContainer">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0" class="welcome">
          <h2 v-if="reviewConvActive">错题复习</h2>
          <h2 v-else-if="!agentMode">面试开始，你准备好了吗？</h2>
          <h2 v-else>Agent 模式已就绪</h2>
          <p class="welcome-sub" v-if="reviewConvActive">按方向顺序考察错题，答对自动移除</p>
          <p class="welcome-sub" v-else-if="!agentMode">我会考察你的 Java 并发、Spring 框架、数据结构和系统设计能力</p>
          <p class="welcome-sub" v-else>我会自主规划、调用工具，逐步解决你的复杂问题</p>
          <div class="suggestions">
            <button v-for="s in (agentMode ? agentSuggestions : suggestions)" :key="s" @click="sendSuggestion(s)" :disabled="connecting" class="sug-btn">{{ s }}</button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.isUser ? 'msg-user' : 'msg-ai', { 'msg-same': i > 0 && msg.isUser === messages[i-1].isUser }]">
          <div class="msg-inner">
            <div class="msg-avatar" v-if="!msg.isUser && (i === 0 || messages[i-1].isUser)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="12" y1="16" x2="12" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>
            </div>
            <div class="msg-avatar-placeholder" v-if="!msg.isUser && i > 0 && !messages[i-1].isUser"></div>
            <div class="msg-body">
              <div class="msg-text" v-if="msg.isUser">{{ msg.content }}</div>
              <div class="msg-text msg-md" v-else v-html="msg.rendered || msg.content"></div>
              <!-- 错误重试按钮 -->
              <button
                v-if="!msg.isUser && isErrorMessage(msg.content)"
                class="retry-btn"
                @click="retryMessage(i)"
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                重试
              </button>
              <div class="msg-meta">{{ formatTime(msg.time) }}</div>
            </div>
            <div class="msg-avatar msg-avatar-user" v-if="msg.isUser && (i === 0 || !messages[i-1].isUser)">我</div>
            <div class="msg-avatar-placeholder" v-if="msg.isUser && i > 0 && messages[i-1].isUser"></div>
          </div>
        </div>

        <!-- 输入中 — 带阶段提示 -->
        <div v-if="connecting" class="msg msg-ai">
          <div class="msg-inner">
            <div class="msg-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="12" y1="16" x2="12" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>
            </div>
            <div class="msg-body">
              <div class="typing-status">
                <span class="typing-status-text">{{ connectingStatus }}</span>
                <div class="typing-dots"><span></span><span></span><span></span></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-wrap">
          <textarea
            ref="inputEl"
            v-model="input"
            @keydown.enter.exact.prevent="send"
            :disabled="connecting"
            maxlength="2000"
            placeholder="输入消息… (Enter 发送，Shift+Enter 换行)"
            rows="1"
            class="input-box"
          ></textarea>
          <button
            v-if="!connecting"
            @click="send"
            :disabled="!input.trim()"
            class="send-btn"
            title="发送"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>
          </button>
          <button
            v-else
            @click="stopGeneration"
            class="send-btn stop-btn"
            title="停止生成"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
          </button>
        </div>
        <p class="input-hint" v-if="!agentMode">AI 面试官可能会严厉追问，请认真作答</p>
        <p class="input-hint" v-else>Agent 模式：我会自主规划、逐步执行，复杂任务可能需要等待片刻</p>
      </div>

      <!-- 能力画像浮动按钮（可拖拽） -->
      <button
        v-if="!agentMode"
        class="ability-fab"
        :style="{ left: fabPos.x + 'px', top: fabPos.y + 'px' }"
        @pointerdown="onFabPointerDown"
        :class="{ 'fab-dragging': isDragging }"
        title="查看能力画像"
        touch-action="none"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
        <span class="fab-badge" v-if="weakCount > 0">{{ weakCount }}</span>
      </button>
    </main>

    <!-- 能力画像抽屉 -->
    <Transition name="overlay-fade">
      <div
        v-if="showAbilityPanel && !agentMode"
        class="ability-drawer-overlay"
        @click.self="showAbilityPanel = false"
      />
    </Transition>
    <div
      v-if="!agentMode"
      class="ability-drawer"
      :class="{ 'drawer-hidden': !showAbilityPanel }"
    >
      <AbilityPanel
        :topics="profileTopics"
        :current-topic="currentDirection"
        :chat-id="chatId"
        @close="showAbilityPanel = false"
        @refresh="loadProfile"
        @startReview="enterReviewMode"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, reactive, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { useHead } from '@unhead/vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { chat, agentChat, reviewChat, getConversations, getConversationMessages, deleteConversation, renameConversation, getProfile, getReviewPool } from '../api'
import AbilityPanel from '../components/AbilityPanel.vue'

useHead({ title: 'AI 面试官', meta: [{ name: 'description', content: '严厉的大厂技术面试官' }] })

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
})

/**
 * 流式场景下临时补全未闭合的 Markdown 结构，
 * 避免 marked 因半截文本生成错乱的 HTML。
 * 只在渲染副本上操作，不修改原始文本。
 */
const sanitizeForStreaming = (text) => {
  let result = text

  // 1. 补全未闭合的围栏代码块 (```)
  const fenceMatches = result.match(/^```/gm)
  if (fenceMatches && fenceMatches.length % 2 !== 0) {
    result += '\n```'
  }

  // 2. 补全未闭合的内联代码 (`)
  //    先去掉围栏块，再统计剩余反引号（避免把围栏的 ``` 算进去）
  const withoutFences = result.replace(/```[\s\S]*?```/g, '').replace(/^```/gm, '')
  const singleBacktickCount = (withoutFences.match(/(?<!`)`(?!`)/g) || []).length
  if (singleBacktickCount % 2 !== 0) {
    result += '`'
  }

  // 3. 补全未闭合的加粗/斜体
  if ((result.match(/\*\*/g) || []).length % 2 !== 0) result += '**'
  if ((result.match(/(?<!\*)\*(?!\*)/g) || []).length % 2 !== 0) result += '*'

  // 4. 截掉末尾未完成的表格行（流式中表格单元格不完整会导致 marked 输出错乱）
  const lines = result.split('\n')
  const lastLine = lines[lines.length - 1]
  // 如果最后一行以 | 开头但格式不完整（缺少闭合 |），截掉整行
  if (lastLine.startsWith('|') && !lastLine.trimEnd().endsWith('|')) {
    lines.pop()
    result = lines.join('\n')
    // 如果有未闭合的表格头，把整个表格部分截掉
    let tableStart = -1
    for (let i = lines.length - 1; i >= 0; i--) {
      if (lines[i].trim().startsWith('|')) tableStart = i
      else break
    }
    if (tableStart >= 0) {
      // 检查表头行后有没有分隔行 (|---|---|)
      const hasSep = lines.slice(tableStart + 1).some(l => /^\|[\s\-:|]+\|$/.test(l.trim()))
      if (!hasSep && lines.length - tableStart <= 3) {
        // 表格还没写完分隔行，整个表格部分都不渲染
        result = lines.slice(0, tableStart).join('\n')
      }
    }
  }

  return result
}

/**
 * 渲染 Markdown → HTML（含 XSS 净化和代码块复制按钮）。
 * 这是纯函数，不依赖外部状态。
 */
const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    // 🔴 前置处理：对漏空格的常见代码场景做补全
    let normalized = text
      .replace(/(?<=[a-zA-Z0-9)]),(?=[a-zA-Z(])/g, ', ')
      .replace(/(-[a-zA-Z]:?\S+)(-[a-zA-Z])/g, '$1 $2')
      .replace(/(\b[A-Z][a-z]+)([a-z]{2,}\b)/g, (match, type, name) => {
        const types = new Set(['Boolean', 'Integer', 'Long', 'String', 'Double', 'Float', 'Short', 'Byte', 'Character', 'Object', 'Class', 'Thread', 'List', 'Set', 'Map', 'Queue', 'Array', 'Enum', 'Exception', 'Runtime', 'System'])
        return types.has(type) ? type + ' ' + name : match
      })
    // 🔴 流式补全：临时闭合未完成的结构，防止 marked 生成错乱 HTML
    const safeForStreaming = sanitizeForStreaming(normalized)
    const rawHtml = marked(safeForStreaming)
    const cleanHtml = DOMPurify.sanitize(rawHtml, { ADD_ATTR: ['target'] })
    let html = cleanHtml.replace(/<pre>/g, '<div class="code-block"><button class="copy-btn">复制</button><pre>')
    html = html.replace(/<\/pre>/g, '</pre></div>')
    return html
  } catch (e) {
    // marked 解析失败时降级显示原文（用 <pre> 保持格式）
    console.warn('Markdown 渲染失败，降级显示原文:', e)
    return '<pre style="white-space:pre-wrap;word-break:break-word">' + escapeHtml(text) + '</pre>'
  }
}

/** HTML 转义（marked 崩溃时的降级方案） */
const escapeHtml = (str) => {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

// 用全局事件委托代理复制按钮点击
document.addEventListener('click', (e) => {
  const btn = e.target.closest('.copy-btn')
  if (!btn || !btn.classList.contains('copy-btn')) return
  const code = btn.closest('.code-block')?.querySelector('pre')?.textContent || ''
  navigator.clipboard.writeText(code).then(() => {
    btn.textContent = '✓ 已复制'
    setTimeout(() => { btn.textContent = '复制' }, 2000)
  }).catch(() => {})
})

// 🔴 前端兜底过滤：移除后端可能漏过来的系统标记
// 包括 [DIM:xxx]、---DETAIL---块、裸DETAIL JSON数组、[NEXT_TOPIC] 等
const filterInternalMarkers = (text) => {
  if (!text) return ''
  // 仅做安全兜底：这些标记在新版系统已不再生成
  let filtered = text
  filtered = filtered.replace(/---DETAIL---[\s\S]*?---DETAIL---/gi, '')
  filtered = filtered.replace(/\[DIM:[^\]]*\]/gi, '')
  filtered = filtered.replace(/\[NEXT_TOPIC\]/g, '')
  return filtered
}

// 创建消息对象时预计算 rendered HTML
const createMessage = (content, isUser, time) => {
  const msg = { content, isUser, time: (time != null && time > 0) ? time : Date.now() }
  if (!isUser) {
    const filtered = filterInternalMarkers(content || '')
    msg.rendered = renderMarkdown(filtered || '')
  }
  return msg
}

// 更新 AI 消息内容并重新渲染
let streamRenderTimer = null
const updateAiMessage = (msg, chunk) => {
  msg.content += chunk
  if (!streamRenderTimer) {
    streamRenderTimer = setTimeout(() => {
      // 🔴 必须在回调里重新读 msg.content，不能用外层闭包的 filtered 变量
      msg.rendered = renderMarkdown(filterInternalMarkers(msg.content || '') || '')
      streamRenderTimer = null
    }, 30)
  }
}
const flushStreamRender = (msg) => {
  if (streamRenderTimer) {
    clearTimeout(streamRenderTimer)
    streamRenderTimer = null
  }
  const filtered = filterInternalMarkers(msg.content || '')
  msg.rendered = renderMarkdown(filtered || '')
}

const isErrorMessage = (content) => {
  return content && (content.startsWith('(连接中断') || content.startsWith('(AI 没有返回') || content.startsWith('[ERROR]'))
}

// localStorage 持久化 chatId
const STORAGE_KEY = 'yu_ai_agent_current_chat_id'
const savedChatId = localStorage.getItem(STORAGE_KEY)
// 🔴 crypto.randomUUID() 仅 HTTPS/localhost 可用，HTTP 需 polyfill
const fallbackUUID = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16) })
const chatId = ref(savedChatId || ('chat_' + (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : fallbackUUID())))
const persistChatId = () => localStorage.setItem(STORAGE_KEY, chatId.value)
watch(chatId, persistChatId)

// 对话重命名
const editingChatId = ref(null)
const editTitle = ref('')
const startRename = (conv) => {
  editingChatId.value = conv.chatId
  editTitle.value = conv.title
  nextTick(() => {
    const el = document.querySelector('.title-input')
    if (el) { el.focus(); el.select() }
  })
}
const confirmRename = async () => {
  const title = editTitle.value.trim()
  if (!title || title.length > 30) { editingChatId.value = null; return }
  try {
    await renameConversation(editingChatId.value, title)
    const conv = conversations.value.find(c => c.chatId === editingChatId.value)
    if (conv) conv.title = title
  } catch (e) {
    console.warn('重命名失败', e)
  }
  editingChatId.value = null
}
const cancelRename = () => { editingChatId.value = null }
const input = ref('')
const messages = ref([])
const conversations = ref([])
const connecting = ref(false)
const msgContainer = ref(null)
const inputEl = ref(null)
const agentMode = ref(localStorage.getItem('yu_ai_agent_mode') === 'true')
watch(agentMode, (v) => localStorage.setItem('yu_ai_agent_mode', String(v)))
let eventSource = null

// 连接阶段状态提示
const STATUS_MESSAGES = [
  '准备中…',
  '检索知识库…',
  '联网搜索…',
  'AI 正在作答…',
]
const connectingStatus = ref('')
let statusTimer = null

watch(connecting, (val) => {
  if (val) {
    let index = 0
    connectingStatus.value = STATUS_MESSAGES[0]
    statusTimer = setInterval(() => {
      index = Math.min(index + 1, STATUS_MESSAGES.length - 1)
      connectingStatus.value = STATUS_MESSAGES[index]
    }, 800)
  } else {
    if (statusTimer) {
      clearInterval(statusTimer)
      statusTimer = null
    }
    connectingStatus.value = ''
  }
})

// 消息缓存
const messageCache = {}
const MAX_CACHED_CONVERSATIONS = 50
const enforceCacheLimit = () => {
  const keys = Object.keys(messageCache)
  if (keys.length > MAX_CACHED_CONVERSATIONS) {
    const oldest = keys.slice(0, keys.length - MAX_CACHED_CONVERSATIONS)
    oldest.forEach(k => delete messageCache[k])
  }
}
const cacheMessages = (id, msgs) => {
  messageCache[id] = msgs
  enforceCacheLimit()
}

// ===== 错题复习 =====
const reviewSourceChatId = ref('') // 原始面试 chatId
const reviewCurrentDir = ref('')
const reviewAnsweredCount = ref(0)
const reviewIsFirstTrigger = ref(true)

// 错题统计数据（从 profileTopics 计算）
const reviewConvTotalCount = computed(() => {
  let c = 0
  for (const t of profileTopics.value) {
    if (t.wrongQuestions) c += Object.keys(t.wrongQuestions).length
  }
  return c
})
const reviewConvDirCount = computed(() => {
  let c = 0
  for (const t of profileTopics.value) {
    if (t.wrongQuestions && Object.keys(t.wrongQuestions).length > 0) c++
  }
  return c
})
const reviewConvVisible = computed(() => reviewConvTotalCount.value > 0)
const reviewConvActive = computed(() => chatId.value.startsWith('review_'))
const reviewProgressPct = computed(() => {
  if (reviewConvTotalCount.value === 0) return 0
  return Math.round((reviewAnsweredCount.value / reviewConvTotalCount.value) * 100)
})

// 切换到错题复习会话
const switchToReviewConv = () => {
  if (reviewConvActive.value) return
  saveCurrentToCache()
  const rid = 'review_' + Date.now()
  reviewSourceChatId.value = chatId.value // 记住原始 chatId
  chatId.value = rid
  messages.value = []
  reviewAnsweredCount.value = 0
  reviewIsFirstTrigger.value = true
  // 自动发首条消息触发后端出题
  input.value = '开始错题复习'
  nextTick(() => send())
}

// 🆕 进入复习模式（从 AbilityPanel 触发）
const enterReviewMode = () => {
  switchToReviewConv()
}

// ===== 能力画像 =====
const PROFILE_CACHE_KEY = 'yu_ai_agent_profile_cache'
const PROFILE_CACHE_CHAT_KEY = 'yu_ai_agent_profile_chat_id'

// 从 localStorage 恢复缓存的画像数据（页面刷新时立即显示，避免 UI 闪烁）
const loadProfileFromCache = () => {
  try {
    const cachedChatId = localStorage.getItem(PROFILE_CACHE_CHAT_KEY)
    const cachedJson = localStorage.getItem(PROFILE_CACHE_KEY)
    if (cachedJson && cachedChatId === chatId.value) {
      const cached = JSON.parse(cachedJson)
      if (Array.isArray(cached) && cached.length > 0) {
        return cached
      }
    }
  } catch (e) { /* ignore */ }
  return null
}

const saveProfileToCache = (topics) => {
  try {
    localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(topics))
    localStorage.setItem(PROFILE_CACHE_CHAT_KEY, chatId.value)
  } catch (e) { /* ignore */ }
}

// 初始化时优先从缓存加载，避免刷新后 UI 先消失再出现
const cachedProfile = loadProfileFromCache()
const profileTopics = ref(cachedProfile || [])
const currentDirection = ref('')
const showAbilityPanel = ref(false)
let profileTimer = null

// 🔴 [V3] 评分等级工具函数
const getScoreLevel = (avg) => {
  if (avg >= 4.5) return { label: '精通', dotClass: 'dot-blue' }
  if (avg >= 3.5) return { label: '良好', dotClass: 'dot-green' }
  if (avg >= 2.5) return { label: '掌握', dotClass: 'dot-yellow' }
  if (avg >= 1.5) return { label: '待加强', dotClass: 'dot-orange' }
  if (avg > 0) return { label: '薄弱', dotClass: 'dot-red' }
  return { label: '未评分', dotClass: 'dot-gray' }
}

const calcAverage = (scores) => {
  if (!scores || scores.length === 0) return 0
  const sum = scores.reduce((a, b) => a + b, 0)
  return Math.round(sum / scores.length * 100) / 100
}

// 🔴 FAB 拖拽状态
const FAB_SIZE = 48
const FAB_MARGIN = 16
const FAB_DEFAULT_X = window.innerWidth - 68
const FAB_DEFAULT_Y = window.innerHeight - 148
const fabPos = reactive({
  x: FAB_DEFAULT_X,
  y: FAB_DEFAULT_Y,
})
const isDragging = ref(false)
let dragStart = { x: 0, y: 0, bx: 0, by: 0 }

const clampFabX = (x) => Math.max(FAB_MARGIN, Math.min(x, window.innerWidth - FAB_SIZE - FAB_MARGIN))
const clampFabY = (y) => Math.max(FAB_MARGIN, Math.min(y, window.innerHeight - FAB_SIZE - FAB_MARGIN))

const FAB_POS_KEY = 'yu_ai_fab_position'

const onFabPointerDown = (e) => {
  isDragging.value = true
  dragStart = { x: e.clientX, y: e.clientY, bx: fabPos.x, by: fabPos.y }
  document.addEventListener('pointermove', onFabPointerMove)
  document.addEventListener('pointerup', onFabPointerUp)
}

const onFabPointerMove = (e) => {
  fabPos.x = clampFabX(dragStart.bx + e.clientX - dragStart.x)
  fabPos.y = clampFabY(dragStart.by + e.clientY - dragStart.y)
}

const onFabPointerUp = (e) => {
  document.removeEventListener('pointermove', onFabPointerMove)
  document.removeEventListener('pointerup', onFabPointerUp)
  isDragging.value = false
  const didMove = Math.abs(e.clientX - dragStart.x) > 5 || Math.abs(e.clientY - dragStart.y) > 5
  if (didMove) {
    try {
      localStorage.setItem(FAB_POS_KEY, JSON.stringify({ x: fabPos.x, y: fabPos.y }))
    } catch (err) { /* ignore */ }
  } else {
    showAbilityPanel.value = true
  }
}

const onFabWindowResize = () => {
  fabPos.x = clampFabX(fabPos.x)
  fabPos.y = clampFabY(fabPos.y)
}

// 🔴 计算薄弱方向数量
const weakCount = computed(() => profileTopics.value.filter(t => {
  const level = getScoreLevel(t.avgScore)
  return level.label === '薄弱' || level.label === '待加强'
}).length)

// 加载能力画像
const loadProfile = async () => {
  if (agentMode.value) return
  try {
    const res = await getProfile(chatId.value)
    const data = res.data
    if (data && data.topicScores) {
      const topics = Object.entries(data.topicScores || {}).map(([topic, ts]) => {
        const scoreHistory = ts.scoreHistory || []
        const avgScore = calcAverage(scoreHistory)
        const level = getScoreLevel(avgScore)
        return {
          topic,
          score: ts.score || 0,
          avgScore,
          scoreLevel: level.label,
          scoreEmoji: level.dotClass,
          scoreHistory,
          questionCount: ts.questionCount || scoreHistory.length,
          weakPoints: ts.weakPoints || [],
          weakPointAnswers: ts.weakPointAnswers || {},
          weakPointDetails: ts.weakPointDetails || {},
          // 🔴 错题本：知识点→原题映射
          wrongQuestions: ts.wrongQuestions || {},
        }
      })
      topics.sort((a, b) => {
        if (a.questionCount === 0 && b.questionCount === 0) return 0
        if (a.questionCount === 0) return 1
        if (b.questionCount === 0) return -1
        return a.score - b.score
      })
      profileTopics.value = topics
      // 🔴 缓存到 localStorage，避免刷新后 UI 闪烁
      saveProfileToCache(topics)
    }
  } catch (e) {
    if (e.response?.status !== 404) {
      console.warn('加载能力画像失败', e)
    }
  }
}

// 从 AI 回复中提取当前方向信息（只认后端拼接的【本轮考题】，忽略【点评】等）
const extractDirection = (content) => {
  const match = content.match(/【本轮考题】([^\n]+)/)
  if (match) {
    currentDirection.value = match[1].trim()
  }
}

const suggestions = [
  '面试官好，我准备好了',
  '我主要擅长 Java 并发编程',
  '可以开始考察 Spring 框架了',
  '请给我出一道系统设计题',
]

const agentSuggestions = [
  '帮我深度分析 Java volatile 的底层实现原理',
  'Spring 事务传播机制有哪些？各有什么适用场景？',
  '设计一个支持百万并发的分布式 ID 生成器',
  '帮我研究一下 Redis 集群模式和哨兵模式的区别',
]

// 🔴 rAF 节流滚动
let scrollRafId = null
const scrollDown = () => {
  if (scrollRafId) return
  scrollRafId = requestAnimationFrame(() => {
    if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    scrollRafId = null
  })
}

const saveCurrentToCache = () => {
  if (messages.value.length > 0) {
    cacheMessages(chatId.value, [...messages.value])
  }
}

const loadConversations = async () => {
  try {
    const res = await getConversations()
    const serverList = res.data || []
    // 合并本地缓存中服务端没有的会话
    for (const [id, msgs] of Object.entries(messageCache)) {
      if (msgs.length > 0 && !serverList.find(c => c.chatId === id)) {
        serverList.unshift({
          chatId: id,
          title: '对话',
          lastModified: msgs[msgs.length - 1].time,
          messageCount: msgs.length
        })
      }
    }
    // 🔴 合并时保留本地已更新的计数（本地更新比服务端文件更实时）
    const oldList = conversations.value
    for (const srv of serverList) {
      const old = oldList.find(c => c.chatId === srv.chatId)
      if (old && old.messageCount > srv.messageCount) {
        srv.messageCount = old.messageCount
        srv.lastModified = Math.max(srv.lastModified, old.lastModified)
      }
    }
    conversations.value = serverList
  } catch (e) {
    console.warn('加载会话列表失败', e)
  }
}

const newChat = () => {
  if (eventSource) eventSource.close()
  connecting.value = false
  if (reviewConvActive.value) {
    // 离开复习会话，回到原来的面试会话
    chatId.value = reviewSourceChatId.value || ('chat_' + Date.now())
    reviewSourceChatId.value = ''
  }
  saveCurrentToCache()
  const currentId = chatId.value
  if (messages.value.length > 0 && !conversations.value.find(c => c.chatId === currentId)) {
    conversations.value.unshift({
      chatId: currentId,
      title: '对话',
      lastModified: Date.now(),
      messageCount: messages.value.length
    })
  }
  const newId = 'chat_' + Date.now()
  // 🔴 新对话清空画像缓存，避免闪现旧数据
  profileTopics.value = []
  try { localStorage.removeItem(PROFILE_CACHE_KEY) } catch (e) { /* ignore */ }
  try { localStorage.removeItem(PROFILE_CACHE_CHAT_KEY) } catch (e) { /* ignore */ }
  chatId.value = newId
  messages.value = []
  messageCache[newId] = []
  conversations.value.unshift({
    chatId: newId,
    title: '对话',
    lastModified: Date.now(),
    messageCount: 0
  })
}

const removeConversation = async (id) => {
  if (!confirm('确定删除这个对话吗？此操作不可恢复。')) return
  try {
    await deleteConversation(id)
  } catch (e) {
    console.warn('删除失败', e)
  }
  conversations.value = conversations.value.filter(c => c.chatId !== id)
  delete messageCache[id]
  if (chatId.value === id) {
    if (eventSource) eventSource.close()
    connecting.value = false
    if (conversations.value.length > 0) {
      switchConversation(conversations.value[0].chatId)
    } else {
      newChat()
    }
  }
}

const switchConversation = async (id) => {
  if (id === chatId.value) return
  if (eventSource) eventSource.close()
  connecting.value = false
  // 如果当前是复习会话，离开时记录原始 chatId
  if (reviewConvActive.value && !id.startsWith('review_')) {
    reviewSourceChatId.value = ''
  }
  saveCurrentToCache()
  chatId.value = id
  if (messageCache[id]) {
    messages.value = messageCache[id]
    scrollDown()
    return
  }
  try {
    const res = await getConversationMessages(id)
    const history = res.data || []
    if (history.length > 0) {
      messages.value = history.map((msg, idx) =>
        createMessage(msg.content || '', msg.messageType === 'USER',
          Date.now() - (history.length - idx) * 60000)
      )
      messageCache[id] = [...messages.value]
    } else {
      messages.value = []
    }
    scrollDown()
  } catch (e) {
    console.warn('加载历史消息失败', e)
    messages.value = []
  }
}

const sendSuggestion = (text) => {
  input.value = text
  send()
}

const stopGeneration = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connecting.value = false
  flushCacheWrite()
}

const retryMessage = (idx) => {
  const failedMsg = messages.value[idx]
  if (!failedMsg || failedMsg.isUser) return
  messages.value.splice(idx, 1)
  cacheMessages(chatId.value, [...messages.value])
  const lastUser = [...messages.value].reverse().find(m => m.isUser)
  if (!lastUser) return
  connecting.value = true
  let aiIdx = messages.value.length
  messages.value.push(createMessage('', false))
  if (eventSource) eventSource.close()
  eventSource = agentMode.value ? agentChat(lastUser.content) : chat(lastUser.content, chatId.value)
  eventSource.onmessage = (e) => {
    if (e.data && e.data !== '[DONE]') {
      connecting.value = false
      updateAiMessage(messages.value[aiIdx], e.data)
      scheduleCacheWrite()
      scrollDown()
    }
    if (e.data === '[DONE]') {
      connecting.value = false
      eventSource.close()
      flushStreamRender(messages.value[aiIdx])
      flushCacheWrite()
    }
  }
  eventSource.onerror = () => {
    connecting.value = false
    eventSource.close()
    if (!messages.value[aiIdx].content) {
      messages.value[aiIdx].content = '(连接中断，请重试)'
    }
    flushCacheWrite()
  }
}

// 🔴 节流缓存写入
let cacheTimer = null
const updateConversationInfo = () => {
  const idx = conversations.value.findIndex(c => c.chatId === chatId.value)
  if (idx >= 0) {
    conversations.value[idx].messageCount = messages.value.length
    conversations.value[idx].lastModified = Date.now()
  }
}
const scheduleCacheWrite = () => {
  if (!cacheTimer) {
    cacheTimer = setTimeout(() => {
      cacheMessages(chatId.value, [...messages.value])
      updateConversationInfo()
      cacheTimer = null
    }, 200)
  }
}
const flushCacheWrite = () => {
  if (cacheTimer) {
    clearTimeout(cacheTimer)
    cacheTimer = null
  }
  cacheMessages(chatId.value, [...messages.value])
  updateConversationInfo()
}

const send = () => {
  const text = input.value.trim()
  if (!text || connecting.value) return
  input.value = ''

  // 🆕 错题复习模式分支
  if (reviewConvActive.value) {
    const userMsg = createMessage(text, true)
    messages.value.push(userMsg)
    scrollDown()
    if (eventSource) eventSource.close()
    connecting.value = true
    let aiIdx = -1
    const rid = chatId.value
    const sid = reviewSourceChatId.value
    eventSource = reviewChat(text, rid, sid)
    eventSource.onmessage = (e) => {
      if (e.data && e.data !== '[DONE]') {
        if (aiIdx === -1) {
          connecting.value = false
          aiIdx = messages.value.length
          messages.value.push(createMessage('', false))
        }
        updateAiMessage(messages.value[aiIdx], e.data)
        // 复习：勿用第一个【…】（常为回顾/讲解），优先本轮考题或括号方向
        const examDir = e.data.match(/【本轮考题】([^\n]+)/)
        const parenDir = e.data.match(/\n\(([^)\n]{2,30})\)\s*(?:\n|$)/)
        if (examDir) {
          reviewCurrentDir.value = examDir[1].trim()
          currentDirection.value = examDir[1].trim()
        } else if (parenDir) {
          reviewCurrentDir.value = parenDir[1].trim()
        }
        scheduleCacheWrite()
        scrollDown()
      }
      if (e.data === '[DONE]') {
        connecting.value = false
        eventSource.close()
        flushStreamRender(messages.value[aiIdx])
        flushCacheWrite()
        if (!reviewIsFirstTrigger.value) reviewAnsweredCount.value++
        reviewIsFirstTrigger.value = false
        if (reviewAnsweredCount.value >= reviewConvTotalCount.value && reviewConvTotalCount.value > 0) {
          setTimeout(() => {
            alert('🎉 恭喜！所有错题已清除！')
            // 回到原来的面试会话
            chatId.value = reviewSourceChatId.value
            reviewSourceChatId.value = ''
            messages.value = []
            loadProfile()
          }, 1000)
        }
      }
    }
    eventSource.onerror = () => {
      connecting.value = false; eventSource.close()
      if (aiIdx >= 0 && !messages.value[aiIdx].content) messages.value[aiIdx].content = '(连接中断，请重试)'
      else if (aiIdx === -1) messages.value.push(createMessage('(连接中断，请重试)', false))
      cacheMessages(rid, [...messages.value])
    }
    return
  }

  const userMsg = createMessage(text, true)
  messages.value.push(userMsg)
  scrollDown()
  // 🔴 立即更新侧边栏计数（不等防抖），确保用户消息发出后计数即时刷新
  updateConversationInfo()
  cacheMessages(chatId.value, [...messages.value])
  if (eventSource) eventSource.close()
  connecting.value = true
  // 🔴 用户发消息后立即刷一次画像（确保最新状态）
  loadProfile()
  let aiIdx = -1
  eventSource = agentMode.value ? agentChat(text) : chat(text, chatId.value)
  eventSource.onmessage = (e) => {
    if (e.data && e.data !== '[DONE]') {
      if (aiIdx === -1) {
        connecting.value = false
        aiIdx = messages.value.length
        messages.value.push(createMessage('', false))
      }
      updateAiMessage(messages.value[aiIdx], e.data)
      if (e.data.includes('【')) {
        extractDirection(e.data)
      }
      scheduleCacheWrite()
      scrollDown()
    }
    if (e.data === '[DONE]') {
      connecting.value = false
      eventSource.close()
      flushStreamRender(messages.value[aiIdx])
      flushCacheWrite()
      if (aiIdx === -1) {
        messages.value.push(createMessage('(AI 没有返回内容)', false))
        cacheMessages(chatId.value, [...messages.value])
        updateConversationInfo()
      }
      // 🔴 评分是异步的（需要调用 LLM），AI 回复结束后评分可能还没完成
      // 密集刷新确保能拿到最新数据（1s/3s/5s/8s/12s 各刷一次）
      setTimeout(loadProfile, 1000)
      setTimeout(loadProfile, 3000)
      setTimeout(loadProfile, 5000)
      setTimeout(loadProfile, 8000)
      setTimeout(loadProfile, 12000)
      // 🔴 对话结束后刷新会话列表，同步服务端最新计数
      setTimeout(loadConversations, 2000)
    }
  }
  eventSource.onerror = () => {
    connecting.value = false
    eventSource.close()
    flushCacheWrite()
    if (aiIdx >= 0 && !messages.value[aiIdx].content) {
      messages.value[aiIdx].content = '(连接中断，请重试)'
    } else if (aiIdx === -1) {
      messages.value.push(createMessage('(连接中断，请重试)', false))
    }
    cacheMessages(chatId.value, [...messages.value])
  }
}

const formatTime = (ts) => {
  const d = new Date(ts)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) return d.toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })
  return d.toLocaleDateString('zh-CN', { month:'short', day:'numeric' }) + ' ' + d.toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })
}

onMounted(async () => {
  try {
    const saved = localStorage.getItem(FAB_POS_KEY)
    if (saved) {
      const pos = JSON.parse(saved)
      fabPos.x = clampFabX(pos.x || FAB_DEFAULT_X)
      fabPos.y = clampFabY(pos.y || FAB_DEFAULT_Y)
    }
  } catch (err) { /* ignore */ }
  window.addEventListener('resize', onFabWindowResize)
  await loadConversations()
  if (savedChatId) {
    try {
      const res = await getConversationMessages(savedChatId)
      const history = res.data || []
      if (history.length > 0) {
        messages.value = history.map((msg, idx) =>
          createMessage(msg.content || '', msg.messageType === 'USER',
            Date.now() - (history.length - idx) * 60000)
        )
        messageCache[savedChatId] = [...messages.value]
        scrollDown()
      }
    } catch (e) {
      console.warn('恢复历史消息失败', e)
    }
  }
  setTimeout(loadProfile, 1000)
  profileTimer = setInterval(loadProfile, 10000)
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  if (profileTimer) clearInterval(profileTimer)
  if (scrollRafId) cancelAnimationFrame(scrollRafId)
  if (cacheTimer) clearTimeout(cacheTimer)
  document.removeEventListener('pointermove', onFabPointerMove)
  document.removeEventListener('pointerup', onFabPointerUp)
  window.removeEventListener('resize', onFabWindowResize)
})
</script>

<style scoped>
/* ===== 布局 ===== */
.layout { display: flex; height: 100vh; background: #fff; }

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px; min-width: 260px; background: #f9fafb; border-right: 1px solid #e8ecf1;
  display: flex; flex-direction: column;
}
.sidebar-header { display: flex; align-items: center; gap: 8px; padding: 20px 16px; cursor: pointer; }
.s-logo { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; color: #4f46e5; } .s-title { font-size: 16px; font-weight: 700; color: #1a1a2e; }
.sidebar-body { flex: 1; padding: 8px 12px; overflow-y: auto; }
.new-chat-btn {
  width: 100%; padding: 10px; border: 1px solid #dde1e7; border-radius: 10px; background: #fff;
  font-size: 14px; color: #374151; cursor: pointer; transition: all .2s; margin-bottom: 12px;
}
.new-chat-btn:hover { border-color: #4f46e5; color: #4f46e5; }

.conv-list { display: flex; flex-direction: column; gap: 2px; }
.conv-item {
  padding: 10px 12px; border-radius: 8px; cursor: pointer; transition: all .15s;
}
.conv-item:hover { background: #eef2ff; }
.conv-active { background: #e0e7ff; }
.conv-active .conv-title { color: #4f46e5; font-weight: 600; }
.conv-title { font-size: 13px; color: #374151; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-row { display: flex; justify-content: space-between; align-items: center; margin-top: 3px; }
.conv-meta { font-size: 11px; color: #9ca3af; }
.conv-del {
  font-size: 16px; color: #94a3b8; background: none; border: none; cursor: pointer;
  width: 20px; height: 20px; display: flex; align-items: center; justify-content: center;
  border-radius: 4px; transition: all .15s; opacity: 0;
}
.conv-item:hover .conv-del { opacity: 1; }
.conv-del:hover { color: #ef4444; background: #fef2f2; }
.sidebar-hint { font-size: 12px; color: #9ca3af; text-align: center; margin-top: 16px; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #e8ecf1; display: flex; flex-direction: column; gap: 8px; }
.s-model { font-size: 11px; color: #9ca3af; text-align: center; }
.mode-toggle {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  cursor: pointer; user-select: none; padding: 4px 0;
}
.mode-label { font-size: 11px; color: #94a3b8; transition: color .2s; }
.mode-label.mode-active { color: #4f46e5; font-weight: 600; }
.mode-switch {
  width: 36px; height: 20px; border-radius: 10px; background: #e2e8f0;
  position: relative; transition: background .2s; flex-shrink: 0;
}
.mode-knob {
  width: 16px; height: 16px; border-radius: 50%; background: #fff;
  position: absolute; top: 2px; left: 2px; transition: transform .2s;
  box-shadow: 0 1px 3px rgba(0,0,0,.12);
}

/* ===== 主区域 ===== */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; position: relative; }

/* ===== 欢迎区 ===== */
.welcome { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px 24px; }
.welcome h2 { font-size: 28px; font-weight: 700; color: #1a1a2e; margin: 0 0 8px; }
.welcome-sub { font-size: 14px; color: #94a3b8; margin: 0 0 32px; }
.suggestions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; max-width: 600px; }
.sug-btn {
  padding: 10px 18px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff;
  font-size: 14px; color: #475569; cursor: pointer; transition: all .2s; text-align: left;
}
.sug-btn:hover { border-color: #4f46e5; color: #4f46e5; background: #f5f3ff; }

/* ===== 消息区 ===== */
.msg-area { flex: 1; overflow-y: auto; padding: 24px 0; }
.msg { padding: 0 24px; margin-bottom: 4px; }
.msg-inner { display: flex; gap: 12px; max-width: 800px; margin: 0 auto; }
.msg-user .msg-inner { justify-content: flex-end; }
.msg-avatar {
  width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; flex-shrink: 0; background: #eef2ff; color: #4f46e5;
}
.msg-avatar-user { background: #4f46e5; color: #fff; font-size: 12px; font-weight: 700; }
.msg-avatar-placeholder { width: 34px; height: 34px; flex-shrink: 0; }
.msg-same .msg-body { margin-top: -2px; }
.msg-same .msg-text { border-radius: 16px; }
.msg-same.msg-ai .msg-text { border-top-left-radius: 4px; }
.msg-same.msg-user .msg-text { border-top-right-radius: 4px; }
.msg-body { max-width: 75%; min-width: 60px; }
.msg-user .msg-body { display: flex; flex-direction: column; align-items: flex-end; }
.msg-text { font-size: 15px; line-height: 1.7; color: #1e293b; white-space: pre-wrap; word-break: break-word;
  padding: 10px 16px; border-radius: 16px; }
.msg-user .msg-text { background: #4f46e5; color: #fff; border-bottom-right-radius: 4px; }
.msg-ai .msg-text { background: #f1f5f9; border-bottom-left-radius: 4px; }

/* ===== Markdown 渲染 ===== */
.msg-md :deep(p) { margin: 0 0 8px; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(strong) { font-weight: 700; color: #1a1a2e; }
.msg-md :deep(em) { font-style: italic; }
.msg-md :deep(code) {
  background: #e2e8f0; color: #be185d; padding: 2px 6px; border-radius: 4px;
  font-size: 13px; font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
}
.msg-md :deep(pre) {
  background: #1e293b; color: #e2e8f0; padding: 14px 16px; border-radius: 10px;
  overflow-x: auto; margin: 8px 0; font-size: 13px; line-height: 1.6;
}
.msg-md :deep(pre code) { background: none; color: inherit; padding: 0; font-size: inherit; }
.msg-md :deep(ul), .msg-md :deep(ol) { padding-left: 20px; margin: 6px 0; }
.msg-md :deep(li) { margin-bottom: 3px; line-height: 1.6; }
.msg-md :deep(blockquote) {
  border-left: 3px solid #818cf8; padding-left: 12px; margin: 8px 0;
  color: #475569; font-style: italic;
}
.msg-md :deep(h1), .msg-md :deep(h2), .msg-md :deep(h3), .msg-md :deep(h4) {
  font-weight: 700; color: #1a1a2e; margin: 12px 0 6px; line-height: 1.3;
}
.msg-md :deep(h1) { font-size: 19px; }
.msg-md :deep(h2) { font-size: 17px; }
.msg-md :deep(h3) { font-size: 15px; }
.msg-md :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 12px 0; }
.msg-md :deep(a) { color: #4f46e5; text-decoration: underline; }
.msg-md :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.msg-md :deep(th), .msg-md :deep(td) { border: 1px solid #e2e8f0; padding: 6px 10px; text-align: left; font-size: 13px; }
.msg-md :deep(th) { background: #f1f5f9; font-weight: 600; }
.msg-meta { font-size: 11px; color: #94a3b8; margin-top: 4px; padding: 0 4px; }

/* ===== 输入中动画（带阶段提示） ===== */
.typing-status {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; background: #f1f5f9; border-radius: 16px; border-bottom-left-radius: 4px;
}
.typing-status-text { font-size: 13px; color: #64748b; white-space: nowrap; }
.typing-dots { display: flex; gap: 4px; }
.typing-dots span { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; animation: dot 1.4s infinite both; }
.typing-dots span:nth-child(2) { animation-delay: .2s; }
.typing-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dot { 0%,80%,100% { opacity: .2; transform: scale(.8); } 40% { opacity: 1; transform: scale(1); } }

/* ===== 输入区 ===== */
.input-area {
  padding: 16px 24px 20px;
  background: linear-gradient(to top, #fff 60%, transparent);
}
.input-wrap {
  display: flex; gap: 8px; align-items: flex-end; max-width: 800px; margin: 0 auto;
  border: 1px solid #e2e8f0; border-radius: 20px;
  padding: 8px 8px 8px 20px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,.04), 0 0 0 1px rgba(0,0,0,.02);
  transition: border-color .2s, box-shadow .2s;
}
.input-wrap:focus-within {
  border-color: #94a3b8;
  box-shadow: 0 4px 16px rgba(0,0,0,.06), 0 0 0 1px rgba(0,0,0,.04);
}
.input-box {
  flex: 1; border: none; outline: none; resize: none; font-size: 15px; line-height: 1.6;
  padding: 8px 0; max-height: 160px; font-family: inherit; background: transparent;
  color: #1e293b;
}
.input-box::placeholder { color: #94a3b8; }
.send-btn {
  width: 40px; height: 40px; border-radius: 50%; border: none; background: #1e293b; color: #fff;
  display: flex; align-items: center; justify-content: center; cursor: pointer; flex-shrink: 0;
  transition: all .2s;
}
.send-btn:hover:not(:disabled) { background: #0f172a; transform: scale(1.06); }
.send-btn:disabled { background: #e2e8f0; color: #cbd5e1; cursor: not-allowed; }
.send-btn:disabled:hover { transform: none; }
.input-hint { text-align: center; font-size: 12px; color: #94a3b8; margin: 10px 0 0; }
/* 停止按钮 */
.stop-btn { background: #ef4444 !important; }
.stop-btn:hover { background: #dc2626 !important; }

/* 标题编辑 */
.title-input {
  width: 100%; padding: 2px 6px; font-size: 13px; border: 1px solid #4f46e5;
  border-radius: 4px; outline: none; background: #fff;
}

/* 重试按钮 */
.retry-btn {
  display: inline-block; margin-top: 6px; padding: 4px 12px; font-size: 12px;
  color: #4f46e5; background: #eef2ff; border: 1px solid #c7d2fe; border-radius: 6px;
  cursor: pointer; transition: all .15s;
}
.retry-btn:hover { background: #e0e7ff; }

/* 代码块复制 */
.code-block { position: relative; }
.code-block .copy-btn {
  position: absolute; top: 6px; right: 8px; padding: 3px 10px; font-size: 11px;
  color: #e2e8f0; background: #334155; border: none; border-radius: 4px;
  cursor: pointer; opacity: 0; transition: opacity .2s; z-index: 1;
}
.code-block:hover .copy-btn { opacity: 1; }

/* ===== 浮动按钮（可拖拽） ===== */
.ability-fab {
  position: fixed;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #4f46e5;
  color: white;
  border: none;
  font-size: 20px;
  cursor: grab;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
  z-index: 100;
  transition: box-shadow .2s;
  display: flex;
  align-items: center;
  justify-content: center;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}
.ability-fab:hover {
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.5);
}
.ability-fab:active {
  cursor: grabbing;
}
.fab-dragging {
  transition: none !important;
  box-shadow: 0 8px 28px rgba(79, 70, 229, 0.6);
  z-index: 101;
}
.fab-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  background: #ef4444;
  border-radius: 50%;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 4px rgba(239, 68, 68, 0.4);
}

/* ===== 能力画像遮罩 ===== */
.ability-drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.3);
  z-index: 150;
}

/* ===== 能力画像抽屉面板 ===== */
.ability-drawer {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: min(360px, 100vw);
  background: #fafbfc;
  box-shadow: -4px 0 20px rgba(0,0,0,0.15);
  z-index: 200;
  overflow-y: auto;
  transform: translateX(0);
  transition: transform 0.25s ease;
}
.ability-drawer.drawer-hidden {
  transform: translateX(100%);
  pointer-events: none;
}

/* 遮罩：淡入淡出 */
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.25s ease;
}
.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}

/* ===== 🆕 错题复习（极简精英蓝） ===== */
/* 侧边栏 */
.review-sidebar-section { margin-bottom: 4px; }
.review-sidebar-label {
  font-size: 10px; font-weight: 600; color: #94a3b8; text-transform: uppercase;
  letter-spacing: .5px; padding: 0 4px; margin-bottom: 4px;
}
.sidebar-section-label {
  font-size: 10px; font-weight: 600; color: #94a3b8; text-transform: uppercase;
  letter-spacing: .5px; padding: 0 4px; margin-bottom: 4px;
}
.review-sidebar-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px; border-radius: 10px; cursor: pointer; transition: all .2s;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #3b82f6;
  box-shadow: 0 1px 3px rgba(0,0,0,.03);
}
.review-sidebar-card:hover {
  border-left-color: #2563eb;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}
.review-sidebar-active {
  background: #f8faff !important;
  border-color: #bfdbfe !important;
  border-left-color: #2563eb !important;
  border-left-width: 3px !important;
  box-shadow: 0 0 0 3px rgba(59,130,246,.08) !important;
}
.review-sidebar-icon {
  width: 34px; height: 34px; border-radius: 8px; flex-shrink: 0;
  background: transparent;
  color: #3b82f6;
  display: flex; align-items: center; justify-content: center; font-size: 14px;
}
.review-sidebar-info { flex: 1; min-width: 0; }
.review-sidebar-title { font-size: 13px; font-weight: 600; color: #0f172a; letter-spacing: -.1px; }
.review-sidebar-active .review-sidebar-title { color: #0f172a; }
.review-sidebar-meta { font-size: 10px; color: #94a3b8; margin-top: 2px; }
.review-sidebar-badge {
  font-size: 13px; font-weight: 700; color: #3b82f6;
  font-variant-numeric: tabular-nums; font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
  white-space: nowrap;
}
.review-sidebar-active .review-sidebar-badge { color: #2563eb; }
.review-sidebar-divider { height: 1px; background: #e8ecf1; margin: 6px 0; }

/* 顶栏 */
.review-topbar {
  display: flex; align-items: center; gap: 14px;
  padding: 16px 28px; border-bottom: 1px solid #f1f5f9;
  background: #fff;
  font-size: 13px;
}
.review-topbar-icon {
  width: 28px; height: 28px; border-radius: 7px; flex-shrink: 0;
  background: transparent;
  color: #3b82f6;
  display: flex; align-items: center; justify-content: center; font-size: 13px;
}
.review-topbar-title { font-weight: 600; color: #0f172a; font-size: 14px; }
.review-topbar-dir { font-size: 11px; color: #64748b; }
.review-topbar-spacer { flex: 1; }
.review-topbar-stat { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #64748b; }
/* 分段步进圆点 */
.review-topbar-steps { display: flex; gap: 3px; align-items: center; }
.review-topbar-dot {
  width: 7px; height: 7px; border-radius: 50%; background: #e2e8f0;
  transition: all .3s ease;
}
.review-topbar-dot.done { background: #3b82f6; }
.review-topbar-dot.current {
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37,99,235,.2);
  transform: scale(1.3);
}
.review-topbar-count { font-size: 15px; font-weight: 700; color: #3b82f6; font-variant-numeric: tabular-nums; }
.review-topbar-total { font-size: 12px; color: #94a3b8; }
.review-topbar-bar { display: none; }
.review-topbar-fill { display: none; }

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .sidebar { display: none; }
  .msg-inner { max-width: 100%; }
  .msg-body { max-width: 85%; }
  .msg { padding: 0 16px; }
  .input-area { padding: 12px 16px 16px; }
  .welcome h2 { font-size: 22px; }
  .sug-btn { font-size: 13px; padding: 8px 14px; }
}
</style>
