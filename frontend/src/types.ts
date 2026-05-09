export type ChatRequest = {
  sessionId: string;
  question: string;
  targetCompany?: string;
  companyTier?: string;
  targetRole?: string;
  focusAreas?: string;
  resumeSummary?: string;
  interviewGoal?: string;
};

export type ChatResponse = {
  answer: string;
  sessionId?: string;
  stage?: 'INIT' | 'COLLECTING_PROFILE' | 'READY' | 'INTERVIEWING';
  missingFields?: string;
};

export type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
};

export type InterviewProfile = {
  targetCompany: string;
  companyTier: string;
  targetRole: string;
  focusAreas: string;
  resumeSummary?: string;
  interviewGoal: string;
};
