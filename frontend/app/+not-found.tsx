import { PlaceholderScreen } from '@/components/foundation/placeholder-screen';

export default function NotFoundScreen() {
  return (
    <PlaceholderScreen
      eyebrow="Route placeholder"
      title="Page not found"
      description="This route is not part of the StockMentor frontend shell yet."
      actions={[{ label: 'Go to welcome', href: '/' }]}
    />
  );
}
