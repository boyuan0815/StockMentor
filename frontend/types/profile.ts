export type OnboardingOptionDto = {
  optionId: string;
  label: string;
  description: string | null;
};

export type OnboardingQuestionDto = {
  questionId: string;
  text: string;
  required: boolean;
  options: OnboardingOptionDto[];
};

export type OnboardingQuestionResponse = {
  questions: OnboardingQuestionDto[];
};

export type OnboardingAnswerRequest = {
  questionId: string;
  optionId: string;
};

export type OnboardingSubmitRequest = {
  answers: OnboardingAnswerRequest[];
};

export type InvestmentProfileResponse = {
  profileId: number;
  profileVersion: number;
  profileSource: string | null;
  riskTolerance: string | null;
  investmentGoal: string | null;
  experienceLevel: string | null;
  preferredVolatility: string | null;
  preferredHorizon: string | null;
  riskScore: number | null;
  goalScore: number | null;
  experienceScore: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type BehaviorProfileSummaryResponse = {
  behaviorProfileId: number | null;
  behaviorConfidence: string | null;
  behaviorStyle: string | null;
  behaviorRiskScore: number | null;
  behaviorSummaryText: string | null;
  sourceNote: string | null;
  updatedAt: string | null;
};

export type UserProfileResponse = {
  userId: number;
  email: string;
  username: string;
  role: string | null;
  onboardingCompleted: boolean | null;
  investmentProfile: InvestmentProfileResponse | null;
  behaviorSummary: BehaviorProfileSummaryResponse | null;
};
