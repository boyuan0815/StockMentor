import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { BackHandler, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import DraggableFlatList, { ScaleDecorator, type RenderItemParams } from 'react-native-draggable-flatlist';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { watchlistApi } from '@/api/watchlist';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { WatchlistStockResponse } from '@/types/stocks';
import { getStockApiErrorMessage } from '@/utils/stock-display';

export function WatchlistEditScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestInFlightRef = useRef(false);
  const originalOrderRef = useRef<string[]>([]);
  const [draftRows, setDraftRows] = useState<WatchlistStockResponse[]>([]);
  const [selectedSymbols, setSelectedSymbols] = useState<Set<string>>(() => new Set());
  const [searchText, setSearchText] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [confirmRemoveVisible, setConfirmRemoveVisible] = useState(false);

  const loadRows = useCallback(async () => {
    if (!credentials || requestInFlightRef.current) {
      if (!credentials) {
        setErrorMessage('Sign in again to edit your watchlist.');
        setIsLoading(false);
      }
      return;
    }

    requestInFlightRef.current = true;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const response = await watchlistApi.getWatchlist(credentials);
      const rows = response.watchlistedStocks ?? [];
      setDraftRows(rows);
      originalOrderRef.current = rows.map((row) => row.symbol);
      setSelectedSymbols(new Set());
    } catch (error) {
      setErrorMessage(getStockApiErrorMessage(error, 'Watchlist could not be loaded.'));
    } finally {
      requestInFlightRef.current = false;
      setIsLoading(false);
    }
  }, [credentials]);

  useFocusEffect(
    useCallback(() => {
      void loadRows();
    }, [loadRows]),
  );

  const closeRoute = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    router.replace('/watchlist' as Href);
  }, [router]);

  const saveAndClose = useCallback(async () => {
    if (!credentials || isSaving) {
      return;
    }

    const nextOrder = draftRows.map((row) => row.symbol);
    if (sameSymbolOrder(originalOrderRef.current, nextOrder)) {
      closeRoute();
      return;
    }

    setIsSaving(true);
    try {
      await watchlistApi.reorderSymbols(credentials, nextOrder);
      originalOrderRef.current = nextOrder;
      closeRoute();
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Watchlist order could not be saved.'), 'error');
    } finally {
      setIsSaving(false);
    }
  }, [closeRoute, credentials, draftRows, isSaving, showToast]);

  useFocusEffect(
    useCallback(() => {
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        void saveAndClose();
        return true;
      });
      return () => subscription.remove();
    }, [saveAndClose]),
  );

  const normalizedSearch = searchText.trim().toLowerCase();
  const searchActive = normalizedSearch.length > 0;
  const visibleRows = useMemo(() => {
    if (!searchActive) {
      return draftRows;
    }
    return draftRows.filter((row) => {
      const companyName = row.companyName?.toLowerCase() ?? '';
      return row.symbol.toLowerCase().includes(normalizedSearch) || companyName.includes(normalizedSearch);
    });
  }, [draftRows, normalizedSearch, searchActive]);

  const selectedCount = selectedSymbols.size;
  const allVisibleSelected = visibleRows.length > 0 && visibleRows.every((row) => selectedSymbols.has(row.symbol));
  const canReorder = !searchActive && !isSaving;

  const toggleSymbol = (symbol: string) => {
    setSelectedSymbols((current) => {
      const next = new Set(current);
      if (next.has(symbol)) {
        next.delete(symbol);
      } else {
        next.add(symbol);
      }
      return next;
    });
  };

  const toggleAllVisible = () => {
    setSelectedSymbols((current) => {
      const next = new Set(current);
      if (allVisibleSelected) {
        visibleRows.forEach((row) => next.delete(row.symbol));
      } else {
        visibleRows.forEach((row) => next.add(row.symbol));
      }
      return next;
    });
  };

  const moveToTop = (symbol: string) => {
    if (!canReorder) {
      return;
    }
    setDraftRows((current) => {
      const index = current.findIndex((row) => row.symbol === symbol);
      if (index <= 0) {
        return current;
      }
      const next = [...current];
      const [row] = next.splice(index, 1);
      next.unshift(row);
      return next;
    });
  };

  const confirmRemove = async () => {
    if (!credentials || selectedCount === 0 || isSaving) {
      return;
    }

    setIsSaving(true);
    try {
      const symbols = Array.from(selectedSymbols);
      const response = await watchlistApi.batchRemoveSymbols(credentials, symbols);
      const remainingRows = response.remainingWatchlistedStocks ?? [];
      setDraftRows(remainingRows);
      originalOrderRef.current = remainingRows.map((row) => row.symbol);
      setSelectedSymbols(new Set());
      setConfirmRemoveVisible(false);
      showToast(`${response.removedSymbols?.length ?? symbols.length} stock(s) removed.`, 'success');
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Selected stocks could not be removed.'), 'error');
    } finally {
      setIsSaving(false);
    }
  };

  const renderItem = ({ drag, isActive, item }: RenderItemParams<WatchlistStockResponse>) => (
    <ScaleDecorator>
      <WatchlistEditRow
        canReorder={canReorder}
        disabled={isSaving}
        drag={drag}
        isActive={isActive}
        onMoveToTop={moveToTop}
        onToggle={toggleSymbol}
        selected={selectedSymbols.has(item.symbol)}
        stock={item}
      />
    </ScaleDecorator>
  );

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: insets.top + Spacing.xs }]}>
        <Pressable
          accessibilityLabel="Back to watchlist"
          accessibilityRole="button"
          disabled={isSaving}
        onPress={() => void saveAndClose()}
        style={({ pressed }) => [styles.headerButton, pressed ? styles.pressed : undefined]}>
          <IconSymbol color={Colors.light.text} name="chevron.left" size={16} />
        </Pressable>
        <Text style={styles.title}>
          All
        </Text>
        <Pressable
          accessibilityLabel="Save watchlist order"
          accessibilityRole="button"
          disabled={isSaving}
          onPress={() => void saveAndClose()}
          style={({ pressed }) => [styles.doneButton, pressed ? styles.pressed : undefined]}>
          <Text style={styles.doneText}>{isSaving ? 'Saving' : 'Done'}</Text>
        </Pressable>
      </View>

      {errorMessage ? <ErrorBanner title="Watchlist edit needs attention" message={errorMessage} /> : null}

      {isLoading ? (
        <View style={styles.loadingWrap}>
          <SkeletonRows count={5} />
        </View>
      ) : (
        <DraggableFlatList
          activationDistance={4}
          animationConfig={{ damping: 22, mass: 0.35, stiffness: 180 }}
          autoscrollThreshold={80}
          contentContainerStyle={[styles.listContent, { paddingBottom: Math.max(94, insets.bottom + 86) }]}
          data={visibleRows}
          keyExtractor={(item) => item.symbol}
          ListEmptyComponent={
            <View style={styles.emptyState}>
          <Text selectable style={styles.emptyTitle}>
                No watchlist symbols
              </Text>
              <Text selectable style={styles.emptyBody}>
                {searchActive ? 'No symbol matches this search.' : 'Add symbols from Search first.'}
              </Text>
            </View>
          }
          ListHeaderComponent={
            <WatchlistEditHeader
              searchActive={searchActive}
              searchText={searchText}
              setSearchText={setSearchText}
            />
          }
          onDragEnd={({ data }) => {
            if (!searchActive) {
              setDraftRows(data);
            }
          }}
          removeClippedSubviews={false}
          renderItem={renderItem}
        />
      )}

      <View style={[styles.footer, { paddingBottom: Spacing.sm }]}>
        <Pressable
          accessibilityLabel={allVisibleSelected ? 'Deselect all visible symbols' : 'Select all visible symbols'}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: allVisibleSelected, disabled: isSaving || visibleRows.length === 0 }}
          disabled={isSaving || visibleRows.length === 0}
          onPress={toggleAllVisible}
          style={({ pressed }) => [styles.selectAllButton, pressed ? styles.pressed : undefined]}>
          <CheckBox checked={allVisibleSelected} disabled={isSaving || visibleRows.length === 0} />
          <Text style={styles.selectAllText}>All</Text>
        </Pressable>
        <Pressable
          accessibilityLabel={`Remove ${selectedCount} selected watchlist stocks`}
          accessibilityRole="button"
          disabled={isSaving || selectedCount === 0}
          onPress={() => setConfirmRemoveVisible(true)}
          style={({ pressed }) => [
            styles.removeButton,
            selectedCount === 0 || isSaving ? styles.removeButtonDisabled : undefined,
            pressed ? styles.pressed : undefined,
          ]}>
          <Text style={styles.removeText}>Remove({selectedCount})</Text>
        </Pressable>
      </View>

      <RemoveConfirmModal
        count={selectedCount}
        onCancel={() => setConfirmRemoveVisible(false)}
        onConfirm={() => void confirmRemove()}
        pending={isSaving}
        visible={confirmRemoveVisible}
      />
    </View>
  );
}

