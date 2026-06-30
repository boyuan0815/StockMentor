import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  type GestureResponderEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { Colors, Spacing } from '@/constants/theme';
import type { ApiNumber, StockHistoryPointResponse, StockHistoryResponse, StockTimeframe } from '@/types/stocks';
import {
  formatPercent,
  formatTradingDateLabel,
  getMovementColor,
  toNumber,
} from '@/utils/stock-display';

type ChartMode = 'line' | 'candle';

type StockHistoryViewProps = {
  history: StockHistoryResponse | null;
  loading: boolean;
  errorMessage: string | null;
  onSelectTimeframe: (timeframe: StockTimeframe) => void;
  onRetry: () => void;
  pendingTimeframe: boolean;
  selectedTimeframe: StockTimeframe;
  timeframes: StockTimeframe[];
};

type ChartPoint = {
  point: StockHistoryPointResponse;
  sourceIndex: number;
  timestamp: number;
  value: number;
};

type CandlePoint = ChartPoint & {
  close: number;
  high: number;
  low: number;
  open: number;
};

type SelectedDetailItem = {
  label: string;
  tone?: ApiNumber;
  value: string;
};

type SelectedDetail = {
  rows: SelectedDetailItem[][];
  title: string;
};

type BottomMarker = {
  label: string;
  left: number;
  showLabel?: boolean;
};

type WagmiChartsModule = typeof import('react-native-wagmi-charts');

declare const require: (moduleName: 'react-native-wagmi-charts') => WagmiChartsModule;

const CHART_LEFT_GUTTER = 42;
const CHART_RIGHT_GUTTER = 48;
const CHART_BOTTOM_GUTTER = 28;
const CHART_TOP_GUTTER = 8;
const FOCUS_DELAY_MS = 380;
let wagmiChartsModule: WagmiChartsModule | null = null;

function getWagmiCharts() {
  wagmiChartsModule ??= require('react-native-wagmi-charts') as WagmiChartsModule;
  return wagmiChartsModule;
}

