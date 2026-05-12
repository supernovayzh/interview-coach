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
  const eventSource = new EventSource(url);

  let sessionId: string | undefined;
  let stage: ChatResponse['stage'] | undefined;
  let receivedAnyChunk = false;

    return await new Promise((resolve, reject) => {
    let finished = false;

    const finish = () => {
      if (finished) {
        return;
      }
      finished = true;
      eventSource.close();
      handlers.onClose?.();
        resolve({ sessionId, stage, receivedAnyChunk });
    };

    const fail = (error: Error) => {
      if (finished) {
        return;
      }
      finished = true;
      eventSource.close();
      handlers.onError?.(error);
      handlers.onClose?.();
      reject(error);
    };

    eventSource.onopen = () => {
      handlers.onOpen?.();
    };

    eventSource.addEventListener('meta', (event) => {
      try {
        const payload = JSON.parse((event as MessageEvent).data) as { sessionId?: string; stage?: ChatResponse['stage'] };
        sessionId = payload.sessionId;
        stage = payload.stage;
      } catch {
        // Ignore malformed meta payloads.
      }
    });

    eventSource.onmessage = (event) => {
      const text = (event as MessageEvent).data as string;
      if (text) {
        receivedAnyChunk = true;
        onChunk(text);
      }
    };

    eventSource.addEventListener('done', (event) => {
      try {
        const payload = JSON.parse((event as MessageEvent).data) as { sessionId?: string; stage?: ChatResponse['stage'] };
        sessionId = payload.sessionId || sessionId;
        stage = payload.stage || stage;
      } catch {
        // Ignore malformed done payloads.
      }
      finish();
    });

    eventSource.addEventListener('error', (ev: Event) => {
      if (finished) {
        return;
      }
      // If server sent a named 'error' event carrying textual data, use it.
      try {
        const me = ev as MessageEvent;
        const payload = me && (me.data as string);
        if (payload && payload.trim()) {
          // If we already received chunks, append a short system message and finish.
          if (receivedAnyChunk) {
            onChunk('\n\n[系统] ' + payload);
            finish();
            return;
          }
          // No chunks received yet — fail with the server-provided reason.
          fail(new Error(payload));
          return;
        }
      } catch (err) {
        // ignore
      }

      if (eventSource.readyState === EventSource.CLOSED) {
        if (receivedAnyChunk) {
          finish();
          return;
        }
        fail(new Error('Request failed'));
      }
    });
  });
}

export async function uploadResume(sessionId: string, file: File): Promise<{ resumeSummary?: string; missingFields?: string }> {
  const fd = new FormData();
  fd.append('sessionId', sessionId);
  fd.append('file', file, file.name);

  const resp = await fetch('/api/v1/profile/uploadResume', {
    method: 'POST',
    body: fd
  });

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

  const resp = await fetch(`${API_BASE}/api/v1/chat/history?${params.toString()}`);
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

  const resp = await fetch(`${API_BASE}/api/v1/chat/evaluations?${params.toString()}`);
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

  const resp = await fetch(`${API_BASE}/api/v1/chat/session-title?${params.toString()}`);
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

  const resp = await fetch(`${API_BASE}/api/v1/chat/session?${params.toString()}`, {
    method: 'DELETE'
  });

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(text || `delete session failed ${resp.status}`);
  }
}
