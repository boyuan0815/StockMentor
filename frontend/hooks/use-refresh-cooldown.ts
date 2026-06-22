import { useCallback, useRef } from 'react';

import { useToast } from '@/providers/toast-provider';

const DEFAULT_COOLDOWN_MS = 1000;

export function useRefreshCooldown(cooldownMs = DEFAULT_COOLDOWN_MS) {
  const lastRefreshAtRef = useRef(0);
  const { showToast } = useToast();

  return useCallback(
    (refresh: () => void) => {
      const now = Date.now();
      if (now - lastRefreshAtRef.current < cooldownMs) {
        showToast('Please wait a moment before refreshing again.');
        return;
      }

      lastRefreshAtRef.current = now;
      refresh();
    },
    [cooldownMs, showToast],
  );
}