function WatchlistEditHeader({
  searchActive,
  searchText,
  setSearchText,
}: {
  searchActive: boolean;
  searchText: string;
  setSearchText: (value: string) => void;
}) {
  return (
    <View style={styles.listHeader}>
      <View style={styles.searchBox}>
        <TextInput
          accessibilityLabel="Search watchlist symbols"
          autoCapitalize="characters"
          autoCorrect={false}
          onChangeText={setSearchText}
          placeholder="Search symbol or company"
          placeholderTextColor="#94A3B8"
          style={styles.searchInput}
          value={searchText}
        />
        <IconSymbol color={Colors.light.mutedText} name="magnifyingglass" size={18} />
      </View>
      {searchActive ? (
        <Text selectable style={styles.reorderHelp}>
          Clear search to reorder.
        </Text>
      ) : null}
      <View style={styles.tableHeader}>
        <Text style={[styles.tableHeaderText, styles.symbolHeader]}>Symbol/Name</Text>
        <View accessibilityLabel="Move to top column" style={styles.topHeader}>
          <Text style={styles.tableHeaderText}>Top</Text>
        </View>
        <View accessibilityLabel="Sort column" style={styles.sortHeader}>
          <Text style={styles.tableHeaderText}>Sort</Text>
        </View>
      </View>
    </View>
  );
}

