import { PaperTradingOverviewScreen } from '@/screens/paper-trading/paper-trading-overview-screen';

export default function TransactionsRoute() {
  return <PaperTradingOverviewScreen historyFocusBehavior="always" initialTab="history" />;
}
