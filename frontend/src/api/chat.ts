import type { ChatRequest, ChatResponse, ConversationEvaluation, ConversationMessage, InterviewProfile } from '../types';

const STORAGE_KEY = 'interview-coach-web-state';

export type AppState = {
  sessionId: string;
  profile: InterviewProfile;
  stage: 'INIT' | 'COLLECTING_PROFILE' | 'READY' | 'INTERVIEWING';
};

export type StreamLifecycleHandlers = {
  onOpen?: () => void;
  onClose?: () => void;
  onError?: (error: Error) => void;
};

function isNgrokHost(url: string): boolean {
  return /\.ngrok(-free)?\.dev/i.test(url) || /\.ngrok\.io/i.test(url);
}

function withNgrokBypassHeaders(url: string, init?: RequestInit): RequestInit {
  if (!isNgrokHost(url)) {
    return init || {};
  }

  const headers = new Headers(init?.headers || {});
  // Avoid ngrok free-plan interstitial page (ERR_NGROK_6024) for browser requests.
  headers.set('ngrok-skip-browser-warning', '1');
  return {
    ...(init || {}),
    headers
  };
}

export function createSessionId() {
  return `session_${Math.random().toString(36).slice(2, 10)}_${Date.now().toString(36)}`;
}

export function loadAppState(): AppState {
  const fallback: AppState = {
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

  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return fallback;
    }
    const parsed = JSON.parse(raw) as Partial<AppState>;
    return {
      sessionId: parsed.sessionId || fallback.sessionId,
      profile: {
        targetCompany: parsed.profile?.targetCompany || '',
        companyTier: parsed.profile?.companyTier || '',
        targetRole: parsed.profile?.targetRole || '',
        focusAreas: parsed.profile?.focusAreas || '',
        resumeSummary: parsed.profile?.resumeSummary || '',
        interviewGoal: parsed.profile?.interviewGoal || ''
      },
      stage: parsed.stage || 'INIT'
    };
  } catch {
    return fallback;
  }
}

export function saveAppState(state: AppState) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function buildRequest(question: string, state: AppState): ChatRequest {
  const clip = (value: string | undefined, maxChars: number) => {
    const text = (value || '').trim();
    return text.length > maxChars ? `${text.slice(0, maxChars)}…` : text;
  };

  return {
    sessionId: state.sessionId,
    question: clip(question, 600),
    targetCompany: clip(state.profile.targetCompany, 80),
    companyTier: clip(state.profile.companyTier, 80),
    targetRole: clip(state.profile.targetRole, 120),
    focusAreas: clip(state.profile.focusAreas, 200),
    resumeSummary: clip(state.profile.resumeSummary, 1200),
    interviewGoal: clip(state.profile.interviewGoal, 200)
  };
}

