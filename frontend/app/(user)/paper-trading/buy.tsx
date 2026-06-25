import { useLocalSearchParams } from 'expo-router';

import { PaperTradingTradeTicketScreen } from '@/screens/paper-trading/paper-trading-trade-ticket-screen';

export default function BuyRoute() {
  const { from, returnTo, searchFrom, searchSymbol, symbol } = useLocalSearchParams<{
    from?: string;
    returnTo?: string;
    searchFrom?: string;
    searchSymbol?: string;
    symbol?: string;
  }>();

  return (
    <PaperTradingTradeTicketScreen
      from={singleParam(from)}
      initialDirection="BUY"
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
