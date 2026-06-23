import { useLocalSearchParams } from 'expo-router';

import { PaperTradingTransactionDetailScreen } from '@/screens/paper-trading/paper-trading-transaction-detail-screen';

export default function TransactionDetailRoute() {
  const { transactionId } = useLocalSearchParams<{ transactionId?: string }>();

  return <PaperTradingTransactionDetailScreen transactionId={singleParam(transactionId) ?? ''} />;
}

function singleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
