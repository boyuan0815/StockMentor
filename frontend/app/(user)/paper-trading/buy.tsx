import { useLocalSearchParams } from 'expo-router';

import { PaperTradingBuyScreen } from '@/screens/paper-trading/paper-trading-buy-screen';

export default function BuyRoute() {
  const { from, returnTo, searchFrom, searchSymbol, symbol } = useLocalSearchParams<{
    from?: string;
    returnTo?: string;
    searchFrom?: string;
    searchSymbol?: string;
    symbol?: string;
  }>();

  return (
    <PaperTradingBuyScreen
      from={singleParam(from)}
      returnTo={singleParam(returnTo)}
      searchFrom={singleParam(searchFrom)}
      searchSymbol={singleParam(searchSymbol)}
      symbol={singleParam(symbol)}
    />
  );
}

function singleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