export async function askChatStream(
  request: ChatRequest,
  onChunk: (chunk: string) => void,
  handlers: StreamLifecycleHandlers = {}
): Promise<{ sessionId?: string; stage?: ChatResponse['stage']; receivedAnyChunk: boolean }> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const params = new URLSearchParams();
  params.set('sessionId', request.sessionId || '');
  params.set('question', request.question || '');
  params.set('targetCompany', request.targetCompany || '');
  params.set('companyTier', request.companyTier || '');
  params.set('targetRole', request.targetRole || '');
  params.set('focusAreas', request.focusAreas || '');
  params.set('resumeSummary', request.resumeSummary || '');
  params.set('interviewGoal', request.interviewGoal || '');

  const url = `${API_BASE}/api/v1/chat/stream?${params.toString()}`;

  let sessionId: string | undefined;
  let stage: ChatResponse['stage'] | undefined;
  let receivedAnyChunk = false;

  const handleSseEvent = (eventName: string, data: string) => {
    const normalizedName = (eventName || 'message').trim();
    const text = data || '';

    if (normalizedName === 'meta') {
      try {
        const payload = JSON.parse(text) as { sessionId?: string; stage?: ChatResponse['stage'] };
        sessionId = payload.sessionId || sessionId;
        stage = payload.stage || stage;
      } catch {
        // Ignore malformed meta payloads.
      }
      return;
    }

    if (normalizedName === 'done') {
      try {
        const payload = JSON.parse(text) as { sessionId?: string; stage?: ChatResponse['stage'] };
        sessionId = payload.sessionId || sessionId;
        stage = payload.stage || stage;
      } catch {
        // Ignore malformed done payloads.
      }
      return;
    }

    if (normalizedName === 'error') {
      const msg = text.trim() || 'Request failed';
      throw new Error(msg);
    }

    if (text) {
      receivedAnyChunk = true;
      onChunk(text);
    }
  };

  try {
    const response = await fetch(url, withNgrokBypassHeaders(url, {
      method: 'GET'
    }));

    handlers.onOpen?.();

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(errText || `Request failed ${response.status}`);
    }

    if (!response.body) {
      throw new Error('Stream body is empty');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const chunks = buffer.split(/\r?\n\r?\n/);
      buffer = chunks.pop() || '';

      for (const chunk of chunks) {
        if (!chunk.trim()) {
          continue;
        }

        const lines = chunk.split(/\r?\n/);
        let eventName = 'message';
        const dataLines: string[] = [];

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trimStart());
          }
        }

        handleSseEvent(eventName, dataLines.join('\n'));
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split(/\r?\n/);
      let eventName = 'message';
      const dataLines: string[] = [];
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart());
        }
      }
      handleSseEvent(eventName, dataLines.join('\n'));
    }

    handlers.onClose?.();
    return { sessionId, stage, receivedAnyChunk };
  } catch (error) {
    const err = error instanceof Error ? error : new Error('Request failed');
    handlers.onError?.(err);
    handlers.onClose?.();
    throw err;
  }
}

export async function uploadResume(sessionId: string, file: File): Promise<{ resumeSummary?: string; missingFields?: string }> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const fd = new FormData();
  fd.append('sessionId', sessionId);
  fd.append('file', file, file.name);

  const uploadUrl = `${API_BASE}/api/v1/profile/uploadResume`;
  const resp = await fetch(uploadUrl, withNgrokBypassHeaders(uploadUrl, {
    method: 'POST',
    body: fd
  }));

  if (!resp.ok) {
    const t = await resp.text();
    throw new Error(t || `upload failed ${resp.status}`);
  }

  return resp.json();
}

export async function fetchConversationHistory(sessionId: string, limit = 50): Promise<ConversationMessage[]> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const params = new URLSearchParams();
  params.set('sessionId', sessionId || '');
  params.set('limit', String(limit));

  const url = `${API_BASE}/api/v1/chat/history?${params.toString()}`;
  const resp = await fetch(url, withNgrokBypassHeaders(url));
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(text || `history query failed ${resp.status}`);
  }

  return resp.json();
}

export async function fetchConversationEvaluations(sessionId: string, limit = 20): Promise<ConversationEvaluation[]> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const params = new URLSearchParams();
  params.set('sessionId', sessionId || '');
  params.set('limit', String(limit));

  const url = `${API_BASE}/api/v1/chat/evaluations?${params.toString()}`;
  const resp = await fetch(url, withNgrokBypassHeaders(url));
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(text || `evaluation query failed ${resp.status}`);
  }

  return resp.json();
}

export async function generateConversationTitle(sessionId: string): Promise<{ sessionId: string; title: string }> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const params = new URLSearchParams();
  params.set('sessionId', sessionId || '');

  const url = `${API_BASE}/api/v1/chat/session-title?${params.toString()}`;
  const resp = await fetch(url, withNgrokBypassHeaders(url));
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(text || `title generation failed ${resp.status}`);
  }

  return resp.json();
}

export async function deleteConversationSession(sessionId: string): Promise<void> {
  const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
  const params = new URLSearchParams();
  params.set('sessionId', sessionId || '');

  const url = `${API_BASE}/api/v1/chat/session?${params.toString()}`;
  const resp = await fetch(url, withNgrokBypassHeaders(url, {
    method: 'DELETE'
  }));

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(text || `delete session failed ${resp.status}`);
  }
}
