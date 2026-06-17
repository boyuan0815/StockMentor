import { PlaceholderScreen } from '@/components/foundation/placeholder-screen';

export default function LoginRoute() {
  return (
    <PlaceholderScreen
      eyebrow="Auth placeholder"
      title="Sign in"
      description="The full Basic Auth sign-in form arrives in Phase 2. This placeholder keeps the route ready without storing credentials."
      actions={[
        { label: 'Create account placeholder', href: '/register', variant: 'secondary' },
        { label: 'Back to welcome', href: '/', variant: 'ghost' },
      ]}
    />
  );
}
