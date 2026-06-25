import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useEffect, useRef } from 'react';
import { AppState, type AppStateStatus } from 'react-native';

type MinuteBoundaryRefreshOptions = {
  enabled?: boolean;
  minimumIntervalMs?: number;
  onRefresh: () => Promise<void> | void;
};

export function useMinuteBoundaryRefresh({
  enabled = true,
  minimumIntervalMs = 50_000,
  onRefresh,
}: MinuteBoundaryRefreshOptions) {
  const appStateRef = useRef<AppStateStatus>(AppState.currentState);
  const inFlightRef = useRef(false);
  const lastBoundaryStartedAtRef = useRef(0);
  const onRefreshRef = useRef(onRefresh);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  const clearTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const runRefresh = useCallback(async (source: 'focus' | 'boundary') => {
    const now = Date.now();
    if (
      !enabled ||
      appStateRef.current !== 'active' ||
      inFlightRef.current ||
      (source === 'boundary' && now - lastBoundaryStartedAtRef.current < minimumIntervalMs)
    ) {
      return;
    }

    inFlightRef.current = true;
    if (source === 'boundary') {
      lastBoundaryStartedAtRef.current = now;
    }
    try {
      await onRefreshRef.current();
    } finally {
      inFlightRef.current = false;
    }
  }, [enabled, minimumIntervalMs]);

  const scheduleNext = useCallback(() => {
    clearTimer();
    if (!enabled || appStateRef.current !== 'active') {
      return;
    }

    const now = new Date();
    const delayMs = Math.max(
      1000,
      (60 - now.getSeconds()) * 1000 - now.getMilliseconds() + 1000,
    );
    timerRef.current = setTimeout(() => {
      void runRefresh('boundary').finally(scheduleNext);
    }, delayMs);
  }, [clearTimer, enabled, runRefresh]);

  useFocusEffect(
    useCallback(() => {
      appStateRef.current = AppState.currentState;
      void runRefresh('focus');
      scheduleNext();

      const subscription = AppState.addEventListener('change', (nextState) => {
        appStateRef.current = nextState;
        if (nextState === 'active') {
          scheduleNext();
        } else {
          clearTimer();
        }
      });

      return () => {
        subscription.remove();
        clearTimer();
      };
    }, [clearTimer, runRefresh, scheduleNext]),
  );
}
