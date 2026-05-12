<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import {
  askChatStream,
  buildRequest,
  createSessionId,
  deleteConversationSession,
  fetchConversationEvaluations,
  fetchConversationHistory,
  generateConversationTitle,
  loadAppState,
  saveAppState,
  uploadResume,
  type AppState
} from './api/chat';
import type { ChatMessage, ConversationEvaluation } from './types';

const appState = ref<AppState>(loadAppState());
const input = ref('');
const isSending = ref(false);
const isAiTyping = ref(false);
const isStreaming = ref(false);
const currentAiResponse = ref('');
const error = ref('');
const historyLoading = ref(false);
const evaluationLoading = ref(false);
const activeHistorySessionId = ref('');
const evaluations = ref<ConversationEvaluation[]>([]);
const scrollAnchor = ref<HTMLElement | null>(null);

type RecentSession = {
  sessionId: string;
  updatedAt: number;
  title?: string;
  preview: string;
};

const RECENT_SESSIONS_KEY = 'interview-coach-web-recent-sessions';

function loadRecentSessions(): RecentSession[] {
  try {
    const raw = localStorage.getItem(RECENT_SESSIONS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as RecentSession[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveRecentSessions(sessions: RecentSession[]) {
  localStorage.setItem(RECENT_SESSIONS_KEY, JSON.stringify(sessions.slice(0, 12)));
}

function removeRecentSession(sessionId: string) {
  recentSessions.value = recentSessions.value.filter((item) => item.sessionId !== sessionId);
  saveRecentSessions(recentSessions.value);
}

const recentSessions = ref<RecentSession[]>(loadRecentSessions());

function getMessagePreview(messagesList: ChatMessage[]) {
  const candidate = [...messagesList].reverse().find((message) => message.content.trim());
  const text = candidate?.content?.trim() || '空会话';
  return text.length > 42 ? `${text.slice(0, 42)}...` : text;
}

function upsertRecentSession(sessionId: string, messagesList: ChatMessage[], title?: string) {
  if (!sessionId) return;
  const existing = recentSessions.value.find((item) => item.sessionId === sessionId);
  const nextItem: RecentSession = {
    sessionId,
    updatedAt: Date.now(),
    title: title || existing?.title || getMessagePreview(messagesList),
    preview: getMessagePreview(messagesList)
  };
  recentSessions.value = [
    nextItem,
    ...recentSessions.value.filter((item) => item.sessionId !== sessionId)
  ].slice(0, 12);
  saveRecentSessions(recentSessions.value);
}

async function loadHistoryForSession(sessionId: string, keepCurrentIfEmpty = true, showError = false) {
  if (!sessionId || historyLoading.value) return;
  historyLoading.value = true;
  activeHistorySessionId.value = sessionId;
  try {
    const history = await fetchConversationHistory(sessionId, 80);
    appState.value.sessionId = sessionId;
    if (history.length > 0) {
      messages.value = history.map((item, index) => ({
        id: `${sessionId}-${item.createdAt || index}-${index}`,
        role: item.role === 'user' ? 'user' : 'assistant',
        content: item.content || ''
      }));
    } else if (!keepCurrentIfEmpty) {
      messages.value = [
        {
          id: `welcome-empty-${Date.now()}`,
          role: 'assistant',
          content: '这个会话目前还没有历史消息。你可以直接继续提问。'
        }
      ];
    }
    upsertRecentSession(sessionId, messages.value);
    persist();
  } catch (err) {
    if (showError) {
      error.value = err instanceof Error ? err.message : '加载历史失败';
    } else {
      console.warn('history load failed', err);
    }
  } finally {
    activeHistorySessionId.value = '';
    historyLoading.value = false;
    scrollToBottom();
  }
}

async function loadEvaluationsForSession(sessionId: string, showError = false) {
  if (!sessionId || evaluationLoading.value) return;
  evaluationLoading.value = true;
  try {
    evaluations.value = await fetchConversationEvaluations(sessionId, 20);
  } catch (err) {
    if (showError) {
      error.value = err instanceof Error ? err.message : '加载评分失败';
    } else {
      console.warn('evaluation load failed', err);
    }
  } finally {
    evaluationLoading.value = false;
  }
}
const stageText = computed(() => {
  const map: Record<AppState['stage'], string> = {
    INIT: '初始化',
    COLLECTING_PROFILE: '收集画像',
    READY: '准备就绪',
    INTERVIEWING: '正式陪练'
  };
  return map[appState.value.stage];
});

const currentSessionTitle = computed(() => {
  return recentSessions.value.find((item) => item.sessionId === appState.value.sessionId)?.title || '';
});

marked.setOptions({
  breaks: true,
  gfm: true
});

function renderMarkdown(content: string) {
  const html = marked.parse(content ?? '', { async: false }) as string;
  return DOMPurify.sanitize(html);
}

function escapeHtml(text: string) {
  return (text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function renderPlainText(content: string) {
  return escapeHtml(content ?? '').replace(/\n/g, '<br>');
}

function renderMessageContent(message: ChatMessage) {
  if (isStreaming.value && message.role === 'assistant') {
    return renderPlainText(message.content);
  }
  return renderMarkdown(message.content);
}

const messages = ref<ChatMessage[]>([
  {
    id: 'welcome',
    role: 'assistant',
    content:
      '我是你的 Java 面试陪练。目标公司、岗位、重点方向和简历都可以给我，没填也没关系，我会尽量按你给的信息来模拟。'
  }
]);

const selectedFile = ref<File | null>(null);
const uploadStatus = ref('');

function onResumeFileChange(e: Event) {
  const t = e.target as HTMLInputElement;
  if (t.files && t.files.length > 0) {
    selectedFile.value = t.files[0];
  } else {
    selectedFile.value = null;
  }
}

async function uploadResumeFile() {
  if (!selectedFile.value) return;
  uploadStatus.value = '上传中...';
  try {
    const sid = appState.value.sessionId;
    const result = await uploadResume(sid, selectedFile.value);
    uploadStatus.value = result.resumeSummary ? '解析成功' : '解析完成，无文本';
    try {
      const raw = localStorage.getItem('interview-coach-web-state');
      if (raw) {
        const parsed = JSON.parse(raw);
        parsed.profile = parsed.profile || {};
        parsed.profile.resumeSummary = result.resumeSummary || '';
        localStorage.setItem('interview-coach-web-state', JSON.stringify(parsed));
      }
    } catch {}
  } catch (err) {
    uploadStatus.value = err instanceof Error ? err.message : '上传失败';
  }
}

const quickPrompts = [
  '我想练 Java 后端面试',
  '帮我模拟大厂 Redis 面试',
  '我想重点练计算机网络',
  '请根据我的简历追问我'
];

const profileFields = [
  { key: 'targetCompany', label: '目标公司', placeholder: '例如：美团 / 阿里 / 字节' },
  { key: 'companyTier', label: '公司层级', placeholder: '例如：大厂 / 中厂 / 小厂' },
  { key: 'targetRole', label: '目标岗位', placeholder: '例如：Java后端实习 / 校招' },
  { key: 'focusAreas', label: '重点方向', placeholder: '例如：Redis / MySQL / 网络 / JVM' },
  { key: 'interviewGoal', label: '本次目标', placeholder: '例如：模拟面试 / 查漏补缺 / 复盘简历' }
] as const;

function persist() {
  saveAppState(appState.value);
}

function updateProfile<K extends keyof AppState['profile']>(key: K, value: string) {
  appState.value.profile[key] = value;
  persist();
}

function patchMessageContent(messageId: string, content: string) {
  messages.value = messages.value.map((message) =>
    message.id === messageId ? { ...message, content } : message
  );
}

function resetSession() {
  appState.value = {
    sessionId: createSessionId(),
    profile: {
      targetCompany: '',
      companyTier: '',
      targetRole: '',
        focusAreas: '',
        interviewGoal: ''
    },
    stage: 'INIT'
  };
  messages.value = [
    {
      id: `welcome-${Date.now()}`,
      role: 'assistant',
      content: '会话已重置。现在重新告诉我你的面试画像。'
    }
  ];
  error.value = '';
  upsertRecentSession(appState.value.sessionId, messages.value);
  persist();
}

async function sendQuestion(rawQuestion: string) {
  const question = rawQuestion.trim();
  if (!question || isSending.value) return;

  messages.value.push({
    id: `${Date.now()}-user`,
    role: 'user',
    content: question
  });
  const assistantMessage: ChatMessage = {
    id: `${Date.now()}-assistant`,
    role: 'assistant',
    content: ''
  };
  messages.value.push(assistantMessage);
  input.value = '';
  isSending.value = true;
  isAiTyping.value = true;
  isStreaming.value = true;
  currentAiResponse.value = '';
  error.value = '';

    try {
      const response = await askChatStream(
        buildRequest(question, appState.value),
        (chunk) => {
          currentAiResponse.value += chunk;
          patchMessageContent(assistantMessage.id, currentAiResponse.value);
          scrollToBottom();
        },
        {
          onClose: () => {
            isStreaming.value = false;
          }
        }
      );
      if (response.stage) {
        appState.value.stage = response.stage;
      }
      if (response.sessionId) {
        appState.value.sessionId = response.sessionId;
      }
      // 如果没有收到任何流式 token，移除空的 AI 气泡并提示
      if (!response.receivedAnyChunk) {
        messages.value = messages.value.filter((m) => m.id !== assistantMessage.id);
        error.value = '未收到回答（可能上游模型不可用）';
      }
    await loadEvaluationsForSession(appState.value.sessionId, false);
    upsertRecentSession(appState.value.sessionId, messages.value);
    persist();
  } catch (err) {
    if (!currentAiResponse.value.trim()) {
      messages.value = messages.value.filter((message) => message.id !== assistantMessage.id);
      error.value = err instanceof Error ? err.message : '请求失败';
    }
  } finally {
    isStreaming.value = false;
    isAiTyping.value = false;
    currentAiResponse.value = '';
    isSending.value = false;
  }
}

function fillPrompt(prompt: string) {
  input.value = prompt;
}

function openHistory(sessionId: string) {
  loadHistoryForSession(sessionId, false, false);
  loadEvaluationsForSession(sessionId, false);
}

async function deleteHistorySession(sessionId: string) {
  if (!sessionId) return;
  const confirmed = window.confirm(`确认删除会话 ${sessionId} 吗？该会话的历史消息和评分都会被删除。`);
  if (!confirmed) return;
  try {
    await deleteConversationSession(sessionId);
    removeRecentSession(sessionId);
    if (appState.value.sessionId === sessionId) {
      resetSession();
    } else {
      if (activeHistorySessionId.value === sessionId) {
        activeHistorySessionId.value = '';
      }
      await loadEvaluationsForSession(appState.value.sessionId, false);
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除会话失败';
  }
}

async function saveCurrentSessionToHistory() {
  try {
    const result = await generateConversationTitle(appState.value.sessionId);
    upsertRecentSession(appState.value.sessionId, messages.value, result.title);
    persist();
    error.value = '';
  } catch (err) {
    upsertRecentSession(appState.value.sessionId, messages.value);
    persist();
    error.value = err instanceof Error ? err.message : '标题生成失败，已按默认内容保存';
  }
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    scrollAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  });
}

watch(messages, scrollToBottom, { deep: true });
onMounted(() => {
  recentSessions.value = loadRecentSessions();
  loadEvaluationsForSession(appState.value.sessionId, false);
  scrollToBottom();
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar panel">
      <div class="brand">
        <div class="brand-mark">IC</div>
        <div>
          <h1>Interview Coach</h1>
          <p>Java 面试陪练 agent</p>
        </div>
      </div>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>会话状态</h2>
          <span class="stage-chip">{{ stageText }}</span>
        </div>
        <p class="muted">Session</p>
        <div class="session-title">{{ currentSessionTitle || '未命名会话' }}</div>
        <button class="ghost-btn" @click="resetSession">重置会话</button>
        <button class="ghost-btn secondary-btn" @click="saveCurrentSessionToHistory">保存当前会话</button>
      </section>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>历史会话</h2>
          <span class="muted">History</span>
        </div>
        <p class="muted compact">点开任一历史会话，会把该 session 的消息加载回来，继续同一个会话聊下去。</p>
        <div v-if="recentSessions.length === 0" class="history-empty muted compact">暂时没有历史会话。</div>
        <div v-else class="history-list">
          <div
            v-for="session in recentSessions"
            :key="session.sessionId"
            class="history-item"
            :class="{ active: appState.sessionId === session.sessionId }"
            @click="openHistory(session.sessionId)"
          >
            <div class="history-main">
              <div class="history-session">{{ session.title || '未命名会话' }}</div>
              <div class="history-preview">{{ session.preview }}</div>
            </div>
            <div class="history-side">
              <span v-if="activeHistorySessionId === session.sessionId" class="history-status">载入中</span>
              <span v-else class="history-status">继续</span>
              <button
                class="history-delete-btn"
                type="button"
                title="删除该会话"
                @click.stop="deleteHistorySession(session.sessionId)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>提示</h2>
          <span class="muted">Flow</span>
        </div>
        <ul class="tips">
          <li>画像是参考项，先填哪个都行，不填也能继续聊。</li>
          <li>如果你愿意，可以把简历 PDF 上传给我，我会尽量提取信息。</li>
          <li>如果你只想专项训练某块知识，可以在重点方向里写 Redis / 网络 / JVM 等。</li>
        </ul>
      </section>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>评分与反馈</h2>
          <span class="muted">Eval</span>
        </div>
        <p class="muted compact">这里只展示当前会话最近几次参考评分，方便你回看自己的回答质量。</p>
        <div v-if="evaluationLoading" class="history-empty muted compact">评分加载中...</div>
        <div v-else-if="evaluations.length === 0" class="history-empty muted compact">当前会话还没有评分记录。</div>
        <div v-else class="evaluation-list">
          <article
            v-for="item in evaluations"
            :key="`${item.traceId || 'trace'}-${item.createdAt || ''}-${item.question || ''}`"
            class="evaluation-item"
          >
            <div class="evaluation-head">
              <strong>{{ item.score?.toFixed?.(1) ?? 'N/A' }}</strong>
              <span class="evaluation-meta">参考分数</span>
            </div>
            <div class="evaluation-question">{{ item.question || '未记录问题' }}</div>
            <div v-if="item.feedback" class="evaluation-feedback markdown-body" v-html="renderMarkdown(item.feedback)"></div>
            <div class="evaluation-time muted">{{ item.createdAt || '' }}</div>
          </article>
        </div>
      </section>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>快速提问</h2>
          <span class="muted">Quick Start</span>
        </div>
        <div class="quick-list">
          <button v-for="prompt in quickPrompts" :key="prompt" class="quick-pill" @click="fillPrompt(prompt)">
            {{ prompt }}
          </button>
        </div>
      </section>
    </aside>

    <main class="workspace panel">
      <header class="hero">
        <div>
          <span class="eyebrow">DeepSeek 风格的简洁控制台</span>
          <h2>Java 面试陪练</h2>
          <p>
            先收集用户画像，再根据公司层级、岗位和重点方向进行追问、模拟面试与点评。
          </p>
        </div>
        <div class="hero-meta">
          <span>Stage · {{ stageText }}</span>
          <span>Backend · Spring Boot</span>
          <span>Frontend · Vue 3 + Vite</span>
        </div>
      </header>

      <section class="chat-panel">
        <div class="messages">
          <article
            v-for="message in messages"
            :key="message.id"
            class="message"
            :class="message.role"
          >
            <div class="message-badge">{{ message.role === 'user' ? '你' : '陪练' }}</div>
            <div
              class="message-bubble markdown-body"
              :class="{ streaming: isStreaming && message.role === 'assistant' }"
              v-html="renderMessageContent(message)"
            ></div>
          </article>
          <div v-if="isAiTyping" class="typing-indicator" :class="{ streaming: isStreaming }">
            <span>AI 正在输入</span>
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
          <div ref="scrollAnchor"></div>
        </div>

        <div class="composer">
          <textarea
            v-model="input"
            rows="3"
            :placeholder="appState.stage === 'INIT' ? '先问我目标公司、岗位、重点方向…' : '继续提问，或者让我接着追问'"
            @keydown.enter.exact.prevent="sendQuestion(input)"
          />
          <div class="composer-actions">
            <span class="hint">Enter 发送，Shift+Enter 换行</span>
            <button class="primary-btn" :disabled="isSending" @click="sendQuestion(input)">
              {{ isSending ? '发送中…' : '发送' }}
            </button>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
        </div>
      </section>
    </main>

    <aside class="profile panel">
      <section class="sidebar-card sticky-block">
        <div class="card-title-row">
          <h2>面试画像</h2>
          <span class="muted">Profile</span>
        </div>
        <p class="muted compact">这些信息只是参考项，填了更贴合，不填也不影响继续面试。</p>

        <div class="field-grid">
          <label v-for="field in profileFields" :key="field.key" class="field-item">
            <span>{{ field.label }}</span>
            <input
              :value="appState.profile[field.key]"
              :placeholder="field.placeholder"
              @input="updateProfile(field.key, ($event.target as HTMLInputElement).value)"
            />
          </label>
        </div>

        <div style="margin-top:12px">
          <label class="muted">上传 PDF 简历（AI 将读取并填入画像）</label>
          <input type="file" accept="application/pdf" @change="onResumeFileChange" />
          <button class="ghost-btn" @click="uploadResumeFile" :disabled="!selectedFile">上传并解析</button>
          <p v-if="uploadStatus" class="muted compact">{{ uploadStatus }}</p>
        </div>
      </section>
    </aside>
  </div>
</template>