function WatchlistEditRow({
  canReorder,
  disabled,
  drag,
  isActive,
  onMoveToTop,
  onToggle,
  selected,
  stock,
}: {
  canReorder: boolean;
  disabled: boolean;
  drag: () => void;
  isActive: boolean;
  onMoveToTop: (symbol: string) => void;
  onToggle: (symbol: string) => void;
  selected: boolean;
  stock: WatchlistStockResponse;
}) {
  return (
    <Pressable
      disabled={disabled}
      delayLongPress={500}
      onLongPress={canReorder ? drag : undefined}
      onPress={() => onToggle(stock.symbol)}
      style={[styles.row, isActive ? styles.rowActive : undefined]}>
      <Pressable
        accessibilityLabel={`${selected ? 'Deselect' : 'Select'} ${stock.symbol}`}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: selected, disabled }}
        disabled={disabled}
        onPress={(event) => {
          event.stopPropagation();
          onToggle(stock.symbol);
        }}
        style={({ pressed }) => [styles.checkArea, pressed ? styles.pressed : undefined]}>
        <CheckBox checked={selected} disabled={disabled} />
      </Pressable>
      <View style={styles.identity}>
        <Text numberOfLines={1} style={styles.company}>
          {stock.symbol}
        </Text>
        <Text numberOfLines={1} style={styles.symbol}>
          {stock.companyName}
        </Text>
      </View>
      <Pressable
        accessibilityLabel={`Move ${stock.symbol} to top`}
        accessibilityRole="button"
        disabled={!canReorder || disabled}
        onPress={(event) => {
          event.stopPropagation();
          onMoveToTop(stock.symbol);
        }}
        style={({ pressed }) => [
          styles.topButton,
          !canReorder || disabled ? styles.disabledAction : undefined,
          pressed ? styles.pressed : undefined,
        ]}>
        <IconSymbol
          color={canReorder ? Colors.light.text : '#CBD5E1'}
          name="arrow.up"
          size={20}
        />
      </Pressable>
      <Pressable
        accessibilityLabel={`Drag ${stock.symbol} to reorder`}
        accessibilityRole="button"
        disabled={!canReorder || disabled}
        delayLongPress={220}
        onLongPress={canReorder ? drag : undefined}
        style={({ pressed }) => [
          styles.dragHandle,
          !canReorder || disabled ? styles.disabledAction : undefined,
          pressed ? styles.pressed : undefined,
        ]}>
        <IconSymbol color={canReorder ? Colors.light.mutedText : '#CBD5E1'} name="line.3.horizontal.decrease" size={22} />
      </Pressable>
    </Pressable>
  );
}

function sameSymbolOrder(first: string[], second: string[]) {
  return first.length === second.length && first.every((symbol, index) => symbol === second[index]);
}

function CheckBox({ checked, disabled }: { checked: boolean; disabled?: boolean }) {
  return (
    <View style={[styles.checkbox, checked ? styles.checkboxChecked : undefined, disabled ? styles.disabledAction : undefined]}>
      {checked ? <IconSymbol color="#FFFFFF" name="checkmark" size={14} /> : null}
    </View>
  );
}

