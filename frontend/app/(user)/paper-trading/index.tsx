import { useLocalSearchParams } from 'expo-router';

import { PaperTradingOverviewScreen } from '@/screens/paper-trading/paper-trading-overview-screen';

export default function PaperTradingRoute() {
  const { tab } = useLocalSearchParams<{ tab?: string }>();
  return <PaperTradingOverviewScreen initialTab={tab === 'history' ? 'history' : 'assets'} />;
}
