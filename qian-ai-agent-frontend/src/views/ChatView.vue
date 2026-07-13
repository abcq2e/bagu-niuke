<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header" @click="$router.push('/')">
        <span class="s-logo">{{ agentMode ? '🤖' : '🧠' }}</span>
        <span class="s-title">{{ agentMode ? 'AI Agent' : 'AI 面试官' }}</span>
      </div>

      <div class="sidebar-body">
        <button class="new-chat-btn" @click="newChat">+ 新对话</button>

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
          <span :class="['mode-label', { 'mode-active': !agentMode }]">💬 智能对话</span>
          <span class="mode-switch">
            <span class="mode-knob" :style="{ transform: agentMode ? 'translateX(100%)' : 'translateX(0)' }"></span>
          </span>
          <span :class="['mode-label', { 'mode-active': agentMode }]">🧠 Agent 规划</span>
        </div>
        <span class="s-model">DeepSeek · DashScope</span>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="main">
      <!-- 消息区 -->
      <div class="msg-area" ref="msgContainer">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0" class="welcome">
          <h2 v-if="!agentMode">面试开始，你准备好了吗？</h2>
          <h2 v-else>Agent 模式已就绪</h2>
          <p class="welcome-sub" v-if="!agentMode">我会考察你的 Java 并发、Spring 框架、数据结构和系统设计能力</p>
          <p class="welcome-sub" v-else>我会自主规划、调用工具，逐步解决你的复杂问题</p>
          <div class="suggestions">
            <button v-for="s in (agentMode ? agentSuggestions : suggestions)" :key="s" @click="sendSuggestion(s)" :disabled="connecting" class="sug-btn">{{ s }}</button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.isUser ? 'msg-user' : 'msg-ai', { 'msg-same': i > 0 && msg.isUser === messages[i-1].isUser }]">
          <div class="msg-inner">
            <div class="msg-avatar" v-if="!msg.isUser && (i === 0 || messages[i-1].isUser)">🤖</div>
            <div class="msg-avatar-placeholder" v-if="!msg.isUser && i > 0 && !messages[i-1].isUser"></div>
            <div class="msg-body">
              <div class="msg-text" v-if="msg.isUser">{{ msg.content }}</div>
              <div class="msg-text msg-md" v-else v-html="renderMarkdown(msg.content)"></div>
              <!-- 🔴 错误重试按钮 -->
              <button
                v-if="!msg.isUser && isErrorMessage(msg.content)"
                class="retry-btn"
                @click="retryMessage(i)"
              >🔄 重试</button>
              <div class="msg-meta">{{ formatTime(msg.time) }}</div>
            </div>
            <div class="msg-avatar msg-avatar-user" v-if="msg.isUser && (i === 0 || !messages[i-1].isUser)">我</div>
            <div class="msg-avatar-placeholder" v-if="msg.isUser && i > 0 && messages[i-1].isUser"></div>
          </div>
        </div>

        <!-- 输入中 -->
        <div v-if="connecting" class="msg msg-ai">
          <div class="msg-inner">
            <div class="msg-avatar">🤖</div>
            <div class="msg-body">
              <div class="typing-dots"><span></span><span></span><span></span></div>
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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22,2 15,22 11,13 2,9"/></svg>
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
    </main>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useHead } from '@unhead/vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { chat, agentChat, getConversations, getConversationMessages, deleteConversation, renameConversation } from '../api'

useHead({ title: 'AI 面试官', meta: [{ name: 'description', content: '严厉的大厂技术面试官' }] })

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
})

const renderMarkdown = (text) => {
  if (!text) return ''
  // 🔴 XSS 防护：先净化 HTML，再渲染
  const rawHtml = marked(text)
  const cleanHtml = DOMPurify.sanitize(rawHtml, { ADD_ATTR: ['target'] })
  // 🔴 代码块加复制按钮
  let html = cleanHtml.replace(/<pre>/g, '<div class="code-block"><button class="copy-btn">复制</button><pre>')
  html = html.replace(/<\/pre>/g, '</pre></div>')
  // 🔴 延迟绑定复制事件（避免内联 onclick XSS）
  nextTick(() => {
    document.querySelectorAll('.code-block .copy-btn').forEach(btn => {
      if (btn._copyBound) return
      btn._copyBound = true
      btn.addEventListener('click', () => {
        const code = btn.closest('.code-block')?.querySelector('pre')?.textContent || ''
        navigator.clipboard.writeText(code).then(() => {
          btn.textContent = '✓ 已复制'
          setTimeout(() => { btn.textContent = '复制' }, 2000)
        }).catch(() => {})
      })
    })
  })
  return html
}

