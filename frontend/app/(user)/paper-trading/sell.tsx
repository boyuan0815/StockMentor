import { useLocalSearchParams } from 'expo-router';

import { PaperTradingSellScreen } from '@/screens/paper-trading/paper-trading-sell-screen';

export default function SellRoute() {
  const { from, symbol } = useLocalSearchParams<{ from?: string; symbol?: string }>();

  return <PaperTradingSellScreen from={singleParam(from)} symbol={singleParam(symbol)} />;
}

function singleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
