import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { Animated, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type { PaperPortfolioResponse, PaperTradingAccountResponse } from '@/types/paper-trading';
import { formatPaperMoney } from '@/utils/paper-trading-display';

type ResetCardSheetProps = {
  nextSessionNumber: number;
  onClose: () => void;
  onConfirm: () => void;
  pending: boolean;
  startingCash: PaperTradingAccountResponse['startingCash'] | PaperPortfolioResponse['totalPortfolioValue'];
  visible: boolean;
};

export function ResetCardSheet({
  nextSessionNumber,
  onClose,
  onConfirm,
  pending,
  startingCash,
  visible,
}: ResetCardSheetProps) {
  const startingCashText = formatPaperMoney(startingCash);
  const validThru = getResetCardValidThru();
  const maskedSession = `**** **** **** ${String(nextSessionNumber).padStart(4, '0')}`;
  const [mounted, setMounted] = useState(visible);
  const slideAnim = useRef(new Animated.Value(visible ? 1 : 0)).current;

  useEffect(() => {
    if (visible) {
      setMounted(true);
      slideAnim.setValue(0);
      Animated.timing(slideAnim, {
        duration: 230,
        toValue: 1,
        useNativeDriver: true,
      }).start();
      return;
    }

    Animated.timing(slideAnim, {
      duration: 190,
      toValue: 0,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setMounted(false);
      }
    });
  }, [slideAnim, visible]);

  if (!mounted) {
    return null;
  }

  const translateY = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [420, 0],
  });

  return (
    <Modal
      animationType="none"
      onRequestClose={() => {
        if (!pending) {
          onClose();
        }
      }}
      transparent
      visible={mounted}>
      <View style={styles.sheetBackdrop}>
        <Pressable
          accessibilityLabel="Close reset card"
          disabled={pending}
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
      </View>
      <View pointerEvents="box-none" style={styles.sheetLayer}>
        <Animated.View style={[styles.sheet, { transform: [{ translateY }] }]}>
          <View style={styles.dragHandle} />
          <View style={styles.sheetTitleRow}>
            <Text selectable style={styles.sheetTitle}>Reset Portfolio</Text>
            <Pressable
              accessibilityLabel="Close reset sheet"
              accessibilityRole="button"
              disabled={pending}
              onPress={onClose}
              style={styles.sheetClose}>
              <IconSymbol color={Colors.light.text} name="xmark" size={22} />
            </Pressable>
          </View>
          <View style={styles.resetCard}>
            <View style={styles.resetTierBadge}>
              <View style={styles.resetTierDot} />
              <Text selectable style={styles.resetCardKicker}>
                Standard
              </Text>
            </View>
            <View style={styles.resetCardBalance}>
              <Text selectable style={styles.resetCardSub}>
                New Paper Trading Balance
              </Text>
              <Text selectable style={styles.resetCardTitle}>
                {startingCashText}
              </Text>
            </View>
            <Text selectable style={styles.resetCardNumber}>
              {maskedSession}
            </Text>
            <View style={styles.resetCardFooter}>
              <View style={styles.validThruBlock}>
                <Text selectable style={styles.validThruLabel}>
                  VALID THRU
                </Text>
                <Text selectable style={styles.validThruValue}>
                  {validThru}
                </Text>
              </View>
              <Image
                accessibilityLabel="StockMentor"
                contentFit="contain"
                source={require('../../assets/images/stockmentor-icon-transparent-1024.png')}
                style={styles.resetCardLogo}
                tintColor="#FFFFFF"
              />
            </View>
          </View>
          <View style={styles.resetInfo}>
            <Text selectable style={styles.resetInfoTitle}>Attributes</Text>
            <Text selectable style={styles.resetInfoText}>
              Restores simulated Net Assets {'\u00b7'} USD to {startingCashText}.
            </Text>
            <View style={styles.resetDivider} />
            <Text selectable style={styles.resetInfoTitle}>Effect</Text>
            <Text selectable style={styles.resetInfoText}>
              Open positions are cleared, a new session starts, and the action cannot be undone.
            </Text>
          </View>
          <ActionButton
            disabled={pending}
            label={pending ? 'Resetting...' : 'Reset Portfolio'}
            onPress={onConfirm}
            style={styles.redeemButton}
          />
        </Animated.View>
      </View>
    </Modal>
  );
}

function getResetCardValidThru() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = String(date.getFullYear() + 5).slice(-2);
  return `${month}/${year}`;
}

const styles = StyleSheet.create({
  sheetBackdrop: {
    backgroundColor: 'rgba(15, 23, 42, 0.42)',
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheetLayer: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: Colors.light.surface,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    gap: Spacing.lg,
    paddingBottom: Spacing.lg,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
  },
  dragHandle: {
    alignSelf: 'center',
    backgroundColor: '#D1D5DB',
    borderRadius: 999,
    height: 5,
    width: 54,
  },
  sheetTitle: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 22,
    fontWeight: '800',
  },
  sheetTitleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  sheetClose: {
    alignItems: 'center',
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  resetCard: {
    alignSelf: 'center',
    aspectRatio: 1.586,
    backgroundColor: '#111827',
    borderColor: '#334155',
    borderRadius: 20,
    borderWidth: 1,
    gap: Spacing.md,
    minHeight: 214,
    padding: Spacing.lg,
    shadowColor: '#000000',
    shadowOffset: { height: 12, width: 0 },
    shadowOpacity: 0.2,
    shadowRadius: 24,
    width: '88%',
    elevation: 8,
  },
  resetCardTitle: {
    color: '#FFFFFF',
    fontSize: 28,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  resetCardKicker: {
    color: '#CBD5E1',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0,
  },
  resetTierBadge: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    borderColor: 'rgba(255, 255, 255, 0.12)',
    borderRadius: 999,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 7,
    minHeight: 24,
    paddingHorizontal: 11,
  },
  resetTierDot: {
    backgroundColor: '#94A3B8',
    borderRadius: 5,
    height: 10,
    width: 10,
  },
  resetCardSub: {
    color: '#CBD5E1',
    fontSize: 13,
  },
  resetCardBalance: {
    gap: 4,
  },
  resetCardNumber: {
    color: '#FFFFFF',
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
    letterSpacing: 1.2,
    marginTop: 'auto',
  },
  resetCardFooter: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  validThruBlock: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  validThruLabel: {
    color: '#CBD5E1',
    fontSize: 9,
    fontWeight: '600',
    lineHeight: 13,
    textAlign: 'center',
  },
  validThruValue: {
    color: '#FFFFFF',
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
    textAlign: 'center',
  },
  resetCardLogo: {
    height: 30,
    width: 30,
  },
  resetInfo: {
    gap: Spacing.sm,
  },
  resetDivider: {
    backgroundColor: Colors.light.border,
    height: 1,
    marginVertical: Spacing.xs,
  },
  resetInfoTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '800',
  },
  resetInfoText: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  redeemButton: {
    backgroundColor: '#111827',
    borderColor: '#111827',
  },
});
