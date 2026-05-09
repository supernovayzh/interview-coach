<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  askChat,
  buildRequest,
  createSessionId,
  loadAppState,
  saveAppState,
  type AppState
} from './api/chat';
import type { ChatMessage } from './types';

const appState = ref<AppState>(loadAppState());
const input = ref('');
const isSending = ref(false);
const error = ref('');
const scrollAnchor = ref<HTMLElement | null>(null);
const stageText = computed(() => {
  const map: Record<AppState['stage'], string> = {
    INIT: '初始化',
    COLLECTING_PROFILE: '收集画像',
    READY: '准备就绪',
    INTERVIEWING: '正式陪练'
  };
  return map[appState.value.stage];
});

const messages = ref<ChatMessage[]>([
  {
    id: 'welcome',
    role: 'assistant',
    content:
      '我是你的 Java 面试陪练。先告诉我你的目标公司、岗位、重点方向和简历，我会先收集画像，再进入正式陪练。'
  }
]);

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
  { key: 'resumeSummary', label: '简历摘要', placeholder: '简要说明项目、实习、学校背景' },
  { key: 'interviewGoal', label: '本次目标', placeholder: '例如：模拟面试 / 查漏补缺 / 复盘简历' }
] as const;

function persist() {
  saveAppState(appState.value);
}

function updateProfile<K extends keyof AppState['profile']>(key: K, value: string) {
  appState.value.profile[key] = value;
  persist();
}

function resetSession() {
  appState.value = {
    sessionId: createSessionId(),
    profile: {
      targetCompany: '',
      companyTier: '',
      targetRole: '',
      focusAreas: '',
      resumeSummary: '',
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
  input.value = '';
  isSending.value = true;
  error.value = '';

  try {
    const response = await askChat(buildRequest(question, appState.value));
    messages.value.push({
      id: `${Date.now()}-assistant`,
      role: 'assistant',
      content: response.answer
    });
    if (response.stage) {
      appState.value.stage = response.stage;
    }
    if (response.sessionId) {
      appState.value.sessionId = response.sessionId;
    }
    persist();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请求失败';
  } finally {
    isSending.value = false;
  }
}

function fillPrompt(prompt: string) {
  input.value = prompt;
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    scrollAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  });
}

watch(messages, scrollToBottom, { deep: true });
onMounted(() => {
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
        <code class="session-id">{{ appState.sessionId }}</code>
        <button class="ghost-btn" @click="resetSession">重置会话</button>
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
            <div class="message-bubble">
              <p>{{ message.content }}</p>
            </div>
          </article>
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
        <p class="muted compact">填全以后，agent 会切到正式陪练阶段。</p>

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
      </section>

      <section class="sidebar-card">
        <div class="card-title-row">
          <h2>提示</h2>
          <span class="muted">Flow</span>
        </div>
        <ul class="tips">
          <li>先输入目标公司、岗位、重点方向。</li>
          <li>把你的简历摘要贴进来，agent 会根据简历追问。</li>
          <li>如果你只想专项训练某块知识，可以在重点方向里写 Redis / 网络 / JVM 等。</li>
        </ul>
      </section>
    </aside>
  </div>
</template>