export function StockHistoryView({
  errorMessage,
  history,
  loading,
  onSelectTimeframe,
  onRetry,
  pendingTimeframe,
  selectedTimeframe,
  timeframes,
}: StockHistoryViewProps) {
  const { width: viewportWidth } = useWindowDimensions();
  const focusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [mode, setMode] = useState<ChartMode>('line');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [chartFocused, setChartFocused] = useState(false);
  const chartWidth = Math.max(300, viewportWidth - Spacing.md * 2);
  const chartHeight = 260;
  const plotHeight = chartHeight - CHART_TOP_GUTTER - CHART_BOTTOM_GUTTER;
  const plotWidth = Math.max(1, chartWidth - CHART_LEFT_GUTTER - CHART_RIGHT_GUTTER);
  const isIntraday = history?.timeframe === '1D' || history?.timeframe === '5D';

  const linePoints = useMemo(() => buildLinePoints(history), [history]);
  const candlePoints = useMemo(() => buildCandlePoints(history), [history]);
  const lineChartData = useMemo(
    () => linePoints.map(({ timestamp, value }) => ({ timestamp, value })),
    [linePoints],
  );
  const candleChartData = useMemo(
    () => candlePoints.map(({ close, high, low, open, timestamp }) => ({ close, high, low, open, timestamp })),
    [candlePoints],
  );
  const candleAvailable =
    Boolean(history) &&
    !isIntraday &&
    history?.candlestickSupported === true &&
    candlePoints.length === (history.points?.length ?? 0) &&
    candlePoints.length > 0;
  const effectiveMode: ChartMode = mode === 'candle' && candleAvailable ? 'candle' : 'line';

  useEffect(() => {
    setSelectedIndex(Math.max(0, linePoints.length - 1));
    setMode('line');
    setChartFocused(false);
  }, [history?.symbol, history?.timeframe, linePoints.length]);

  useEffect(() => {
    if (!candleAvailable && mode === 'candle') {
      setMode('line');
    }
  }, [candleAvailable, mode]);

  useEffect(() => () => {
    if (focusTimerRef.current) {
      clearTimeout(focusTimerRef.current);
    }
  }, []);

  if (loading && !history) {
    return (
      <View style={styles.stableShell}>
        <Text selectable style={styles.loadingText}>
          Loading chart...
        </Text>
        <View style={styles.chartSkeleton} />
      </View>
    );
  }

  if (errorMessage) {
    return (
      <View style={styles.stack}>
        <ErrorBanner title="History needs attention" message={errorMessage} />
        <ActionButton
          accessibilityHint="Retries the stock history request for the selected timeframe."
          label="Try loading history again"
          onPress={onRetry}
          variant="secondary"
        />
      </View>
    );
  }

  if (!history || linePoints.length === 0) {
    return (
      <EmptyState
        title="No chart points yet"
        description={history?.message ?? 'The backend returned no stored points for this timeframe.'}
      />
    );
  }

  const visiblePoints = effectiveMode === 'candle' ? candlePoints : linePoints;
  const safeSelectedIndex = Math.min(Math.max(selectedIndex, 0), visiblePoints.length - 1);
  const selectedPoint = visiblePoints[safeSelectedIndex] ?? visiblePoints[visiblePoints.length - 1];
  const selectedDetail =
    chartFocused && selectedPoint
      ? effectiveMode === 'candle' && isCandlePoint(selectedPoint)
        ? getCandleDetail(selectedPoint)
        : getLineDetail(history, selectedPoint, linePoints, safeSelectedIndex)
      : null;
  const chartLineColor = getMovementColor(
    history.displayedPercentChange ?? history.displayedAbsoluteChange ?? getChartChangeValue(history, linePoints),
  );
  const scale = getChartScale(history, linePoints, visiblePoints);
  const drawWidth = Math.max(2, plotWidth * getDrawRatio(history, visiblePoints));
  const selectedLineLeft =
    visiblePoints.length <= 1
      ? CHART_LEFT_GUTTER
      : CHART_LEFT_GUTTER + (safeSelectedIndex / (visiblePoints.length - 1)) * drawWidth;
  const referenceLineTop =
    scale.referenceTopPercent === null
      ? null
      : CHART_TOP_GUTTER + (scale.referenceTopPercent / 100) * plotHeight;
  const { CandlestickChart, LineChart } = getWagmiCharts();

  const setIndexFromX = (locationX: number) => {
    const nextIndex = getIndexFromX(locationX, drawWidth, visiblePoints.length);
    setSelectedIndex((current) => (current === nextIndex ? current : nextIndex));
  };

  const clearFocusTimer = () => {
    if (focusTimerRef.current) {
      clearTimeout(focusTimerRef.current);
      focusTimerRef.current = null;
    }
  };

  const startChartFocus = (event: GestureResponderEvent) => {
    clearFocusTimer();
    const initialX = event.nativeEvent.locationX;
    focusTimerRef.current = setTimeout(() => {
      setIndexFromX(initialX);
      setChartFocused(true);
      focusTimerRef.current = null;
    }, FOCUS_DELAY_MS);
  };

  const moveChartFocus = (event: GestureResponderEvent) => {
    if (chartFocused) {
      setIndexFromX(event.nativeEvent.locationX);
    }
  };

  const finishChartFocus = () => {
    clearFocusTimer();
    endChartFocus(setChartFocused, setSelectedIndex, visiblePoints.length);
  };

  return (
    <View style={styles.container}>
      {loading ? (
        <Text selectable style={styles.inlineLoading}>
          Refreshing chart...
        </Text>
      ) : null}

      {selectedDetail ? (
        <View pointerEvents="none" style={styles.detailOverlay}>
          <SelectedDetailPanel detail={selectedDetail} />
        </View>
      ) : null}

      <View
        onTouchCancel={finishChartFocus}
        onTouchEnd={finishChartFocus}
        style={styles.chartShell}>
        <ChartAxisLabels scale={scale} />
        <ChartBottomLabels history={history} points={linePoints} />
        <ChartVerticalGridLines history={history} plotWidth={plotWidth} points={linePoints} />
        <View
          accessibilityLabel="Hold to inspect chart"
          onTouchCancel={finishChartFocus}
          onTouchEnd={finishChartFocus}
          onTouchMove={moveChartFocus}
          onTouchStart={startChartFocus}
          style={styles.chartFocusLayer}
        />
        {chartFocused ? <View style={[styles.inspectLine, { left: selectedLineLeft }]} /> : null}
        {referenceLineTop !== null ? (
          <View style={[styles.referenceLine, { top: referenceLineTop }]} />
        ) : null}
        <View style={[styles.chartPlotArea, { height: plotHeight, width: plotWidth }]}>
          {effectiveMode === 'candle' ? (
            <CandlestickChart.Provider
              data={candleChartData}
              onCurrentIndexChange={(index) => {
                if (chartFocused) {
                  setSelectedIndex(clampIndex(index, candlePoints.length));
                }
              }}
            >
              <CandlestickChart height={plotHeight} width={drawWidth}>
                <CandlestickChart.Candles
                  negativeColor={Colors.light.destructive}
                  positiveColor={Colors.light.success}
                  useAnimations={false}
                />
              </CandlestickChart>
            </CandlestickChart.Provider>
          ) : (
            <LineChart.Provider
              data={lineChartData}
              onCurrentIndexChange={(index) => {
                if (chartFocused) {
                  setSelectedIndex(clampIndex(index, linePoints.length));
                }
              }}
            >
              <LineChart height={plotHeight} width={drawWidth} yGutter={12}>
                <LineChart.Path color={chartLineColor} width={2} />
              </LineChart>
            </LineChart.Provider>
          )}
        </View>
        {loading ? (
          <View
            pointerEvents="none"
            style={[
              styles.chartLoadingOverlay,
              {
                top: CHART_TOP_GUTTER + plotHeight * 0.2,
              },
            ]}>
            <ActivityIndicator color="#052344" size="large" style={styles.chartLoadingSpinner} />
          </View>
        ) : null}
      </View>

      <View style={styles.chartControls}>
        <View accessibilityLabel="Stock history timeframe selector" style={styles.timeframeRow}>
          {timeframes.map((timeframe) => {
            const selected = timeframe === selectedTimeframe;
            const disabled = pendingTimeframe || selected;
            return (
              <Pressable
                accessibilityHint={`Loads ${timeframe} stock history from the backend.`}
                accessibilityLabel={`${timeframe} timeframe`}
                accessibilityRole="button"
                accessibilityState={{ disabled, selected }}
                disabled={disabled}
                key={timeframe}
                onPress={() => onSelectTimeframe(timeframe)}
                style={({ pressed }) => [
                  styles.timeframeButton,
                  selected ? styles.timeframeButtonActive : undefined,
                  pressed && !disabled ? styles.pressed : undefined,
                  disabled && !selected ? styles.disabledButton : undefined,
                ]}>
                <Text style={[styles.timeframeText, selected ? styles.timeframeTextActive : undefined]}>
                  {timeframe}
                </Text>
              </Pressable>
            );
          })}
        </View>
        <View style={styles.modeSlot}>
          {candleAvailable ? (
            <View style={styles.modeTabs}>
            {(['line', 'candle'] as const).map((nextMode) => (
              <Pressable
                accessibilityLabel={`${nextMode === 'line' ? 'Line' : 'Candle'} chart mode`}
                accessibilityRole="button"
                accessibilityState={{ selected: effectiveMode === nextMode }}
                key={nextMode}
                onPress={() => {
                  setMode(nextMode);
                  setChartFocused(false);
                  setSelectedIndex(Math.max(0, (nextMode === 'candle' ? candlePoints : linePoints).length - 1));
                }}
                style={[styles.modeTab, effectiveMode === nextMode ? styles.modeTabActive : undefined]}>
                <Text style={[styles.modeTabText, effectiveMode === nextMode ? styles.modeTabTextActive : undefined]}>
                  {nextMode === 'line' ? 'Line' : 'Candle'}
                </Text>
              </Pressable>
            ))}
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

function SelectedDetailPanel({ detail }: { detail: SelectedDetail }) {
  return (
    <View style={styles.detailPanel}>
      <Text selectable style={styles.detailTitle}>
        {detail.title}
      </Text>
      {detail.rows.map((row, rowIndex) => (
        <View key={`row-${rowIndex}`} style={styles.detailRow}>
          {row.map((item) => (
            <View key={item.label} style={styles.detailItem}>
              <Text selectable style={styles.detailLabel}>
                {item.label}
              </Text>
              <Text
                selectable
                numberOfLines={1}
                style={[
                  styles.detailValue,
                  item.tone === undefined ? undefined : { color: getMovementColor(item.tone) },
                ]}>
                {item.value}
              </Text>
            </View>
          ))}
        </View>
      ))}
    </View>
  );
}

function buildLinePoints(history: StockHistoryResponse | null): ChartPoint[] {
  if (!history) {
    return [];
  }

  return history.points
    .map((point, sourceIndex) => {
      const value = toNumber(point.price ?? point.close ?? point.closePrice);
      const timestamp = getPointTimestamp(point, history.timeframe === '1D' || history.timeframe === '5D');
      if (value === null || timestamp === null) {
        return null;
      }
      return { point, sourceIndex, timestamp, value };
    })
    .filter((point): point is ChartPoint => point !== null);
}

function buildCandlePoints(history: StockHistoryResponse | null): CandlePoint[] {
  if (!history || history.timeframe === '1D' || history.timeframe === '5D') {
    return [];
  }

  return history.points
    .map((point, sourceIndex) => {
      const open = toNumber(point.open ?? point.openPrice);
      const high = toNumber(point.high ?? point.highPrice);
      const low = toNumber(point.low ?? point.lowPrice);
      const close = toNumber(point.close ?? point.closePrice);
      const value = close;
      const timestamp = getPointTimestamp(point, false);
      if ([open, high, low, close, value, timestamp].some((item) => item === null)) {
        return null;
      }
      return {
        close,
        high,
        low,
        open,
        point,
        sourceIndex,
        timestamp,
        value,
      } as CandlePoint;
    })
    .filter((point): point is CandlePoint => point !== null);
}

function getPointTimestamp(point: StockHistoryPointResponse, intraday: boolean) {
  if (intraday && point.timestamp) {
    const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(point.timestamp);
    if (match) {
      const [, year, month, day, hour, minute] = match;
      return new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), 0).getTime();
    }
  }

  const dateValue = point.tradingDate ?? point.timestamp?.slice(0, 10);
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue ?? '');
  if (!match) {
    return null;
  }

  const [, year, month, day] = match;
  return new Date(Number(year), Number(month) - 1, Number(day), 12, 0, 0).getTime();
}

function getLineDetail(
  history: StockHistoryResponse,
  selectedPoint: ChartPoint,
  linePoints: ChartPoint[],
  selectedIndex: number,
): SelectedDetail {
  const point = selectedPoint.point;
  const intraday = history.timeframe === '1D' || history.timeframe === '5D';
  const price = selectedPoint.value;
  const open = toNumber(point.open ?? point.openPrice);
  const high = toNumber(point.high ?? point.highPrice);
  const low = toNumber(point.low ?? point.lowPrice);
  const close = toNumber(point.close ?? point.closePrice ?? point.price ?? null);

  const baseline = getLineBaseline(history, point, linePoints, selectedIndex, intraday);
  const change = baseline !== null && baseline !== 0 ? price - baseline : null;
  const percent = change !== null && baseline !== null && baseline !== 0 ? (change / baseline) * 100 : null;

  return {
    title: intraday
      ? formatIntradayDetailLabel(point.timestamp)
      : formatTradingDateLabel(point.tradingDate ?? point.timestamp?.slice(0, 10)),
    rows: [
      [
        { label: 'Open', value: formatPanelPrice(open) },
        { label: 'High', value: formatPanelPrice(high) },
        { label: 'Chg', tone: change ?? undefined, value: formatPanelSigned(change) },
      ],
      [
        { label: 'Close', value: formatPanelPrice(close ?? price) },
        { label: 'Low', value: formatPanelPrice(low) },
        { label: '% Chg', tone: change ?? undefined, value: percent === null ? '--' : formatPercent(percent) },
      ],
      [{ label: 'Volume', value: formatCompactVolume(point.volume) }],
    ],
  };
}

function getCandleDetail(selectedPoint: CandlePoint): SelectedDetail {
  const dayChange = selectedPoint.close - selectedPoint.open;
  const dayPercent = selectedPoint.open === 0 ? null : (dayChange / selectedPoint.open) * 100;

  return {
    title: formatTradingDateLabel(
      selectedPoint.point.tradingDate ?? selectedPoint.point.timestamp?.slice(0, 10),
    ),
    rows: [
      [
        { label: 'Open', value: formatPanelPrice(selectedPoint.open) },
        { label: 'High', value: formatPanelPrice(selectedPoint.high) },
        { label: 'Day Chg', tone: dayChange, value: formatPanelSigned(dayChange) },
      ],
      [
        { label: 'Close', value: formatPanelPrice(selectedPoint.close) },
        { label: 'Low', value: formatPanelPrice(selectedPoint.low) },
        { label: 'Day % Chg', tone: dayChange, value: dayPercent === null ? '--' : formatPercent(dayPercent) },
      ],
      [{ label: 'Volume', value: formatCompactVolume(selectedPoint.point.volume) }],
    ],
  };
}

function getChartChangeValue(history: StockHistoryResponse | null, linePoints: ChartPoint[]) {
  if (!history || linePoints.length < 2) {
    return 0;
  }

  const latest = linePoints[linePoints.length - 1]?.value;
  const first = linePoints[0]?.value;
  return latest - first;
}

function isCandlePoint(point: ChartPoint): point is CandlePoint {
  return (
    toNumber((point as Partial<CandlePoint>).open ?? null) !== null &&
    toNumber((point as Partial<CandlePoint>).high ?? null) !== null &&
    toNumber((point as Partial<CandlePoint>).low ?? null) !== null &&
    toNumber((point as Partial<CandlePoint>).close ?? null) !== null
  );
}

function getLineBaseline(
  history: StockHistoryResponse,
  point: StockHistoryPointResponse,
  linePoints: ChartPoint[],
  selectedIndex: number,
  intraday: boolean,
) {
  if (history.timeframe === '1D') {
    return toNumber(history.previousClose ?? null);
  }
  if (history.timeframe === '5D') {
    return getPreviousReturnedDayClose(point, linePoints) ?? toNumber(history.previousClose ?? null);
  }
  if (!intraday && selectedIndex > 0) {
    return linePoints[selectedIndex - 1]?.value ?? null;
  }
  return null;
}

function getPreviousReturnedDayClose(point: StockHistoryPointResponse, linePoints: ChartPoint[]) {
  const selectedDate = getPointDate(point);
  if (!selectedDate) {
    return null;
  }
  let previousDate: string | null = null;
  let previousClose: number | null = null;

  for (const candidate of linePoints) {
    const candidateDate = getPointDate(candidate.point);
    if (!candidateDate || candidateDate >= selectedDate) {
      continue;
    }
    if (!previousDate || candidateDate >= previousDate) {
      previousDate = candidateDate;
      previousClose = candidate.value;
    }
  }

  return previousClose;
}

function getPointDate(point: StockHistoryPointResponse) {
  return point.tradingDate ?? point.timestamp?.slice(0, 10) ?? null;
}

function formatPanelPrice(value: ApiNumber) {
  const parsed = toNumber(value);
  return parsed === null ? '--' : parsed.toFixed(3);
}

function formatPanelSigned(value: ApiNumber) {
  const parsed = toNumber(value);
  if (parsed === null) {
    return '--';
  }
  const sign = parsed > 0 ? '+' : parsed < 0 ? '-' : '';
  return `${sign}${Math.abs(parsed).toFixed(3)}`;
}

function formatCompactVolume(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return '--';
  }
  const abs = Math.abs(value);
  if (abs >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(2)}M`;
  }
  if (abs >= 1_000) {
    return `${(value / 1_000).toFixed(2)}K`;
  }
  return value.toLocaleString('en-US');
}

function formatIntradayDetailLabel(value: string | null | undefined) {
  if (!value) {
    return '--';
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value);
  if (!match) {
    return value.replace('T', ' ').slice(0, 16);
  }

  const [, year, month, day, hour, minute] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day), 12, 0, 0);
  const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][date.getDay()];
  const monthLabel = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][
    Number(month) - 1
  ];
  return `${weekday}, ${monthLabel} ${Number(day)}, ${hour}:${minute}`;
}

function getChartScale(
  history: StockHistoryResponse,
  linePoints: ChartPoint[],
  visiblePoints: ChartPoint[],
) {
  const values = visiblePoints.flatMap((point) => {
    if (isCandlePoint(point)) {
      return [point.open, point.high, point.low, point.close];
    }
    return [point.value];
  });
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const reference =
    toNumber(history.displayedPrice) ??
    linePoints[linePoints.length - 1]?.value ??
    null;
  const referenceTopPercent =
    reference === null || reference < min || reference > max ? null : ((max - reference) / range) * 100;
  const baseline = toNumber(history.previousClose ?? null);

  return {
    bottom: min,
    middle: min + range / 2,
    referenceTopPercent,
    rightBottom: baseline && baseline !== 0 ? ((min - baseline) / baseline) * 100 : null,
    rightMiddle: baseline && baseline !== 0 ? ((min + range / 2 - baseline) / baseline) * 100 : null,
    rightTop: baseline && baseline !== 0 ? ((max - baseline) / baseline) * 100 : null,
    top: max,
  };
}

function ChartAxisLabels({
  scale,
}: {
  scale: ReturnType<typeof getChartScale>;
}) {
  return (
    <>
      <View pointerEvents="none" style={[styles.horizontalGridLine, { top: CHART_TOP_GUTTER }]} />
      <View pointerEvents="none" style={[styles.horizontalGridLine, { top: '50%' }]} />
      <View pointerEvents="none" style={[styles.horizontalGridLine, { bottom: CHART_BOTTOM_GUTTER }]} />
      <View pointerEvents="none" style={styles.leftAxis}>
        <Text style={styles.axisText}>{formatPanelPrice(scale.top)}</Text>
        <Text style={styles.axisText}>{formatPanelPrice(scale.middle)}</Text>
        <Text style={styles.axisText}>{formatPanelPrice(scale.bottom)}</Text>
      </View>
      <View pointerEvents="none" style={styles.rightAxis}>
        <Text style={styles.axisText}>{scale.rightTop === null ? '--' : formatPercent(scale.rightTop)}</Text>
        <Text style={styles.axisText}>{scale.rightMiddle === null ? '--' : formatPercent(scale.rightMiddle)}</Text>
        <Text style={styles.axisText}>{scale.rightBottom === null ? '--' : formatPercent(scale.rightBottom)}</Text>
      </View>
    </>
  );
}

function ChartBottomLabels({
  history,
  points,
}: {
  history: StockHistoryResponse;
  points: ChartPoint[];
}) {
  const markers = getBottomMarkers(history, points);
  return (
    <View pointerEvents="none" style={styles.bottomAxis}>
      {markers.map((marker, index) => (
        <Text
          key={`${marker.label}-${index}`}
          numberOfLines={1}
          style={[
            styles.axisText,
            styles.bottomAxisLabel,
            { left: `${marker.left}%` },
          ]}>
          {marker.showLabel === false ? '' : marker.label}
        </Text>
      ))}
    </View>
  );
}

function ChartVerticalGridLines({
  history,
  plotWidth,
  points,
}: {
  history: StockHistoryResponse;
  plotWidth: number;
  points: ChartPoint[];
}) {
  const markers = getBottomMarkers(history, points);
  if (markers.length === 0) {
    return null;
  }

  return (
    <>
      {markers.map((marker, index) => (
        <View
          key={`grid-${marker.label}-${index}`}
          pointerEvents="none"
          style={[styles.verticalGridLine, { left: CHART_LEFT_GUTTER + (marker.left / 100) * plotWidth }]}
        />
      ))}
    </>
  );
}

function getBottomMarkers(history: StockHistoryResponse, points: ChartPoint[]): BottomMarker[] {
  if (history.timeframe === '1D') {
    return [
      { label: '09:30', left: 0 },
      { label: '15:59', left: 100 },
    ];
  }
  if (history.timeframe === '5D') {
    const seen = new Set<string>();
    const days = points.reduce<string[]>((dates, item) => {
      const date = getPointDate(item.point);
      if (date && !seen.has(date)) {
        seen.add(date);
        dates.push(date);
      }
      return dates;
    }, []);
    return days.slice(0, 5).map((date, index) => ({
      label: formatMonthDayLabel(date),
      left: index * 20,
    }));
  }

  const latestDate = getPointDate(points[points.length - 1]?.point);
  const scoped = getExpectedMonthKeys(history.timeframe, latestDate).flatMap((month) => {
    const index = points.findIndex((item) => {
      const date = getPointDate(item.point);
      return Boolean(date && date >= `${month}-01`);
    });
    if (index < 0) {
      return [];
    }
    return [{
      label: formatMonthLabel(month),
      left: points.length <= 1 ? 0 : (index / (points.length - 1)) * 100,
      month,
    }];
  });
  const labelInterval = scoped.length > 6 ? Math.ceil(scoped.length / 4) : 1;
  return scoped.map((marker, index) => ({
    label: formatMonthMarkerLabel(marker.month, index),
    left: marker.left,
    showLabel: scoped.length <= 6 || index === 0 || index === scoped.length - 1 || index % labelInterval === 0,
  }));
}

function getExpectedMonthKeys(timeframe: StockHistoryResponse['timeframe'], latestDate: string | null) {
  const match = /^(\d{4})-(\d{2})-\d{2}$/.exec(latestDate ?? '');
  if (!match) {
    return [];
  }
  const latest = new Date(Number(match[1]), Number(match[2]) - 1, 1);
  const count = timeframe === '1M' ? 1 : timeframe === '3M' ? 3 : timeframe === '1Y' ? 12 : latest.getMonth() + 1;
  const start = new Date(latest.getFullYear(), latest.getMonth() - count + 1, 1);
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth() + index, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
}

function formatMonthDayLabel(value: string) {
  const match = /^\d{4}-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const [, month, day] = match;
  const monthLabel = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][
    Number(month) - 1
  ];
  return `${monthLabel} ${Number(day)}`;
}

function formatMonthLabel(value: string) {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const [, year, month] = match;
  const monthLabel = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][
    Number(month) - 1
  ];
  return Number(month) === 1 ? `${monthLabel} ${year}` : monthLabel;
}

function formatMonthMarkerLabel(value: string, index: number) {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const [, year, month] = match;
  const monthLabel = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][
    Number(month) - 1
  ];
  return index === 0 || Number(month) === 1 ? `${monthLabel} ${year}` : monthLabel;
}

function endChartFocus(
  setChartFocused: (value: boolean) => void,
  setSelectedIndex: (value: number) => void,
  length: number,
) {
  setChartFocused(false);
  setSelectedIndex(Math.max(0, length - 1));
}

function clampIndex(index: number, length: number) {
  if (length <= 0 || !Number.isFinite(index)) {
    return 0;
  }
  return Math.min(Math.max(Math.round(index), 0), length - 1);
}

function getIndexFromX(locationX: number, width: number, length: number) {
  if (length <= 1 || width <= 0 || !Number.isFinite(locationX)) {
    return 0;
  }
  const ratio = Math.min(Math.max(locationX / width, 0), 1);
  return clampIndex(ratio * (length - 1), length);
}

function getDrawRatio(history: StockHistoryResponse, visiblePoints: ChartPoint[]) {
  if (visiblePoints.length === 0) {
    return 1;
  }
  const latest = visiblePoints[visiblePoints.length - 1]?.point;
  if (!latest) {
    return 1;
  }
  if (history.timeframe === '1D') {
    return getRegularSessionMinuteRatio(latest);
  }
  if (history.timeframe === '5D') {
    const dates = Array.from(new Set(visiblePoints.map(({ point }) => getPointDate(point)).filter(Boolean)));
    const latestDate = getPointDate(latest);
    const dayIndex = latestDate ? Math.max(0, dates.indexOf(latestDate)) : dates.length - 1;
    const minuteRatio = getRegularSessionMinuteRatio(latest);
    return Math.min(1, Math.max(0.08, (dayIndex + minuteRatio) / Math.max(1, dates.length)));
  }
  return 1;
}

function getRegularSessionMinuteRatio(point: StockHistoryPointResponse) {
  const match = /[T ](\d{2}):(\d{2})/.exec(point.timestamp ?? '');
  if (!match) {
    return 1;
  }
  const minutes = Number(match[1]) * 60 + Number(match[2]);
  const start = 9 * 60 + 30;
  const end = 15 * 60 + 59;
  return Math.min(1, Math.max(0.02, (minutes - start + 1) / (end - start + 1)));
}

const styles = StyleSheet.create({
  stack: {
    gap: Spacing.md,
  },
  stableShell: {
    gap: Spacing.md,
    justifyContent: 'center',
    minHeight: 360,
    paddingHorizontal: Spacing.md,
  },
  loadingText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '500',
  },
  chartSkeleton: {
    backgroundColor: '#E2E8F0',
    borderRadius: 8,
    height: 260,
  },
  container: {
    backgroundColor: Colors.light.background,
    gap: Spacing.sm,
    minHeight: 318,
    paddingBottom: Spacing.md,
    paddingHorizontal: Spacing.sm,
    paddingTop: Spacing.xs,
    position: 'relative',
  },
  inlineLoading: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '700',
  },
  detailPanel: {
    backgroundColor: 'rgba(241, 245, 249, 0.94)',
    gap: Spacing.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    width: '100%',
  },
  detailOverlay: {
    left: Spacing.sm,
    position: 'absolute',
    right: Spacing.sm,
    top: -92,
    zIndex: 12,
  },
  detailTitle: {
    color: Colors.light.text,
    fontSize: 12,
    fontWeight: '500',
  },
  detailRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  detailItem: {
    alignItems: 'center',
    flex: 1,
    flexDirection: 'row',
    gap: 4,
    justifyContent: 'flex-start',
    minWidth: 0,
  },
  detailLabel: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontWeight: '500',
  },
  detailValue: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  modeTabs: {
    alignSelf: 'center',
    backgroundColor: '#F1F5F9',
    borderRadius: 999,
    flexDirection: 'row',
    padding: 3,
  },
  modeTab: {
    borderRadius: 999,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 6,
  },
  modeTabActive: {
    backgroundColor: '#CBD5E1',
  },
  modeTabText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
  },
  modeTabTextActive: {
    color: Colors.light.text,
    fontWeight: '700',
  },
  chartShell: {
    alignItems: 'center',
    backgroundColor: Colors.light.background,
    height: 260,
    overflow: 'hidden',
    position: 'relative',
  },
  chartFocusLayer: {
    bottom: 0,
    left: CHART_LEFT_GUTTER,
    position: 'absolute',
    right: CHART_RIGHT_GUTTER,
    top: 0,
    zIndex: 4,
  },
  chartPlotArea: {
    left: CHART_LEFT_GUTTER,
    overflow: 'hidden',
    position: 'absolute',
    top: CHART_TOP_GUTTER,
    zIndex: 0,
  },
  chartLoadingOverlay: {
    alignItems: 'center',
    height: 104,
    justifyContent: 'center',
    left: CHART_LEFT_GUTTER,
    position: 'absolute',
    right: CHART_RIGHT_GUTTER,
    zIndex: 5,
  },
  chartLoadingSpinner: {
    transform: [{ scale: 1.8 }],
  },
  inspectLine: {
    backgroundColor: 'rgba(5, 35, 68, 0.28)',
    bottom: CHART_BOTTOM_GUTTER,
    position: 'absolute',
    top: CHART_TOP_GUTTER,
    width: 1,
    zIndex: 3,
  },
  referenceLine: {
    borderStyle: 'dotted',
    borderTopColor: '#F97316',
    borderTopWidth: 1.5,
    left: CHART_LEFT_GUTTER,
    opacity: 0.75,
    position: 'absolute',
    right: CHART_RIGHT_GUTTER,
    zIndex: 1,
  },
  horizontalGridLine: {
    borderTopColor: '#E2E8F0',
    borderTopWidth: 1,
    left: CHART_LEFT_GUTTER,
    opacity: 0.7,
    position: 'absolute',
    right: CHART_RIGHT_GUTTER,
    zIndex: 1,
  },
  verticalGridLine: {
    backgroundColor: '#E2E8F0',
    bottom: CHART_BOTTOM_GUTTER,
    opacity: 0.45,
    position: 'absolute',
    top: CHART_TOP_GUTTER,
    width: 1,
    zIndex: 1,
  },
  leftAxis: {
    bottom: CHART_BOTTOM_GUTTER,
    justifyContent: 'space-between',
    left: 0,
    position: 'absolute',
    top: CHART_TOP_GUTTER,
    zIndex: 2,
  },
  rightAxis: {
    bottom: CHART_BOTTOM_GUTTER,
    justifyContent: 'space-between',
    position: 'absolute',
    right: 0,
    top: CHART_TOP_GUTTER,
    zIndex: 2,
  },
  bottomAxis: {
    bottom: 0,
    height: CHART_BOTTOM_GUTTER,
    left: CHART_LEFT_GUTTER,
    overflow: 'visible',
    position: 'absolute',
    right: CHART_RIGHT_GUTTER,
    zIndex: 2,
  },
  bottomAxisLabel: {
    marginLeft: -38,
    position: 'absolute',
    textAlign: 'center',
    width: 76,
  },
  axisText: {
    color: Colors.light.mutedText,
    fontSize: 10,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  chartControls: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.xs,
  },
  timeframeRow: {
    flex: 1,
    flexDirection: 'row',
    gap: 3,
    justifyContent: 'space-between',
  },
  timeframeButton: {
    alignItems: 'center',
    borderRadius: 999,
    flex: 1,
    justifyContent: 'center',
    minHeight: 30,
    paddingHorizontal: 4,
    paddingVertical: 5,
  },
  timeframeButtonActive: {
    backgroundColor: '#E5E7EB',
  },
  timeframeText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
    lineHeight: 16,
  },
  timeframeTextActive: {
    color: Colors.light.text,
    fontWeight: '800',
  },
  disabledButton: {
    opacity: 0.55,
  },
  modeSlot: {
    alignItems: 'flex-end',
    justifyContent: 'center',
    minWidth: 104,
  },
  pressed: {
    opacity: 0.72,
  },
});
