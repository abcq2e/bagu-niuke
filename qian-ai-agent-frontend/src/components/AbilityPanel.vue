<template>
  <div class="panel">
    <!-- 面板头部 -->
    <div class="panel-header">
      <div class="panel-title-row">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>
        <span class="panel-title">能力画像</span>
        <span v-if="scoredTopics.length > 0" class="panel-badge">{{ overallLevel }}</span>
      </div>
      <div class="panel-actions">
        <button class="icon-btn" @click="$emit('refresh')" title="刷新">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
        </button>
        <button class="icon-btn" @click="$emit('close')" title="关闭">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="scoredTopics.length > 0">
      <div class="stat-card">
        <span class="stat-value">{{ overallLevel }}</span>
        <span class="stat-label">综合等级</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ scoredTopics.length }}/16</span>
        <span class="stat-label">已考方向</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ totalQuestions }}</span>
        <span class="stat-label">答题数</span>
      </div>
    </div>

    <!-- 雷达图 -->
    <div class="radar-wrap" v-if="scoredTopics.length > 1">
      <svg :viewBox="`0 0 ${size} ${size}`" class="radar-svg">
        <polygon v-for="level in 4" :key="level" :points="gridPoints(level / 4)" fill="none" stroke="#e2e8f0" stroke-width="1" />
        <line v-for="(t, i) in scoredTopics" :key="'axis-' + i" :x1="cx" :y1="cy" :x2="point(i, 1).x" :y2="point(i, 1).y" stroke="#e2e8f0" stroke-width="1" />
        <polygon :points="dataPoints" fill="rgba(15,23,42,0.06)" stroke="#1e293b" stroke-width="1.5" />
        <circle v-for="(t, i) in scoredTopics" :key="'dot-' + i" :cx="point(i, t.score / 100).x" :cy="point(i, t.score / 100).y" :r="3" fill="#1e293b" />
      </svg>
      <div class="radar-labels">
        <div v-for="(t, i) in scoredTopics" :key="'label-' + i" class="radar-label" :style="labelStyle(i)">{{ t.topic.length > 4 ? t.topic.substring(0, 4) + '…' : t.topic }}</div>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else class="empty-hint">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/><path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"/></svg>
      <p class="empty-title">暂无数据</p>
      <p class="empty-desc">发送消息后自动生成能力画像</p>
    </div>

    <!-- 🆕 错题复习入口 -->
    <div v-if="totalWrongQuestions > 0" class="review-entry-card">
      <div class="review-entry-icon">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      </div>
      <div class="review-entry-info">
        <div class="review-entry-title">错题复习</div>
        <div class="review-entry-desc">{{ wrongQuestionTopics.length }} 个方向 · {{ totalWrongQuestions }} 道错题待复习</div>
      </div>
      <button class="review-entry-btn" @click="$emit('startReview')" title="进入复习">→</button>
    </div>

    <!-- 当前方向标志 -->
    <div class="current-tag" v-if="currentTopic">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      <span>当前：{{ currentTopic }}</span>
    </div>

    <!-- 需重点复习 -->
    <div class="group" v-if="focusTopics.length > 0">
      <div class="group-header">需重点复习</div>
      <div class="card-list">
        <div v-for="t in focusTopics" :key="t.topic" class="card card-focus">
          <div class="card-row" @click="toggleTopic(t.topic)">
            <span class="card-name">{{ t.topic }}</span>
            <span class="card-score" :class="scoreClass(t)">{{ scoreText(t) }}</span>
            <span class="card-meta">{{ t.questionCount }} 题</span>
            <svg class="card-chevron" :class="{ rotated: expandedTopics.has(t.topic) }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>
          </div>
          <div class="bar"><div class="bar-fill" :style="{ width: t.score + '%' }"></div></div>
          <Transition name="slide">
            <div v-if="expandedTopics.has(t.topic)" class="weak-list">
              <div class="weak-label">涉及知识点</div>
              <div class="weak-tags">
                <button v-for="(q, kp) in t.wrongQuestions" :key="kp" class="tag" @click.stop="showWrongQuestion(t.topic, kp, q)">{{ kp }}</button>
                <span v-if="!t.wrongQuestions || Object.keys(t.wrongQuestions).length === 0" class="weak-empty">答题后自动识别</span>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <!-- 已考察方向 -->
    <div class="group" v-if="reviewedTopics.length > 0">
      <div class="group-header">已考察</div>
      <div class="card-list">
        <div v-for="t in reviewedTopics" :key="t.topic" class="card">
          <div class="card-row">
            <span class="card-name">{{ t.topic }}</span>
            <span class="card-score" :class="scoreClass(t)">{{ scoreText(t) }}</span>
            <span class="card-meta">{{ t.questionCount }} 题</span>
          </div>
          <div class="bar"><div class="bar-fill" :style="{ width: t.score + '%' }"></div></div>
        </div>
      </div>
    </div>

    <!-- 待考察 -->
    <div class="group" v-if="unscoredTopics.length > 0">
      <div class="group-header">待考察</div>
      <div class="card-list">
        <div v-for="t in unscoredTopics" :key="t.topic" class="card card-muted">
          <span class="card-name">{{ t.topic }}</span>
          <span class="card-pending">—</span>
        </div>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="actions">
      <button class="btn btn-primary" @click="getAISuggestion" :disabled="aiSuggestionLoading">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2"/><path d="M20 14h2"/><path d="M15 13v2"/><path d="M9 13v2"/></svg>
        {{ aiSuggestionLoading ? '生成中…' : '学习建议' }}
      </button>
      <button class="btn btn-ghost" @click="handleCleanup">修复归类</button>
      <button class="btn btn-ghost" @click="handleReset">重置画像</button>
    </div>

    <!-- 错题详情弹窗 -->
    <div v-if="showWrongModal" class="overlay" @click.self="closeWrongModal">
      <div class="modal">
        <div class="modal-head">
          <h4 class="modal-title">{{ wrongModalTitle }}</h4>
          <button class="icon-btn" @click="closeWrongModal">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div v-if="wrongModalQuestion" class="detail-section">
            <div class="detail-label">📝 题目</div>
            <div class="question-content">{{ wrongModalQuestion }}</div>
          </div>
          <div v-else class="detail-empty">暂无数据</div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-ghost" @click="closeWrongModal">关闭</button>
        </div>
      </div>
    </div>

    <!-- AI建议弹窗 -->
    <div v-if="showSuggestion" class="overlay" @click.self="showSuggestion = false">
      <div class="modal">
        <div class="modal-head">
          <h4 class="modal-title">AI 学习建议</h4>
          <button class="icon-btn" @click="showSuggestion = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">{{ aiSuggestionText || '暂无数据' }}</div>
        <div class="modal-foot">
          <button class="btn btn-ghost" @click="showSuggestion = false">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, reactive } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getProfileSummary, resetProfile as resetProfileApi, cleanupProfile } from '../api'