const isErrorMessage = (content) => {
  return content && (content.startsWith('(连接中断') || content.startsWith('(AI 没有返回') || content.startsWith('[ERROR]'))
}

// 🔴 修复：localStorage 持久化 chatId，刷新页面不丢会话
const STORAGE_KEY = 'yu_ai_agent_current_chat_id'
const savedChatId = localStorage.getItem(STORAGE_KEY)
const chatId = ref(savedChatId || ('chat_' + crypto.randomUUID()))  // 🔴 UUID 防碰撞
// 同步到 localStorage
const persistChatId = () => localStorage.setItem(STORAGE_KEY, chatId.value)
// chatId 变化时自动持久化
watch(chatId, persistChatId)

// 🔴 对话重命名
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

// 消息缓存：{ chatId: [{content, isUser, time}, ...] }
const messageCache = {}
const MAX_CACHED_CONVERSATIONS = 50  // 🔴 LRU 上限，防内存泄漏
const enforceCacheLimit = () => {
  const keys = Object.keys(messageCache)
  if (keys.length > MAX_CACHED_CONVERSATIONS) {
    const oldest = keys.slice(0, keys.length - MAX_CACHED_CONVERSATIONS)
    oldest.forEach(k => delete messageCache[k])
  }
}
// 在缓存写入处调用
const cacheMessages = (id, msgs) => {
  messageCache[id] = msgs
  enforceCacheLimit()
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

const scrollDown = () => nextTick(() => {
  if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
})

// 保存当前会话消息到缓存
const saveCurrentToCache = () => {
  if (messages.value.length > 0) {
    cacheMessages(chatId.value, [...messages.value])
  }
}

// 加载会话列表
const loadConversations = async () => {
  try {
    const res = await getConversations()
    const serverList = res.data || []
    // 合并本地临时会话（消息数 > 0 但后端尚未写入的）
    for (const [id, msgs] of Object.entries(messageCache)) {
      if (msgs.length > 0 && !serverList.find(c => c.chatId === id)) {
        serverList.unshift({
          chatId: id,
          title: extractConversationTitle(msgs),
          lastModified: msgs[msgs.length - 1].time,
          messageCount: msgs.length
        })
      }
    }
    conversations.value = serverList
  } catch (e) {
    console.warn('加载会话列表失败', e)
  }
}

// 新建对话（立即在左侧列表插入新条目，缓存当前会话）
const newChat = () => {
  if (eventSource) eventSource.close()
  connecting.value = false

  // 保存当前会话到缓存
  saveCurrentToCache()

  // 确保当前会话在列表中
  const currentId = chatId.value
  if (messages.value.length > 0 && !conversations.value.find(c => c.chatId === currentId)) {
    const title = extractConversationTitle(messages.value)
    conversations.value.unshift({
      chatId: currentId,
      title,
      lastModified: Date.now(),
      messageCount: messages.value.length
    })
  }

  // 创建新会话
  const newId = 'chat_' + Date.now()
  chatId.value = newId
  messages.value = []
  messageCache[newId] = []

  conversations.value.unshift({
    chatId: newId,
    title: '新对话',
    lastModified: Date.now(),
    messageCount: 0
  })
}

// 从消息中提取对话标题
const extractConversationTitle = (msgs) => {
  const firstUser = msgs.find(m => m.isUser)
  if (firstUser) {
    const t = firstUser.content
    return t.length > 30 ? t.substring(0, 30) + '…' : t
  }
  return '对话'
}

// 删除会话
const removeConversation = async (id) => {
  if (!confirm('确定删除这个对话吗？此操作不可恢复。')) return
  try {
    await deleteConversation(id)
  } catch (e) {
    console.warn('删除失败', e)
  }
  // 从列表和缓存中移除
  conversations.value = conversations.value.filter(c => c.chatId !== id)
  delete messageCache[id]
  // 如果删除的是当前会话，切换到最近一个或新建
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

// 切换会话（优先从缓存恢复，缓存没有才从后端加载）
const switchConversation = async (id) => {
  if (id === chatId.value) return
  if (eventSource) eventSource.close()
  connecting.value = false

  // 保存当前会话到缓存
  saveCurrentToCache()

  chatId.value = id

  // 先从缓存恢复
  if (messageCache[id]) {
    messages.value = messageCache[id]
    scrollDown()
    return
  }

  // 缓存没有，从后端加载
  try {
    const res = await getConversationMessages(id)
    const history = res.data || []
    if (history.length > 0) {
      messages.value = history.map(msg => ({
        content: msg.content || '',
        isUser: msg.messageType === 'USER',
        time: Date.now() - history.length * 60000
      }))
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

// 🔴 停止 AI 生成
const stopGeneration = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connecting.value = false
  // 保留已生成的部分内容
}

// 🔴 重试失败的 AI 消息（修复：不重复 push 用户消息）
const retryMessage = (idx) => {
  const failedMsg = messages.value[idx]
  if (!failedMsg || failedMsg.isUser) return
  // 删掉失败的 AI 消息
  messages.value.splice(idx, 1)
  cacheMessages(chatId.value, [...messages.value])
  // 拿上一条用户消息重发（直接调 chat，不通过 send 避免重复 push）
  const lastUser = [...messages.value].reverse().find(m => m.isUser)
  if (!lastUser) return
  connecting.value = true
  let aiIdx = messages.value.length
  messages.value.push({ content: '', isUser: false, time: Date.now() })
  if (eventSource) eventSource.close()
  eventSource = agentMode.value ? agentChat(lastUser.content) : chat(lastUser.content, chatId.value)
  eventSource.onmessage = (e) => {
    if (e.data && e.data !== '[DONE]') {
      connecting.value = false
      messages.value[aiIdx].content += e.data
      cacheMessages(chatId.value, [...messages.value])
      scrollDown()
    }
    if (e.data === '[DONE]') {
      connecting.value = false
      eventSource.close()
    }
  }
  eventSource.onerror = () => {
    connecting.value = false
    eventSource.close()
    if (!messages.value[aiIdx].content) {
      messages.value[aiIdx].content = '(连接中断，请重试)'
    }
    cacheMessages(chatId.value, [...messages.value])
  }
}

const send = () => {
  const text = input.value.trim()
  if (!text || connecting.value) return
  input.value = ''
  const isFirstMsg = messages.value.length === 0
  const userMsg = { content: text, isUser: true, time: Date.now() }
  messages.value.push(userMsg)
  scrollDown()

  // 首条消息：更新侧边栏标题
  if (isFirstMsg) {
    const title = text.length > 30 ? text.substring(0, 30) + '…' : text
    const existing = conversations.value.find(c => c.chatId === chatId.value)
    if (existing) {
      existing.title = title
    } else {
      conversations.value.unshift({
        chatId: chatId.value,
        title,
        lastModified: Date.now(),
        messageCount: 0
      })
    }
  }
  // 每次发消息同步更新缓存
  cacheMessages(chatId.value, [...messages.value])

  if (eventSource) eventSource.close()
  connecting.value = true

  let aiIdx = -1
  eventSource = agentMode.value ? agentChat(text) : chat(text, chatId.value)
  eventSource.onmessage = (e) => {
    if (e.data && e.data !== '[DONE]') {
      if (aiIdx === -1) {
        connecting.value = false
        aiIdx = messages.value.length
        messages.value.push({ content: '', isUser: false, time: Date.now() })
      }
      messages.value[aiIdx].content += e.data
      // 流式更新缓存
      cacheMessages(chatId.value, [...messages.value])
      scrollDown()
    }
    if (e.data === '[DONE]') {
      connecting.value = false
      eventSource.close()
      if (aiIdx === -1) {
        messages.value.push({ content: '(AI 没有返回内容)', isUser: false, time: Date.now() })
        cacheMessages(chatId.value, [...messages.value])
      }
    }
  }
  eventSource.onerror = () => {
    connecting.value = false
    eventSource.close()
    if (aiIdx >= 0 && !messages.value[aiIdx].content) {
      messages.value[aiIdx].content = '(连接中断，请重试)'
    } else if (aiIdx === -1) {
      messages.value.push({ content: '(连接中断，请重试)', isUser: false, time: Date.now() })
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
  await loadConversations()
  // 🔴 刷新后恢复当前会话的消息
  if (savedChatId) {
    try {
      const res = await getConversationMessages(savedChatId)
      const history = res.data || []
      if (history.length > 0) {
        messages.value = history.map(msg => ({
          content: msg.content || '',
          isUser: msg.messageType === 'USER',
          time: Date.now() - history.length * 60000
        }))
        messageCache[savedChatId] = [...messages.value]
        scrollDown()
        return
      }
    } catch (e) {
      console.warn('恢复历史消息失败', e)
    }
    // 如果后端也没有消息，清除无效的 chatId
    localStorage.removeItem(STORAGE_KEY)
    chatId.value = 'chat_' + Date.now()
  }
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
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
.s-logo { font-size: 24px; } .s-title { font-size: 16px; font-weight: 700; color: #1a1a2e; }
.sidebar-body { flex: 1; padding: 8px 12px; overflow-y: auto; }
.new-chat-btn {
  width: 100%; padding: 10px; border: 1px solid #dde1e7; border-radius: 10px; background: #fff;
  font-size: 14px; color: #374151; cursor: pointer; transition: all .2s; margin-bottom: 12px;
}
.new-chat-btn:hover { border-color: #4f46e5; color: #4f46e5; }

/* 会话列表 */
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
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

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
  justify-content: center; font-size: 18px; flex-shrink: 0; background: #f1f5f9;
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

/* ===== 输入中动画 ===== */
.typing-dots { display: flex; gap: 4px; padding: 14px 16px; background: #f1f5f9; border-radius: 16px; border-bottom-left-radius: 4px; }
.typing-dots span { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; animation: dot 1.4s infinite both; }
.typing-dots span:nth-child(2) { animation-delay: .2s; }
.typing-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dot { 0%,80%,100% { opacity: .2; transform: scale(.8); } 40% { opacity: 1; transform: scale(1); } }

/* ===== 输入区 ===== */
.input-area { padding: 12px 24px 16px; border-top: 1px solid #f1f5f9; background: #fff; }
.input-wrap {
  display: flex; gap: 8px; align-items: flex-end; max-width: 800px; margin: 0 auto;
  border: 1px solid #e2e8f0; border-radius: 16px; padding: 6px 6px 6px 16px; transition: border-color .2s;
}
.input-wrap:focus-within { border-color: #4f46e5; box-shadow: 0 0 0 3px rgba(79,70,229,.08); }
.input-box {
  flex: 1; border: none; outline: none; resize: none; font-size: 15px; line-height: 1.5;
  padding: 6px 0; max-height: 120px; font-family: inherit; background: transparent;
}
.input-box::placeholder { color: #94a3b8; }
.send-btn {
  width: 38px; height: 38px; border-radius: 12px; border: none; background: #4f46e5; color: #fff;
  display: flex; align-items: center; justify-content: center; cursor: pointer; flex-shrink: 0;
  transition: all .2s;
}
.send-btn:hover:not(:disabled) { background: #4338ca; transform: scale(1.05); }
.send-btn:disabled { background: #cbd5e1; cursor: not-allowed; }
.input-hint { text-align: center; font-size: 12px; color: #94a3b8; margin: 8px 0 0; }

/* 🔴 停止按钮 */
.stop-btn { background: #ef4444 !important; }
.stop-btn:hover { background: #dc2626 !important; }

/* 🔴 标题编辑 */
.title-input {
  width: 100%; padding: 2px 6px; font-size: 13px; border: 1px solid #4f46e5;
  border-radius: 4px; outline: none; background: #fff;
}

/* 🔴 重试按钮 */
.retry-btn {
  display: inline-block; margin-top: 6px; padding: 4px 12px; font-size: 12px;
  color: #4f46e5; background: #eef2ff; border: 1px solid #c7d2fe; border-radius: 6px;
  cursor: pointer; transition: all .15s;
}
.retry-btn:hover { background: #e0e7ff; }

/* 🔴 代码块复制 */
.code-block { position: relative; }
.code-block .copy-btn {
  position: absolute; top: 6px; right: 8px; padding: 3px 10px; font-size: 11px;
  color: #e2e8f0; background: #334155; border: none; border-radius: 4px;
  cursor: pointer; opacity: 0; transition: opacity .2s; z-index: 1;
}
.code-block:hover .copy-btn { opacity: 1; }

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
