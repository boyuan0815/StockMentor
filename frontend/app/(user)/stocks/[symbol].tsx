import { useLocalSearchParams } from 'expo-router';

import { StockDetailScreen } from '@/screens/stocks/stock-detail-screen';
import { normalizeStockSymbol } from '@/utils/stock-display';

export default function StockDetailRoute() {
  const { returnTo, searchFrom, searchSymbol, symbol } = useLocalSearchParams<{
    returnTo?: string | string[];
    searchFrom?: string | string[];
    searchSymbol?: string | string[];
    symbol: string | string[];
  }>();

  return (
    <StockDetailScreen
      returnTo={singleParam(returnTo)}
      searchFrom={singleParam(searchFrom)}
      searchSymbol={singleParam(searchSymbol)}
      symbol={normalizeStockSymbol(symbol)}
    />
  );
}

function singleParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