const renderMarkdown = (text) => {
  if (!text) return ''
  // 🔴 前置处理：对漏空格的常见代码场景做补全
  let normalized = text
    // 逗号、冒号、括号后缺空格
    .replace(/，(?!\s|$)/g, '， ')
    .replace(/(?<=[a-zA-Z0-9)]),(?=[a-zA-Z(])/g, ', ')
    // JVM 参数连在一起（如 -Xms1g-Xmx2g → -Xms1g -Xmx2g）
    .replace(/(-[a-zA-Z]:?\S+)(-[a-zA-Z])/g, '$1 $2')
    // 变量类型和变量名连在一起（如 Booleanlocked → Boolean locked）
    .replace(/(\b[A-Z][a-z]+)([a-z]{2,}\b)/g, (match, type, name) => {
      const types = new Set(['Boolean', 'Integer', 'Long', 'String', 'Double', 'Float', 'Short', 'Byte', 'Character', 'Object', 'Class', 'Thread', 'List', 'Set', 'Map', 'Queue', 'Array', 'Enum', 'Exception', 'Runtime', 'System'])
      return types.has(type) ? type + ' ' + name : match
    })

  const rawHtml = marked(normalized, { breaks: true })
  const cleanHtml = DOMPurify.sanitize(rawHtml, { ADD_ATTR: ['target'] })
  return cleanHtml
}

