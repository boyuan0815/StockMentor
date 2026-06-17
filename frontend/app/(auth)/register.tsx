import { PlaceholderScreen } from '@/components/foundation/placeholder-screen';

export default function RegisterRoute() {
  return (
    <PlaceholderScreen
      eyebrow="Auth placeholder"
      title="Create account"
      description="The registration form will be implemented in Phase 2 after this foundation is in place."
      actions={[
        { label: 'Sign in placeholder', href: '/login', variant: 'secondary' },
        { label: 'Back to welcome', href: '/', variant: 'ghost' },
      ]}
    />
  );
}
