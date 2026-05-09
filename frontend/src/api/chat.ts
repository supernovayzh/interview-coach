import type { ChatRequest, ChatResponse, InterviewProfile } from '../types';

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
  return {
    sessionId: state.sessionId,
    question,
    targetCompany: state.profile.targetCompany,
    companyTier: state.profile.companyTier,
    targetRole: state.profile.targetRole,
    focusAreas: state.profile.focusAreas,
    resumeSummary: state.profile.resumeSummary,
    interviewGoal: state.profile.interviewGoal
  };
}

export async function askChatStream(
  request: ChatRequest,
  onChunk: (chunk: string) => void,
  handlers: StreamLifecycleHandlers = {}
): Promise<{ sessionId?: string; stage?: ChatResponse['stage'] }> {
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
      resolve({ sessionId, stage });
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

    eventSource.addEventListener('error', () => {
      if (finished) {
        return;
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