const props = defineProps({
  topics: { type: Array, default: () => [] },
  currentTopic: { type: String, default: '' },
  chatId: { type: String, default: '' },
})

const emit = defineEmits(['refresh', 'close', 'startReview'])

const size = 280
const cx = size / 2
const cy = size / 2
const radius = size / 2 - 28

const showSuggestion = ref(false)
const aiSuggestionText = ref('')
const aiSuggestionLoading = ref(false)

// 🔴 错题详情弹窗
const showWrongModal = ref(false)
const wrongModalTitle = ref('')
const wrongModalQuestion = ref('')

const showWrongQuestion = (topic, kp, question) => {
  wrongModalTitle.value = `${topic} · ${kp}`
  wrongModalQuestion.value = question || '（题目内容未记录）'
  showWrongModal.value = true
}

const closeWrongModal = () => {
  showWrongModal.value = false
  wrongModalTitle.value = ''
  wrongModalQuestion.value = ''
}

// 🔴 [四大改进-P2] 折叠展开状态：必须用 reactive() 包裹 Set
const expandedTopics = reactive(new Set())

const toggleTopic = (topicName) => {
  if (expandedTopics.has(topicName)) {
    expandedTopics.delete(topicName)
  } else {
    expandedTopics.add(topicName)
  }
}

// 🆕 错题复习入口数据
const wrongQuestionTopics = computed(() => {
  return props.topics
    .filter(t => t.wrongQuestions && Object.keys(t.wrongQuestions).length > 0)
    .map(t => ({
      topic: t.topic,
      count: Object.keys(t.wrongQuestions).length,
    }))
})
const totalWrongQuestions = computed(() => {
  return wrongQuestionTopics.value.reduce((sum, t) => sum + t.count, 0)
})

// 有评分的方向
const scoredTopics = computed(() => props.topics.filter(t => t.questionCount > 0))
// 无评分的方向
const unscoredTopics = computed(() => props.topics.filter(t => t.questionCount === 0))

// 综合等级（纯文本，无颜色）
const overallLevel = computed(() => {
  const scored = scoredTopics.value
  if (scored.length === 0) return '暂无评分'
  const totalAvg = scored.reduce((s, t) => s + t.avgScore, 0) / scored.length
  return `${totalAvg.toFixed(1)} / 5`
})

// 总答题数
const totalQuestions = computed(() => props.topics.reduce((s, t) => s + (t.questionCount || 0), 0))

// 需重点复习：薄弱或带薄弱点的方向，升序排列
const focusTopics = computed(() => scoredTopics.value
  .filter(t => {
    const hasWrongQ = t.wrongQuestions && Object.keys(t.wrongQuestions).length > 0
    return t.avgScore < 2.5 || (t.weakPoints?.length || 0) > 0 || hasWrongQ
  })
  .sort((a, b) => a.avgScore - b.avgScore))

// 已考察方向：其余方向，分数降序
const reviewedTopics = computed(() => scoredTopics.value
  .filter(t => !focusTopics.value.some(focus => focus.topic === t.topic))
  .sort((a, b) => b.avgScore - a.avgScore))

// 格式化分数显示
const scoreText = (topic) => `${Number(topic.avgScore || 0).toFixed(1)} / 5`

// 分数对应的等级样式
const scoreClass = (topic) => {
  const avg = topic.avgScore || 0
  if (avg >= 3.5) return 'lvl-high'
  if (avg >= 2.5) return 'lvl-mid'
  return 'lvl-low'
}

// 雷达图计算
const gridPoints = (ratio) => {
  if (!scoredTopics.value.length) return ''
  return scoredTopics.value.map((_, i) => {
    const p = point(i, ratio)
    return `${p.x},${p.y}`
  }).join(' ')
}

const point = (index, ratio) => {
  const angle = (Math.PI * 2 * index) / scoredTopics.value.length - Math.PI / 2
  return {
    x: cx + radius * ratio * Math.cos(angle),
    y: cy + radius * ratio * Math.sin(angle),
  }
}

const dataPoints = computed(() => {
  if (!scoredTopics.value.length) return ''
  return scoredTopics.value.map((t, i) => {
    const p = point(i, t.score / 100)
    return `${p.x},${p.y}`
  }).join(' ')
})

