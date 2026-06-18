export type AuthRole = 'BEGINNER_INVESTOR' | 'ADMIN';

export type AuthUserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export type AuthUserResponse = {
  userId: number;
  email: string;
  username: string;
  role: AuthRole | null;
  status: AuthUserStatus | null;
  onboardingCompleted: boolean | null;
  hasInvestmentProfile: boolean;
  mustCompleteOnboarding: boolean;
  createdAt: string | null;
  lastLoginAt: string | null;
};

export type BasicAuthCredentials = {
  username: string;
  password: string;
};

export type OnboardingMode = 'initial' | 'retake';
