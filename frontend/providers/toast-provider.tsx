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
          <View
            accessibilityLiveRegion="polite"
            pointerEvents="none"
            style={[styles.toast, getToastStyle(toast.tone)]}>
            <Text selectable style={[styles.toastText, getToastTextStyle(toast.tone)]}>
              {toast.message}
            </Text>
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
  toast: {
    alignSelf: 'center',
    borderRadius: Radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    left: Spacing.xl,
    maxWidth: 520,
    minHeight: 58,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.lg,
    position: 'absolute',
    right: Spacing.xl,
    top: '44%',
    zIndex: 50,
  },
  neutralToast: {
    backgroundColor: '#052344',
    borderColor: '#0B3A63',
    elevation: 10,
    shadowColor: '#020617',
    shadowOffset: { height: 10, width: 0 },
    shadowOpacity: 0.28,
    shadowRadius: 18,
  },
  errorToast: {
    backgroundColor: '#052344',
    borderColor: '#FDA4AF',
    elevation: 10,
    shadowColor: '#020617',
    shadowOffset: { height: 10, width: 0 },
    shadowOpacity: 0.28,
    shadowRadius: 18,
  },
  toastText: {
    fontSize: 16,
    fontWeight: '500',
    lineHeight: 22,
    textAlign: 'center',
  },
  neutralToastText: {
    color: '#FFFFFF',
  },
  errorToastText: {
    color: '#FFFFFF',
  },
});