const labelStyle = (index) => {
  const p = point(index, 1.18)
  return { left: `${p.x}px`, top: `${p.y}px`, transform: 'translate(-50%, -50%)' }
}

// 获取 AI 学习建议
const getAISuggestion = async () => {
  if (!props.chatId) return
  aiSuggestionLoading.value = true
  try {
    const res = await getProfileSummary(props.chatId)
    const data = res.data
    aiSuggestionText.value = (data?.summary || '') + '\n\n' + (data?.aiSuggestion || '')
    showSuggestion.value = true
  } catch (e) {
    aiSuggestionText.value = '获取建议失败，请稍后重试。'
    showSuggestion.value = true
  } finally {
    aiSuggestionLoading.value = false
  }
}

// 重置画像
const handleReset = async () => {
  if (!confirm('确定重置能力画像吗？所有评分数据将被清除。')) return
  try {
    await resetProfileApi(props.chatId)
    emit('refresh')
  } catch (e) {
    console.warn('重置画像失败', e)
  }
}

const handleCleanup = async () => {
  try {
    const res = await cleanupProfile(props.chatId)
    const moved = res.data?.moved || 0
    alert(`归类修复完成：移动了 ${moved} 个知识点到正确方向。`)
    emit('refresh')
  } catch (e) {
    console.warn('清理画像失败', e)
    alert('修复失败，请重试')
  }
}
</script>

<style scoped>
/* ===== Modern sidebar panel — shadcn/ui inspired =====
 * Palette:
 *   bg: #fff
 *   text: #0f172a / #475569 / #94a3b8
 *   border: #e2e8f0 / #f1f5f9
 *   accent: #334155 (slate-700, only on primary button)
 *   radar: #1e293b (slate-800)
 *   score colors: subtle, not bright
 *
 * Rule: no strong purple, no filled colored backgrounds on cards.
 * Cards are white with a 1px border and a whisper shadow.
 */

.panel {
  width: 100%; height: 100%;
  display: flex; flex-direction: column; gap: 16px;
  padding: 20px 16px 24px; overflow-y: auto;
  font-size: 13px; color: #0f172a;
}

/* ---- Header ---- */
.panel-header {
  display: flex; align-items: center; justify-content: space-between;
}
.panel-title-row {
  display: flex; align-items: center; gap: 8px;
}
.panel-icon {
  color: #94a3b8; flex-shrink: 0;
}
.panel-title {
  font-size: 15px; font-weight: 600; color: #0f172a;
}
.panel-badge {
  font-size: 11px; font-weight: 600; color: #64748b;
  background: #f1f5f9; padding: 2px 8px; border-radius: 6px;
}
.panel-actions {
  display: flex; align-items: center; gap: 4px;
}