function RemoveConfirmModal({
  count,
  onCancel,
  onConfirm,
  pending,
  visible,
}: {
  count: number;
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
  visible: boolean;
}) {
  return (
    <Modal animationType="fade" onRequestClose={onCancel} transparent visible={visible}>
      <View style={styles.modalBackdrop}>
        <View style={styles.confirmCard}>
          <Text selectable style={styles.confirmTitle}>
            Delete {count} {count === 1 ? 'stock' : 'stocks'}?
          </Text>
          <View style={styles.confirmActions}>
            <Pressable
              accessibilityRole="button"
              disabled={pending}
              onPress={onCancel}
              style={({ pressed }) => [styles.cancelButton, pressed ? styles.pressed : undefined]}>
              <Text style={styles.cancelText}>Cancel</Text>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              disabled={pending}
              onPress={onConfirm}
              style={({ pressed }) => [styles.confirmButton, pressed ? styles.pressed : undefined]}>
              <Text style={styles.confirmText}>Confirm</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  header: {
    alignItems: 'center',
    backgroundColor: Colors.light.background,
    flexDirection: 'row',
    minHeight: 54,
    paddingHorizontal: Spacing.md,
  },
  headerButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 36,
  },
  title: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 20,
    fontWeight: '700',
    paddingLeft: 2,
  },
  doneButton: {
    alignItems: 'center',
    minHeight: 44,
    justifyContent: 'center',
    paddingLeft: Spacing.md,
    paddingRight: Spacing.md,
  },
  doneText: {
    color: '#2563EB',
    fontSize: 16,
    fontWeight: '600',
  },
  loadingWrap: {
    padding: Spacing.md,
  },
  listContent: {
    backgroundColor: Colors.light.surface,
  },
  listHeader: {
    backgroundColor: Colors.light.background,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.xs,
  },
  searchBox: {
    alignItems: 'center',
    backgroundColor: '#F1F5F9',
    borderColor: '#E5E7EB',
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 42,
    paddingHorizontal: Spacing.md,
    marginBottom: Spacing.md,
    marginTop: Spacing.sm,
  },
  searchInput: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 15,
    paddingVertical: 0,
  },
  reorderHelp: {
    color: Colors.light.mutedText,
    fontSize: 12,
    marginTop: Spacing.sm,
  },
  tableHeader: {
    alignItems: 'center',
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 42,
  },
  tableHeaderText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
  },
  symbolHeader: {
    flex: 1,
    paddingLeft: Spacing.md
  },
  topHeader: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 62,
  },
  sortHeader: {
    alignItems: 'flex-end',
    justifyContent: 'flex-end',
    paddingRight: Spacing.md,
    width: 74,
  },
  row: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 62,
    paddingHorizontal: Spacing.md,
  },
  rowActive: {
    backgroundColor: '#F8FAFC',
    shadowColor: '#000000',
    shadowOffset: { height: 8, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 14,
  },
  checkArea: {
    alignItems: 'center',
    height: 48,
    justifyContent: 'center',
    width: 40,
  },
  checkbox: {
    alignItems: 'center',
    borderColor: '#94A3B8',
    borderRadius: 4,
    borderWidth: 1.4,
    height: 20,
    justifyContent: 'center',
    width: 20,
  },
  checkboxChecked: {
    backgroundColor: '#111827',
    borderColor: '#111827',
  },
  identity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  company: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '500',
    lineHeight: 19,
  },
  symbol: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  topButton: {
    alignItems: 'center',
    height: 48,
    justifyContent: 'center',
    width: 62,
  },
  dragHandle: {
    alignItems: 'flex-end',
    height: 48,
    justifyContent: 'center',
    paddingRight: Spacing.md,
    width: 74,
  },
  disabledAction: {
    opacity: 0.35,
  },
  footer: {
    alignItems: 'center',
    backgroundColor: '#EEF2F7',
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    bottom: 0,
    flexDirection: 'row',
    justifyContent: 'space-between',
    left: 0,
    minHeight: 54,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.sm,
    position: 'absolute',
    right: 0,
  },
  selectAllButton: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 40,
    minWidth: 110,
  },
  selectAllText: {
    color: Colors.light.text,
    fontSize: 15,
  },
  removeButton: {
    alignItems: 'center',
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  removeButtonDisabled: {
    opacity: 0.42,
  },
  removeText: {
    color: Colors.light.destructive,
    fontSize: 15,
    fontWeight: '500',
  },
  emptyState: {
    alignItems: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.xl,
    paddingVertical: 72,
  },
  emptyTitle: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '700',
  },
  emptyBody: {
    color: Colors.light.mutedText,
    fontSize: 13,
    textAlign: 'center',
  },
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.48)',
    flex: 1,
    justifyContent: 'center',
    padding: Spacing.xl,
  },
  confirmCard: {
    backgroundColor: Colors.light.surface,
    borderRadius: 16,
    gap: Spacing.lg,
    maxWidth: 320,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.lg,
    width: '86%',
  },
  confirmTitle: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '500',
    textAlign: 'center',
  },
  confirmActions: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  cancelButton: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: 999,
    borderWidth: 1,
    flex: 1,
    minHeight: 42,
    justifyContent: 'center',
  },
  cancelText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '500',
  },
  confirmButton: {
    alignItems: 'center',
    backgroundColor: '#111827',
    borderRadius: 999,
    flex: 1,
    minHeight: 42,
    justifyContent: 'center',
  },
  confirmText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  pressed: {
    opacity: 0.78,
  },
});
