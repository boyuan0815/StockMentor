import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Radius, Spacing } from '@/constants/theme';

type ToastTone = 'neutral' | 'success' | 'error';

type ToastMessage = {
  id: number;
  message: string;
  tone: ToastTone;
};

type ToastContextValue = {
  showToast: (message: string, tone?: ToastTone) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: PropsWithChildren) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((message: string, tone: ToastTone = 'neutral') => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    setToast({
      id: Date.now(),
      message,
      tone,
    });

    timeoutRef.current = setTimeout(() => {
      setToast(null);
      timeoutRef.current = null;
    }, 2600);
  }, []);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      <View style={styles.root}>
        {children}
        {toast ? (
          <View pointerEvents="box-none" style={styles.overlay}>
            <View
              accessibilityLiveRegion="polite"
              pointerEvents="none"
              style={[styles.toast, getToastStyle(toast.tone)]}>
              <Text selectable style={[styles.toastText, getToastTextStyle(toast.tone)]}>
                {toast.message}
              </Text>
            </View>
          </View>
        ) : null}
      </View>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const value = useContext(ToastContext);
  if (!value) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return value;
}

function getToastStyle(tone: ToastTone) {
  if (tone === 'success') {
    return styles.neutralToast;
  }
  if (tone === 'error') {
    return styles.errorToast;
  }
  return styles.neutralToast;
}

function getToastTextStyle(tone: ToastTone) {
  if (tone === 'error') {
    return styles.errorToastText;
  }
  return styles.neutralToastText;
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  overlay: {
    alignItems: 'center',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 50,
  },
  toast: {
    alignSelf: 'center',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    maxWidth: 320,
    minHeight: 34,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  neutralToast: {
    backgroundColor: 'rgba(15, 23, 42, 0.9)',
    elevation: 5,
    shadowColor: '#020617',
    shadowOffset: { height: 6, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 12,
  },
  errorToast: {
    backgroundColor: 'rgba(127, 29, 29, 0.92)',
    elevation: 5,
    shadowColor: '#020617',
    shadowOffset: { height: 6, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 12,
  },
  toastText: {
    fontSize: 13,
    fontWeight: '400',
    lineHeight: 18,
    textAlign: 'center',
  },
  neutralToastText: {
    color: '#FFFFFF',
  },
  errorToastText: {
    color: '#FFFFFF',
  },
});