.icon-btn {
  width: 28px; height: 28px; border-radius: 6px; border: none;
  background: transparent; cursor: pointer; font-size: 14px;
  display: flex; align-items: center; justify-content: center;
  color: #94a3b8; transition: all .15s;
}
.icon-btn:hover { background: #f1f5f9; color: #475569; }

/* ---- Stats cards ---- */
.stats-row {
  display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px;
}
.stat-card {
  background: #fff; border: 1px solid #f1f5f9; border-radius: 10px;
  padding: 12px 8px; text-align: center;
  display: flex; flex-direction: column; gap: 4px;
}
.stat-value {
  font-size: 18px; font-weight: 700; color: #0f172a; line-height: 1.1;
}
.stat-label {
  font-size: 11px; color: #94a3b8; font-weight: 500;
}

/* ---- Radar ---- */
.radar-wrap {
  position: relative; width: 100%; aspect-ratio: 1;
  max-width: 260px; margin: 0 auto;
}
.radar-svg { width: 100%; height: 100%; }
.radar-labels {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  pointer-events: none;
}
.radar-label {
  position: absolute; font-size: 10px; color: #94a3b8;
  white-space: nowrap; text-align: center;
}

/* ---- Empty ---- */
.empty-hint {
  text-align: center; padding: 32px 8px;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.empty-title { margin: 0; font-size: 14px; font-weight: 600; color: #94a3b8; }
.empty-desc { margin: 0; font-size: 12px; color: #cbd5e1; }

/* ---- Current topic ---- */
.current-tag {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #94a3b8; padding: 0 2px;
}

/* ---- 🆕 错题复习入口 ---- */
.review-entry-card {
  display: flex; align-items: center; gap: 16px;
  background: #fff;
  border: 1px solid #f1f5f9; border-radius: 14px;
  padding: 20px;
}
.review-entry-icon {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0;
  background: #f8fafc;
  color: #3b82f6;
  display: flex; align-items: center; justify-content: center;
}
.review-entry-info { flex: 1; min-width: 0; }
.review-entry-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.review-entry-desc { font-size: 11px; color: #94a3b8; margin-top: 3px; }
.review-entry-btn {
  width: 38px; height: 38px; border-radius: 50%; padding: 0;
  font-size: 16px; font-weight: 600;
  cursor: pointer; border: none; white-space: nowrap; transition: all .2s;
  background: #3b82f6; color: #fff; flex-shrink: 0;
  box-shadow: 0 2px 10px rgba(59,130,246,.25);
  display: flex; align-items: center; justify-content: center;
}
.review-entry-btn:hover {
  background: #2563eb;
  box-shadow: 0 4px 16px rgba(37,99,235,.35);
  transform: scale(1.05);
}

/* ---- Sections ---- */
.group { display: flex; flex-direction: column; gap: 8px; }
.group-header {
  font-size: 11px; font-weight: 600; color: #94a3b8;
  text-transform: uppercase; letter-spacing: 0.5px;
}

/* ---- Cards ---- */
.card-list { display: flex; flex-direction: column; gap: 6px; }
.card {
  background: #fff; border: 1px solid #f1f5f9; border-radius: 10px;
  padding: 10px 12px; transition: border-color .2s;
}
.card:hover { border-color: #e2e8f0; }
.card-focus { }
.card-muted { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; }

.card-row {
  display: flex; align-items: center; gap: 8px;
  cursor: pointer; user-select: none;
}
.card-name { font-weight: 500; font-size: 13px; color: #0f172a; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-score { font-size: 12px; font-weight: 600; white-space: nowrap; }
.card-meta { font-size: 11px; color: #94a3b8; white-space: nowrap; }
.card-pending { font-size: 13px; color: #cbd5e1; }

.card-chevron { color: #cbd5e1; flex-shrink: 0; transition: transform .2s; }
.card-chevron.rotated { transform: rotate(180deg); }

/* Score level colors (subtle, gray-based) */
.lvl-high { color: #059669; }
.lvl-mid { color: #d97706; }
.lvl-low { color: #dc2626; }

/* ---- Progress bar ---- */
.bar { height: 3px; background: #f1f5f9; border-radius: 2px; margin-top: 8px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 2px; background: #0f172a; transition: width .5s ease; }

/* ---- Weak points (expandable) ---- */
.weak-list { margin-top: 8px; padding-top: 8px; border-top: 1px solid #f1f5f9; }
.weak-label { font-size: 10px; font-weight: 600; color: #94a3b8; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; }
.weak-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.tag {
  display: inline-block; padding: 3px 8px; font-size: 11px; font-weight: 500;
  color: #475569; background: #f8fafc; border: 1px solid #f1f5f9;
  border-radius: 6px; cursor: pointer; transition: all .15s;
}
.tag:hover { background: #f1f5f9; border-color: #e2e8f0; color: #0f172a; }
.weak-empty { font-size: 12px; color: #cbd5e1; }

/* slide transition */
.slide-enter-active { transition: all .2s ease; overflow: hidden; }
.slide-leave-active { transition: all .15s ease; overflow: hidden; }
.slide-enter-from { opacity: 0; max-height: 0; }
.slide-enter-to { opacity: 1; max-height: 200px; }
.slide-leave-from { opacity: 1; max-height: 200px; }
.slide-leave-to { opacity: 0; max-height: 0; }

/* ---- Actions ---- */
.actions { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.btn {
  width: 100%; padding: 9px 0; border-radius: 8px; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .15s; text-align: center;
  display: flex; align-items: center; justify-content: center; gap: 6px;
}
.btn-primary {
  background: #1e293b; color: #fff; border: 1px solid #1e293b;
}
.btn-primary:hover:not(:disabled) { background: #334155; }
.btn-primary:disabled { background: #cbd5e1; border-color: #cbd5e1; cursor: not-allowed; }
.btn-ghost {
  background: transparent; color: #94a3b8; border: 1px solid #f1f5f9;
}
.btn-ghost:hover { background: #f8fafc; color: #475569; }

/* ---- Overlays & Modals ---- */
.overlay {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(15,23,42,0.4); z-index: 300;
  display: flex; align-items: center; justify-content: center;
}
.modal {
  background: #fff; border-radius: 14px; width: 92%; max-width: 440px;
  max-height: 75vh; display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(15,23,42,0.18);
}
.modal-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px 12px;
}
.modal-title {
  margin: 0; font-size: 14px; font-weight: 600; color: #0f172a;
  line-height: 1.3; flex: 1;
}
.modal-body {
  padding: 0 20px 20px; overflow-y: auto; flex: 1;
}
.modal-foot {
  padding: 12px 20px; border-top: 1px solid #f1f5f9;
  display: flex; justify-content: flex-end;
}

/* ---- Detail (in modal) ---- */
.detail-section { margin-bottom: 4px; }
.detail-label {
  font-size: 10px; font-weight: 600; color: #94a3b8;
  text-transform: uppercase; letter-spacing: .5px; margin-bottom: 8px;
}
.question-content {
  font-size: 14px; line-height: 1.7; color: #0f172a;
  background: #f8fafc; border-radius: 10px; padding: 14px 16px;
  white-space: pre-wrap; word-break: break-word;
}
.question-content.dim {
  color: #94a3b8; font-size: 13px; font-style: italic;
  background: transparent; padding: 8px 0;
}
.detail-content {
  font-size: 14px; line-height: 1.8; color: #0f172a;
  white-space: break-spaces; word-break: break-word;
  background: #f8fafc; border-radius: 10px; padding: 14px 16px;
}
.detail-content :deep(p) { margin: 0 0 6px; }
.detail-content :deep(p:last-child) { margin-bottom: 0; }
.detail-content :deep(ul), .detail-content :deep(ol) { margin: 4px 0 6px; padding-left: 20px; }
.detail-content :deep(li) { margin-bottom: 4px; }
.detail-content :deep(strong) { font-weight: 600; }
.detail-content :deep(code) {
  background: #f1f5f9; color: #0f172a; padding: 1px 5px;
  border-radius: 4px; font-size: 13px; font-family: 'SF Mono', monospace;
}
.detail-content :deep(pre) {
  background: #1e293b; color: #e2e8f0; border-radius: 8px;
  padding: 12px 14px; margin: 8px 0; overflow-x: auto;
  font-size: 13px; line-height: 1.5; font-family: 'SF Mono', monospace;
}
.detail-content :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.detail-content :deep(br) { display: block; content: ''; margin: 4px 0; }
.detail-empty { color: #94a3b8; font-size: 13px; text-align: center; padding: 24px; }

/* ---- My answers divider ---- */
.my-answers { margin-top: 4px; }
.divider {
  display: flex; align-items: center; gap: 8px; margin: 12px 0 6px;
}
.divider-label {
  font-size: 10px; font-weight: 600; color: #94a3b8; white-space: nowrap;
  text-transform: uppercase; letter-spacing: .3px;
}
.divider-line { flex: 1; height: 1px; background: #f1f5f9; }
.answer-item {
  display: flex; gap: 8px; padding: 6px 0;
  border-bottom: 1px solid #f8fafc;
}
.answer-item:last-child { border-bottom: none; }
.answer-num { font-size: 10px; font-weight: 600; color: #cbd5e1; flex-shrink: 0; width: 18px; text-align: right; padding-top: 2px; }
.answer-text { font-size: 13px; line-height: 1.6; color: #64748b; white-space: pre-wrap; word-break: break-word; }

@media (max-width: 768px) {
  .panel { padding: 16px 12px; }
  .radar-wrap { max-width: 200px; }
  .stats-row { gap: 6px; }
  .stat-card { padding: 10px 6px; }
  .stat-value { font-size: 16px; }
}
</style>
