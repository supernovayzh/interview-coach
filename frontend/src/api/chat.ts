import type { ChatRequest, ChatResponse, InterviewProfile } from '../types';

const STORAGE_KEY = 'interview-coach-web-state';

export type AppState = {
  sessionId: string;
  profile: InterviewProfile;
  stage: 'INIT' | 'COLLECTING_PROFILE' | 'READY' | 'INTERVIEWING';
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

export async function askChat(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch('/api/v1/chat/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8'
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed with ${response.status}`);
  }

  return response.json();
}
