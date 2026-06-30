import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { Image } from 'expo-image';
import { Redirect, type Href, usePathname, useRouter } from 'expo-router';
import { createElement, isValidElement, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';

import { ApiError, normalizeUnknownApiError } from '@/api/errors';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';

const BRAND_NAVY = '#052344';
const ADMIN_HOVER = '#F1F5F9';
const INPUT_BACKGROUND = '#F8FAFC';
const WEB_TRANSITION =
  Platform.OS === 'web'
    ? ({
        transitionDuration: '160ms',
        transitionProperty: 'background-color, border-color, box-shadow, opacity, transform',
        transitionTimingFunction: 'ease',
      } as unknown as ViewStyle)
    : {};
const ADMIN_TABLET_MIN_WIDTH = 768;
const ADMIN_PAGE_SIZE_OPTIONS = [10, 15, 20, 25, 30, 40, 50, 100];

const ADMIN_NAV_ITEM_HEIGHT = 60;
const ADMIN_NAV_ITEM_GAP = 12;

type AdminGateProps = {
  children: ReactNode;
};

type AdminShellProps = {
  children: ReactNode;
};

type AdminPageProps = {
  actions?: ReactNode;
  children: ReactNode;
  eyebrow?: string;
  title: string;
};

type AdminSectionProps = {
  action?: ReactNode;
  children: ReactNode;
  description?: string;
  tone?: 'default' | 'highlight' | 'success';
  title: string;
};

type AdminMetricProps = {
  description?: string;
  label: string;
  tone?: 'danger' | 'neutral' | 'success' | 'warn';
  value: string;
};

type AdminButtonProps = {
  disabled?: boolean;
  label: string;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
  variant?: 'danger' | 'ghost' | 'primary' | 'secondary';
};

type AdminDataColumn<T> = {
  align?: 'left' | 'right';
  key: string;
  render: (item: T) => ReactNode;
  sortValue?: (item: T) => number | string | null | undefined;
  title: string;
  width?: number;
};

type AdminDataTableProps<T> = {
  columns: AdminDataColumn<T>[];
  emptyDescription: string;
  emptyTitle: string;
  keyExtractor: (item: T) => string;
  loading?: boolean;
  onRowPress?: (item: T) => void;
  rows: T[];
};

type AdminPaginationProps = {
  itemLabel: string;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type AdminSearchInputProps = {
  accessibilityLabel: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  value: string;
};

type AdminConfirmModalProps = {
  confirmLabel: string;
  danger?: boolean;
  message?: string;
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
  pendingLabel: string;
  title: string;
  visible: boolean;
};

type SortDirection = 'asc' | 'desc';

const adminNavItems: { href: Href; label: string }[] = [
  { href: '/admin' as Href, label: 'Dashboard' },
  { href: '/admin/users' as Href, label: 'Users' },
  { href: '/admin/ai-suggestions' as Href, label: 'AI monitoring' },
  { href: '/admin/stocks/maintenance' as Href, label: 'Stock maintenance' },
];

export function AdminGate({ children }: AdminGateProps) {
  const { clearSession, isAuthenticated, isBootstrapping, user } = useAuthSession();

  if (isBootstrapping) {
    return <AdminLoadingScreen />;
  }

  if (!isAuthenticated || !user) {
    return <Redirect href={'/login' as Href} />;
  }

  if (user.role !== 'ADMIN') {
    return (
      <AdminAccessDenied
        description="This account is signed in as a beginner investor. Admin routes require an administrator account."
        onLogout={clearSession}
      />
    );
  }

  return <AdminShell>{children}</AdminShell>;
}

export function AdminShell({ children }: AdminShellProps) {
  const { adminToken, clearSession, setAdminToken } = useAuthSession();
  const { width } = useWindowDimensions();
  const isPhone = width < ADMIN_TABLET_MIN_WIDTH;

  if (isPhone) {
    return (
      <AdminPhoneFallback
        adminToken={adminToken}
        onLogout={clearSession}
        onSetToken={setAdminToken}
      />
    );
  }

  if (!adminToken) {
    return (
      <AdminTokenPrompt
        onLogout={clearSession}
        onSaveToken={setAdminToken}
      />
    );
  }

  return (
    <View style={styles.shell}>
      <AdminSideNav onLogout={clearSession} />
      <View style={styles.shellContent}>{children}</View>
    </View>
  );
}

export function useAdminRequest() {
  const { adminToken, credentials, setAdminToken } = useAuthSession();

  const handleAdminError = useCallback(
    (error: unknown) => {
      const apiError = normalizeUnknownApiError(error);
      if (apiError.status === 401) {
        setAdminToken(null);
      }
      return getAdminErrorMessage(apiError);
    },
    [setAdminToken],
  );

  return {
    adminToken,
    credentials,
    handleAdminError,
  };
}

export function AdminPage({ actions, children, eyebrow = 'Admin console', title }: AdminPageProps) {
  return (
    <ScrollView
      contentContainerStyle={styles.pageContent}
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      style={styles.page}>
      <View style={styles.pageHeader}>
        <View style={styles.pageHeaderText}>
          <Text selectable style={styles.eyebrow}>
            {eyebrow}
          </Text>
          <Text selectable style={styles.pageTitle}>
            {title}
          </Text>
        </View>
        {actions ? <View style={styles.pageActions}>{actions}</View> : null}
      </View>
      {children}
    </ScrollView>
  );
}

export function AdminSection({ action, children, description, title, tone = 'default' }: AdminSectionProps) {
  return (
    <View
      style={[
        styles.section,
        tone === 'highlight' ? styles.sectionHighlight : undefined,
        tone === 'success' ? styles.sectionSuccess : undefined,
      ]}>
      <View style={styles.sectionHeader}>
        <View style={styles.sectionHeaderText}>
          <Text selectable style={styles.sectionTitle}>
            {title}
          </Text>
          {description ? (
            <Text selectable style={styles.sectionDescription}>
              {description}
            </Text>
          ) : null}
        </View>
        {action}
      </View>
      {children}
    </View>
  );
}

export function AdminMetric({ description, label, tone = 'neutral', value }: AdminMetricProps) {
  return (
    <View style={styles.metric}>
      <Text selectable style={styles.metricLabel}>
        {label}
      </Text>
      <Text selectable numberOfLines={1} style={[styles.metricValue, getToneTextStyle(tone)]}>
        {value}
      </Text>
      {description ? (
        <Text selectable style={styles.metricDescription}>
          {description}
        </Text>
      ) : null}
    </View>
  );
}

export function AdminButton({
  disabled = false,
  label,
  onPress,
  style,
  variant = 'primary',
}: AdminButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={(state) => [
        styles.adminButton,
        getAdminButtonVariantStyle(variant),
        isHovered(state) ? getAdminButtonHoverStyle(variant) : undefined,
        state.pressed ? styles.adminButtonPressed : undefined,
        disabled ? styles.adminButtonDisabled : undefined,
        style,
      ]}>
      <Text style={[styles.adminButtonText, getAdminButtonTextStyle(variant), disabled ? styles.adminButtonTextDisabled : undefined]}>
        {label}
      </Text>
    </Pressable>
  );
}

export function AdminMetricGrid({ children }: { children: ReactNode }) {
  return <View style={styles.metricGrid}>{children}</View>;
}

export function AdminStatusPill({ tone, value }: { tone?: AdminMetricProps['tone']; value: string }) {
  return (
    <View style={[styles.statusPill, getTonePillStyle(tone ?? 'neutral')]}>
      <Text selectable numberOfLines={1} style={[styles.statusPillText, getToneTextStyle(tone ?? 'neutral')]}>
        {formatAdminEnum(value)}
      </Text>
    </View>
  );
}

export function AdminTabs<T extends string>({
  activeTab,
  tabs,
  onSelect,
}: {
  activeTab: T;
  tabs: { label: string; value: T }[];
  onSelect: (tab: T) => void;
}) {
  return (
    <View style={styles.tabs}>
      {tabs.map((tab) => {
        const active = activeTab === tab.value;
        return (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            key={tab.value}
            onPress={() => onSelect(tab.value)}
            style={(state) => [
              styles.tab,
              active ? styles.tabActive : undefined,
              !active && isHovered(state) ? styles.tabHovered : undefined,
              state.pressed ? styles.pressed : undefined,
            ]}>
            <Text style={[styles.tabText, active ? styles.tabTextActive : undefined]}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function AdminDataTable<T>({
  columns,
  emptyDescription,
  emptyTitle,
  keyExtractor,
  loading = false,
  onRowPress,
  rows,
}: AdminDataTableProps<T>) {
  const [sortState, setSortState] = useState<{ direction: SortDirection; key: string } | null>(null);
  const sortedRows = useMemo(() => {
    if (!sortState) {
      return rows;
    }

    const column = columns.find((item) => item.key === sortState.key);
    if (!column) {
      return rows;
    }

    return [...rows].sort((left, right) => compareAdminSortValues(getSortValue(column, left), getSortValue(column, right), sortState.direction));
  }, [columns, rows, sortState]);

  if (loading) {
    return <SkeletonRows count={5} />;
  }

  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <ScrollView
      contentContainerStyle={styles.tableScrollContent}
      horizontal
      showsHorizontalScrollIndicator
      style={styles.tableScroll}>
      <View style={styles.table}>
        <View style={[styles.tableRow, styles.tableHeaderRow]}>
          {columns.map((column) => {
            const activeSort = sortState?.key === column.key ? sortState.direction : null;
            return (
              <Pressable
                accessibilityLabel={`Sort by ${column.title}`}
                accessibilityRole="button"
                accessibilityState={{ selected: Boolean(activeSort) }}
                key={column.key}
                onPress={() => setSortState((current) => getNextSortState(current, column.key))}
                style={(state) => [
                  styles.tableHeaderCell,
                  getColumnStyle(column),
                  isHovered(state) ? styles.tableHeaderCellHovered : undefined,
                  state.pressed ? styles.pressed : undefined,
                ]}>
                <Text style={[styles.tableHeaderText, getTextAlignStyle(column.align)]}>{column.title}</Text>
                <MaterialIcons
                  color={activeSort ? BRAND_NAVY : Colors.light.icon}
                  name={activeSort === 'asc' ? 'arrow-upward' : activeSort === 'desc' ? 'arrow-downward' : 'unfold-more'}
                  size={14}
                />
              </Pressable>
            );
          })}
        </View>
        {sortedRows.map((row) => {
          const cells = columns.map((column) => (
            <View key={column.key} style={[styles.tableCell, getColumnStyle(column)]}>
              <View style={column.align === 'right' ? styles.alignRight : undefined}>
                {column.render(row)}
              </View>
            </View>
          ));

          return (
            <Pressable
              accessibilityRole={onRowPress ? 'button' : undefined}
              key={keyExtractor(row)}
              onPress={onRowPress ? () => onRowPress(row) : undefined}
              style={(state) => [
                styles.tableRow,
                isHovered(state) ? styles.tableRowHovered : undefined,
                state.pressed ? styles.tableRowPressed : undefined,
              ]}>
              {cells}
            </Pressable>
          );
        })}
      </View>
    </ScrollView>
  );
}

export function AdminPagination({
  itemLabel,
  onPageChange,
  onSizeChange,
  page,
  size,
  totalElements,
  totalPages,
}: AdminPaginationProps) {
  const [sizeMenuOpen, setSizeMenuOpen] = useState(false);
  const lastAutoSizeRef = useRef<number | null>(null);
  const safePage = Math.max(page, 0);
  const safeSize = size > 0 ? size : 15;
  const safeTotalPages = Math.max(totalPages, 1);
  const first = totalElements === 0 ? 0 : safePage * safeSize + 1;
  const last = Math.min((safePage + 1) * safeSize, totalElements);
  const visibleCount = totalElements === 0 ? 0 : Math.max(last - first + 1, 0);
  const pageItems = getPaginationItems(safePage, safeTotalPages);
  const availableSizeOptions = ADMIN_PAGE_SIZE_OPTIONS.filter((option) => option <= totalElements);
  const canChooseSize = Boolean(onSizeChange && availableSizeOptions.length > 0);
  const largestAvailableSize = availableSizeOptions[availableSizeOptions.length - 1];

  useEffect(() => {
    if (!onSizeChange || !largestAvailableSize || safeSize <= totalElements) {
      lastAutoSizeRef.current = null;
      return;
    }

    if (lastAutoSizeRef.current !== largestAvailableSize) {
      lastAutoSizeRef.current = largestAvailableSize;
      onSizeChange(largestAvailableSize);
    }
  }, [largestAvailableSize, onSizeChange, safeSize, totalElements]);

  return (
    <View style={styles.paginationBar}>
      <Text selectable style={styles.paginationText}>
        Showing {formatAdminNumber(visibleCount)} out of {formatAdminNumber(totalElements)}
      </Text>
      <View style={styles.paginationControls}>
        {canChooseSize ? (
          <View style={styles.pageSizeMenuWrap}>
            {sizeMenuOpen ? (
              <View style={styles.pageSizeMenu}>
                {availableSizeOptions.map((option) => (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityState={{ selected: option === safeSize }}
                    key={option}
                    onPress={() => {
                      onSizeChange?.(option);
                      setSizeMenuOpen(false);
                    }}
                    style={(state) => [
                      styles.pageSizeMenuItem,
                      option === safeSize ? styles.pageSizeMenuItemActive : undefined,
                      isHovered(state) ? styles.pageSizeMenuItemHovered : undefined,
                    ]}>
                    <MaterialIcons
                      color={option === safeSize ? BRAND_NAVY : Colors.light.icon}
                      name={option === safeSize ? 'check-box' : 'check-box-outline-blank'}
                      size={16}
                    />
                    <Text style={styles.pageSizeMenuItemText}>{option} per page</Text>
                  </Pressable>
                ))}
              </View>
            ) : null}
            <Pressable
              accessibilityLabel={`Rows per page for ${itemLabel}`}
              accessibilityRole="button"
              accessibilityState={{ expanded: sizeMenuOpen }}
              onPress={() => setSizeMenuOpen((current) => !current)}
              style={(state) => [
                styles.pageSizeSelect,
                isHovered(state) ? styles.pageSizeSelectHovered : undefined,
                state.pressed ? styles.pressed : undefined,
              ]}>
              <Text style={styles.pageSizeSelectText}>{safeSize} per page</Text>
              <MaterialIcons color={BRAND_NAVY} name={sizeMenuOpen ? 'expand-less' : 'expand-more'} size={20} />
            </Pressable>
          </View>
        ) : null}
        <PaginationButton disabled={safePage <= 0} label="<" onPress={() => onPageChange(Math.max(safePage - 1, 0))} />
        {pageItems.map((item, index) =>
          item === 'ellipsis' ? (
            <Text selectable key={`ellipsis-${index}`} style={styles.paginationEllipsis}>
              ...
            </Text>
          ) : (
            <PaginationButton
              key={item}
              active={item === safePage}
              label={String(item + 1)}
              onPress={() => onPageChange(item)}
            />
          ),
        )}
        <PaginationButton
          disabled={safePage + 1 >= safeTotalPages}
          label=">"
          onPress={() => onPageChange(Math.min(safePage + 1, safeTotalPages - 1))}
        />
      </View>
    </View>
  );
}

export function AdminConfirmModal({
  confirmLabel,
  danger = false,
  message,
  onCancel,
  onConfirm,
  pending,
  pendingLabel,
  title,
  visible,
}: AdminConfirmModalProps) {
  return (
    <Modal animationType="fade" onRequestClose={onCancel} transparent visible={visible}>
      <View style={styles.modalBackdrop}>
        <View style={styles.modalCard}>
          <Text selectable style={[styles.modalTitle, !message ? styles.modalTitleOnly : undefined]}>
            {title}
          </Text>
          {message ? (
            <Text selectable style={styles.modalMessage}>
              {message}
            </Text>
          ) : null}
          <View style={styles.modalActions}>
            <AdminButton disabled={pending} label="Cancel" onPress={onCancel} style={styles.modalButton} variant="ghost" />
            <AdminButton
              disabled={pending}
              label={pending ? pendingLabel : confirmLabel}
              onPress={onConfirm}
              style={styles.modalButton}
              variant={danger ? 'danger' : 'primary'}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

export function AdminInlineError({
  message,
  title = 'Admin request failed',
}: {
  message: string | null;
  title?: string;
}) {
  if (!message) {
    return null;
  }

  return <ErrorBanner title={title} message={message} />;
}

export function AdminFieldText({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  return (
    <Text selectable style={[styles.fieldText, style]}>
      {children}
    </Text>
  );
}

export function AdminMutedText({ children }: { children: ReactNode }) {
  return (
    <Text selectable style={styles.mutedText}>
      {children}
    </Text>
  );
}

export function AdminDateInput({
  accessibilityLabel,
  onChangeText,
  placeholder,
  value,
}: {
  accessibilityLabel: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  value: string;
}) {
  if (Platform.OS === 'web') {
    return (
      <View style={styles.iconInputShell}>
        {createElement('input', {
          'aria-label': accessibilityLabel,
          onChange: (event: { currentTarget: { value: string } }) => onChangeText(event.currentTarget.value),
          onFocus: (event: { currentTarget: { showPicker?: () => void } }) => {
            try {
              event.currentTarget.showPicker?.();
            } catch {
              // Browsers may block programmatic picker opening; focusing still leaves the native date input usable.
            }
          },
          onKeyDown: (event: { key?: string; preventDefault: () => void }) => {
            if ((event.key ?? '').length === 1) {
              event.preventDefault();
            }
          },
          onPaste: (event: { preventDefault: () => void }) => event.preventDefault(),
          placeholder,
          style: getWebDateInputStyle(),
          type: 'date',
          value,
        })}
      </View>
    );
  }

  return (
    <View style={styles.iconInputShell}>
      <TextInput
        accessibilityLabel={accessibilityLabel}
        autoCapitalize="none"
        onChangeText={onChangeText}
        placeholder={placeholder}
        style={[styles.input, styles.inputWithTrailingIcon, styles.dateInput]}
        value={value}
      />
      <View pointerEvents="none" style={styles.inputTrailingIcon}>
        <MaterialIcons color={Colors.light.icon} name="calendar-today" size={18} />
      </View>
    </View>
  );
}

export function AdminSearchInput({ accessibilityLabel, onChangeText, placeholder, value }: AdminSearchInputProps) {
  return (
    <View style={styles.iconInputShell}>
      <TextInput
        accessibilityLabel={accessibilityLabel}
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onChangeText}
        placeholder={placeholder}
        returnKeyType="search"
        style={[styles.input, styles.inputWithTrailingIcon]}
        value={value}
      />
      <View pointerEvents="none" style={styles.inputTrailingIcon}>
        <MaterialIcons color={Colors.light.icon} name="search" size={20} />
      </View>
    </View>
  );
}

export function AdminKeyValueGrid({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <View style={styles.keyValueGrid}>
      {rows.map(([label, value]) => {
        const renderedValue = value ?? 'Not available';
        return (
          <View key={label} style={styles.keyValueRow}>
            <Text selectable style={styles.keyValueLabel}>
              {label}
            </Text>
            <View style={styles.keyValueValue}>
              {typeof renderedValue === 'string' || typeof renderedValue === 'number' ? (
                <Text selectable style={styles.keyValueValueText}>
                  {renderedValue}
                </Text>
              ) : (
                renderedValue
              )}
            </View>
          </View>
        );
      })}
    </View>
  );
}

export function formatAdminDate(value: string | null | undefined) {
  if (!value) {
    return 'Not available';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

export function formatAdminEnum(value: string | null | undefined) {
  if (!value) {
    return 'Not available';
  }

  if (ADMIN_ENUM_LABELS[value]) {
    return ADMIN_ENUM_LABELS[value];
  }

  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatAdminNumber(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return '0';
  }

  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return String(value);
  }

  return new Intl.NumberFormat('en-US').format(numericValue);
}

export function formatAdminMoney(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return 'Not available';
  }

  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return String(value);
  }

  return new Intl.NumberFormat('en-US', {
    currency: 'USD',
    maximumFractionDigits: 2,
    style: 'currency',
  }).format(numericValue);
}

export function getStatusTone(value: string | null | undefined): AdminMetricProps['tone'] {
  if (!value) {
    return 'neutral';
  }

  if (['ACTIVE', 'SUCCESS'].includes(value)) {
    return 'success';
  }

  if (['FAILED', 'INACTIVE'].includes(value)) {
    return 'danger';
  }

  if (['RUNNING', 'PARTIAL_SUCCESS', 'FALLBACK_CACHED', 'FALLBACK_RULE_BASED'].includes(value)) {
    return 'warn';
  }

  return 'neutral';
}

function AdminLoadingScreen() {
  return (
    <View style={styles.centerScreen}>
      <Text selectable style={styles.pageTitle}>
        Preparing admin console
      </Text>
      <Text selectable style={styles.mutedText}>
        StockMentor is checking the current in-memory session.
      </Text>
    </View>
  );
}

function AdminAccessDenied({
  description,
  onLogout,
}: {
  description: string;
  onLogout: () => void;
}) {
  return (
    <View style={styles.centerScreen}>
      <View style={styles.centerCard}>
        <Text selectable style={styles.eyebrow}>
          Admin access
        </Text>
        <Text selectable style={styles.pageTitle}>
          Admin console unavailable
        </Text>
        <Text selectable style={styles.mutedText}>
          {description}
        </Text>
        <AdminLogoutButton onLogout={onLogout} />
      </View>
    </View>
  );
}

function AdminPhoneFallback({
  adminToken,
  onLogout,
  onSetToken,
}: {
  adminToken: string | null;
  onLogout: () => void;
  onSetToken: (token: string | null) => void;
}) {
  return (
    <View style={styles.centerScreen}>
      <View style={styles.centerCard}>
        <Text selectable style={styles.eyebrow}>
          Admin console
        </Text>
        <Text selectable style={styles.pageTitle}>
          Admin console is best viewed on tablet or web
        </Text>
        <Text selectable style={styles.mutedText}>
          Full admin tables and maintenance actions are hidden on phone-sized screens.
        </Text>
        {adminToken ? (
          <AdminMutedText>Admin token is active for this app session. Use a tablet or web browser to continue.</AdminMutedText>
        ) : (
          <AdminTokenInlineForm onSaveToken={onSetToken} />
        )}
        <AdminLogoutButton onLogout={onLogout} />
      </View>
    </View>
  );
}

function AdminTokenPrompt({
  onLogout,
  onSaveToken,
}: {
  onLogout: () => void;
  onSaveToken: (token: string | null) => void;
}) {
  return (
    <View style={styles.centerScreen}>
      <View style={[styles.centerCard, styles.tokenCard]}>
        <Text selectable style={styles.eyebrow}>
          Admin token
        </Text>
        <Text selectable style={styles.pageTitle}>
          Enter admin token
        </Text>
        <AdminTokenInlineForm onSaveToken={onSaveToken} />
        <AdminLogoutButton onLogout={onLogout} />
      </View>
    </View>
  );
}

function AdminTokenInlineForm({ onSaveToken }: { onSaveToken: (token: string | null) => void }) {
  const [token, setToken] = useState('');
  const trimmedToken = token.trim();

  return (
    <View style={styles.tokenForm}>
      <TextInput
        accessibilityLabel="Admin token"
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={setToken}
        placeholder="Paste admin token for this session"
        secureTextEntry
        style={styles.input}
        value={token}
      />
      <AdminButton
        disabled={!trimmedToken}
        label="Log In"
        onPress={() => onSaveToken(trimmedToken)}
      />
    </View>
  );
}

function AdminSideNav({ onLogout }: { onLogout: () => void }) {
  const pathname = usePathname();
  const router = useRouter();

  const activeHref = useMemo(() => {
    if (pathname.startsWith('/admin/users')) {
      return '/admin/users';
    }
    if (pathname.startsWith('/admin/ai-suggestions')) {
      return '/admin/ai-suggestions';
    }
    if (pathname.startsWith('/admin/stocks')) {
      return '/admin/stocks/maintenance';
    }
    return '/admin';
  }, [pathname]);

  const activeIndex = useMemo(
    () => adminNavItems.findIndex((item) => item.href === activeHref),
    [activeHref],
  );

  return (
    <View style={styles.sideNav}>
      <View style={styles.sideNavBrand}>
        <View style={styles.brandBadge}>
          <Image
            contentFit="contain"
            source={require('../../assets/images/stockmentor-icon-transparent-1024.png')}
            style={styles.brandImage}
          />
        </View>
        <View style={styles.sideNavBrandText}>
          <Text selectable style={styles.sideNavTitle}>
            StockMentor
          </Text>
          <Text selectable style={styles.sideNavSubtitle}>
            Admin console
          </Text>
        </View>
      </View>

      <View style={styles.navItems}>
        {activeIndex >= 0 ? (
          <View
            pointerEvents="none"
            style={[
              styles.navActivePill,
              {
                transform: [
                  {
                    translateY: activeIndex * (ADMIN_NAV_ITEM_HEIGHT + ADMIN_NAV_ITEM_GAP),
                  },
                ],
              },
            ]}
          />
        ) : null}

        {adminNavItems.map((item, index) => {
          const active = activeHref === item.href;

          return (
            <View key={item.label} style={styles.navItemWrap}>
              {index > 0 ? <View pointerEvents="none" style={styles.navItemDivider} /> : null}

              <Pressable
                accessibilityRole="link"
                accessibilityState={{ selected: active }}
                onPress={() => {
                  if (!active) {
                    router.push(item.href);
                  }
                }}
                style={(state) => [
                  styles.navItem,
                  active ? styles.navItemActive : undefined,
                  !active && isHovered(state) ? styles.navItemHovered : undefined,
                  state.pressed ? styles.pressed : undefined,
                ]}>
                <Text style={[styles.navItemText, active ? styles.navItemTextActive : undefined]}>
                  {item.label}
                </Text>
              </Pressable>
            </View>
          );
        })}
      </View>

      <View style={styles.sideNavActions}>
        <AdminLogoutButton onLogout={onLogout} />
      </View>
    </View>
  );
}

function AdminLogoutButton({ onLogout }: { onLogout: () => void }) {
  const [confirmOpen, setConfirmOpen] = useState(false);

  return (
    <>
      <AdminButton label="Log out" onPress={() => setConfirmOpen(true)} variant="danger" />
      <AdminConfirmModal
        visible={confirmOpen}
        title="Log out of admin console?"
        confirmLabel="Log out"
        pendingLabel="Logging out..."
        pending={false}
        danger
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          setConfirmOpen(false);
          onLogout();
        }}
      />
    </>
  );
}

function PaginationButton({
  active = false,
  disabled = false,
  label,
  onPress,
}: {
  active?: boolean;
  disabled?: boolean;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled, selected: active }}
      disabled={disabled}
      onPress={onPress}
      style={(state) => [
        styles.paginationButton,
        active ? styles.paginationButtonActive : undefined,
        !active && isHovered(state) ? styles.paginationButtonHovered : undefined,
        disabled ? styles.paginationButtonDisabled : undefined,
        state.pressed ? styles.pressed : undefined,
      ]}>
      <Text style={[styles.paginationButtonText, active ? styles.paginationButtonTextActive : undefined]}>
        {label}
      </Text>
    </Pressable>
  );
}

function getAdminErrorMessage(error: ApiError) {
  const lowerMessage = error.message.toLowerCase();

  if (error.status === 401) {
    return 'The admin token was missing or not accepted. Enter the token again.';
  }

  if (error.status === 403) {
    return 'This account does not have admin access for that action.';
  }

  if (error.status === 404) {
    return 'That admin resource was not found.';
  }

  if (error.status === 409) {
    return error.message || 'That change conflicts with the current backend state.';
  }

  if (error.status === 429 || lowerMessage.includes('rate limit')) {
    return 'A backend rate limit was reached. Wait one minute, then retry.';
  }

  if (error.status === 0 || error.retryable) {
    return `${error.message} Check that the backend is running and CORS allows the Expo Web origin.`;
  }

  return error.message;
}

function getToneTextStyle(tone: AdminMetricProps['tone']): StyleProp<TextStyle> {
  switch (tone) {
    case 'danger':
      return styles.dangerText;
    case 'success':
      return styles.successText;
    case 'warn':
      return styles.warnText;
    case 'neutral':
    default:
      return undefined;
  }
}

function getTonePillStyle(tone: AdminMetricProps['tone']): StyleProp<ViewStyle> {
  switch (tone) {
    case 'danger':
      return styles.dangerPill;
    case 'success':
      return styles.successPill;
    case 'warn':
      return styles.warnPill;
    case 'neutral':
    default:
      return styles.neutralPill;
  }
}

function getColumnStyle<T>(column: AdminDataColumn<T>): StyleProp<ViewStyle> {
  const width = column.width ?? 140;

  return {
    flexBasis: width,
    flexGrow: width,
    flexShrink: 0,
  };
}

function getWebDateInputStyle() {
  return {
    ...(StyleSheet.flatten([styles.input, styles.dateInput, styles.webDateInput]) as Record<string, unknown>),
    borderColor: '#CBD5E1',
    boxSizing: 'border-box',
    outline: 'none',
    paddingLeft: Spacing.md,
    paddingRight: Spacing.md,
  };
}

function getTextAlignStyle(align?: 'left' | 'right'): StyleProp<TextStyle> {
  return align === 'right' ? styles.textRight : undefined;
}

function getNextSortState(current: { direction: SortDirection; key: string } | null, key: string) {
  if (current?.key !== key) {
    return { direction: 'asc' as const, key };
  }
  if (current.direction === 'asc') {
    return { direction: 'desc' as const, key };
  }
  return null;
}

function getSortValue<T>(column: AdminDataColumn<T>, item: T) {
  const value = column.sortValue ? column.sortValue(item) : getReactNodeText(column.render(item));
  return typeof value === 'string' ? value.toLowerCase() : value;
}

function compareAdminSortValues(
  left: number | string | null | undefined,
  right: number | string | null | undefined,
  direction: SortDirection,
) {
  const leftEmpty = left === null || left === undefined || left === '';
  const rightEmpty = right === null || right === undefined || right === '';
  if (leftEmpty || rightEmpty) {
    return leftEmpty === rightEmpty ? 0 : leftEmpty ? 1 : -1;
  }

  const result = typeof left === 'number' && typeof right === 'number' ? left - right : String(left).localeCompare(String(right));
  return direction === 'asc' ? result : -result;
}

function getReactNodeText(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(getReactNodeText).join(' ');
  }
  if (isValidElement<{ children?: ReactNode }>(node)) {
    return getReactNodeText(node.props.children);
  }
  return '';
}

function isHovered(state: unknown) {
  return Boolean((state as { hovered?: boolean }).hovered);
}

function getPaginationItems(page: number, totalPages: number) {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const visiblePages = new Set([0, totalPages - 1, page - 1, page, page + 1]);
  if (page <= 2) {
    visiblePages.add(1).add(2).add(3);
  }
  if (page >= totalPages - 3) {
    visiblePages.add(totalPages - 2).add(totalPages - 3).add(totalPages - 4);
  }

  const pages = [...visiblePages].filter((item) => item >= 0 && item < totalPages).sort((a, b) => a - b);
  return pages.flatMap((item, index) => (index > 0 && item - pages[index - 1] > 1 ? ['ellipsis' as const, item] : [item]));
}

function getAdminButtonVariantStyle(variant: NonNullable<AdminButtonProps['variant']>): StyleProp<ViewStyle> {
  switch (variant) {
    case 'danger':
      return styles.adminButtonDanger;
    case 'ghost':
      return styles.adminButtonGhost;
    case 'secondary':
      return styles.adminButtonSecondary;
    case 'primary':
    default:
      return styles.adminButtonPrimary;
  }
}

function getAdminButtonTextStyle(variant: NonNullable<AdminButtonProps['variant']>): StyleProp<TextStyle> {
  switch (variant) {
    case 'danger':
    case 'primary':
      return styles.adminButtonTextOnDark;
    case 'secondary':
      return styles.adminButtonTextSecondary;
    case 'ghost':
    default:
      return styles.adminButtonTextGhost;
  }
}

function getAdminButtonHoverStyle(variant: NonNullable<AdminButtonProps['variant']>): StyleProp<ViewStyle> {
  switch (variant) {
    case 'danger':
      return styles.adminButtonDangerHovered;
    case 'primary':
      return styles.adminButtonPrimaryHovered;
    case 'ghost':
    case 'secondary':
    default:
      return styles.adminButtonLightHovered;
  }
}

const ADMIN_ENUM_LABELS: Record<string, string> = {
  ADMIN_MANUAL: 'Admin Manual',
  CLEANUP_1MIN: 'Cleanup 1-Minute',
  DAILY_MISSING: 'Daily Missing',
  DAILY_RANGE: 'Daily Range',
  FALLBACK_CACHED: 'Fallback Cached',
  FALLBACK_RULE_BASED: 'Fallback Rules',
  INTRADAY_DATE: 'Intraday Date',
  MANUAL_REFRESH: 'Manual Refresh',
  NO_ACTIVE_SUGGESTION: 'No Active Suggestion',
  ONBOARDING_COMPLETED: 'Onboarding Completed',
  PARTIAL_SUCCESS: 'Partial Success',
  RETAKE_QUIZ: 'Retake Quiz',
  SCHEDULED_REFRESH: 'Scheduled Refresh',
};

const styles = StyleSheet.create({
  shell: {
    backgroundColor: Colors.light.background,
    flex: 1,
    flexDirection: 'row',
  },
  shellContent: {
    flex: 1,
  },
  sideNav: {
    backgroundColor: BRAND_NAVY,
    gap: Spacing.xl,
    padding: Spacing.xl,
    width: 276,
  },
  sideNavBrand: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  sideNavBrandText: {
    flex: 1,
    gap: Spacing.xs,
  },
  brandBadge: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: Radius.md,
    height: 42,
    justifyContent: 'center',
    overflow: 'hidden',
    width: 42,
  },
  brandImage: {
    height: 34,
    width: 34,
  },
  sideNavTitle: {
    color: Colors.light.surface,
    fontSize: 22,
    fontWeight: '800',
  },
  sideNavSubtitle: {
    color: '#BFDBFE',
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  navItems: {
    gap: ADMIN_NAV_ITEM_GAP,
    position: 'relative',
  },
  navActivePill: {
    ...WEB_TRANSITION,
    backgroundColor: '#FFFFFF',
    borderColor: '#FFFFFF',
    borderRadius: Radius.md,
    borderWidth: 1,
    boxShadow: '0 10px 24px rgba(15, 23, 42, 0.24)',
    height: ADMIN_NAV_ITEM_HEIGHT,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 0,
  },
  navItem: {
    ...WEB_TRANSITION,
    alignItems: 'flex-start',
    backgroundColor: 'transparent',
    borderColor: 'transparent',
    borderRadius: Radius.md,
    borderWidth: 1,
    height: '100%',
    justifyContent: 'center',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
    position: 'relative',
    width: '100%',
    zIndex: 3,
  },
  navItemActive: {
    transform: [{ translateX: 4 }],
  },
  navItemHovered: {
    backgroundColor: 'rgba(255, 255, 255, 0.14)',
    borderColor: 'rgba(255, 255, 255, 0.28)',
  },
  navItemText: {
    color: '#DBEAFE',
    fontSize: 18,
    fontWeight: '500',
  },
  navItemTextActive: {
    color: BRAND_NAVY,
    fontWeight: '800',
  },
  navItemWrap: {
    height: ADMIN_NAV_ITEM_HEIGHT,
    position: 'relative',
    zIndex: 1,
  },
  navItemDivider: {
    backgroundColor: 'rgba(226, 232, 240, 0.22)',
    height: 2,
    left: 0,
    position: 'absolute',
    right: 0,
    top: -(ADMIN_NAV_ITEM_GAP / 2),
    zIndex: 2,
  },
  sideNavActions: {
    gap: Spacing.sm,
    marginTop: 'auto',
  },
  centerScreen: {
    alignItems: 'center',
    backgroundColor: Colors.light.background,
    flex: 1,
    justifyContent: 'center',
    padding: Spacing.xl,
  },
  centerCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.lg,
    maxWidth: 520,
    padding: Spacing.xl,
    width: '100%',
  },
  tokenCard: {
    gap: Spacing.md,
  },
  tokenForm: {
    gap: Spacing.md,
  },
  inputLabel: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  input: {
    backgroundColor: INPUT_BACKGROUND,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    color: Colors.light.text,
    fontSize: 16,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  iconInputShell: {
    flex: 1,
    minWidth: 220,
    position: 'relative',
  },
  inputWithTrailingIcon: {
    paddingRight: 44,
  },
  inputTrailingIcon: {
    position: 'absolute',
    right: Spacing.md,
    top: 14,
  },
  dateInput: {
    flex: 1,
  },
  webDateInput: {
    width: '100%',
  },
  page: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  pageContent: {
    gap: Spacing.xl,
    padding: Spacing.xl,
  },
  pageHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.lg,
    justifyContent: 'space-between',
  },
  pageHeaderText: {
    flex: 1,
    gap: Spacing.xs,
  },
  pageActions: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  eyebrow: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  pageTitle: {
    color: Colors.light.text,
    fontSize: 28,
    fontWeight: '700',
    lineHeight: 34,
  },
  section: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  sectionHighlight: {
    backgroundColor: '#FFF7ED',
    borderColor: '#FDBA74',
  },
  sectionSuccess: {
    backgroundColor: '#ECFDF5',
    borderColor: '#86EFAC',
  },
  sectionHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  sectionHeaderText: {
    flex: 1,
    gap: Spacing.xs,
  },
  sectionTitle: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '800',
  },
  sectionDescription: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
  },
  metric: {
    backgroundColor: 'white',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    minWidth: 180,
    padding: Spacing.md,
  },
  metricLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  metricValue: {
    color: Colors.light.text,
    fontSize: 24,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  metricDescription: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  adminButton: {
    ...WEB_TRANSITION,
    alignItems: 'center',
    borderRadius: Radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 40,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
  adminButtonPrimary: {
    backgroundColor: BRAND_NAVY,
    borderColor: BRAND_NAVY,
  },
  adminButtonSecondary: {
    backgroundColor: '#FFFFFF',
    borderColor: '#CBD5E1',
  },
  adminButtonGhost: {
    backgroundColor: 'transparent',
    borderColor: Colors.light.border,
  },
  adminButtonDanger: {
    backgroundColor: Colors.light.destructive,
    borderColor: Colors.light.destructive,
  },
  adminButtonLightHovered: {
    backgroundColor: ADMIN_HOVER,
    borderColor: '#CBD5E1',
  },
  adminButtonPrimaryHovered: {
    backgroundColor: '#08315F',
    borderColor: '#08315F',
  },
  adminButtonDangerHovered: {
    backgroundColor: '#991B1B',
    borderColor: '#991B1B',
  },
  adminButtonPressed: {
    opacity: 0.78,
  },
  adminButtonDisabled: {
    opacity: 0.48,
  },
  adminButtonText: {
    fontSize: 14,
    fontWeight: '800',
  },
  adminButtonTextOnDark: {
    color: '#FFFFFF',
  },
  adminButtonTextSecondary: {
    color: BRAND_NAVY,
  },
  adminButtonTextGhost: {
    color: Colors.light.text,
  },
  adminButtonTextDisabled: {
    color: Colors.light.mutedText,
  },
  mutedText: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
  fieldText: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
  keyValueGrid: {
    backgroundColor: 'white',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  keyValueRow: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    padding: Spacing.md,
  },
  keyValueLabel: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '700',
    width: 190,
  },
  keyValueValue: {
    flex: 1,
  },
  keyValueValueText: {
    color: Colors.light.text,
    fontSize: 13,
    lineHeight: 18,
  },
  tabs: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  tab: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 40,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
  tabActive: {
    backgroundColor: BRAND_NAVY,
    borderColor: BRAND_NAVY,
  },
  tabHovered: {
    backgroundColor: ADMIN_HOVER,
  },
  tabText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  tabTextActive: {
    color: Colors.light.surface,
  },
  table: {
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    minWidth: '100%',
    width: '100%',
  },
  tableScroll: {
    width: '100%',
  },
  tableScrollContent: {
    minWidth: '100%',
  },
  tableHeaderRow: {
    backgroundColor: '#F8FAFC',
  },
  tableHeaderCell: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
    justifyContent: 'flex-start',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
  },
  tableHeaderCellHovered: {
    backgroundColor: '#E2E8F0',
  },
  tableRow: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 52,
    width: '100%',
  },
  tableRowPressed: {
    backgroundColor: '#EFF6FF',
  },
  tableRowHovered: {
    backgroundColor: '#F8FAFC',
  },
  tableHeaderText: {
    color: Colors.light.mutedText,
    flexShrink: 1,
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  tableCell: {
    justifyContent: 'center',
    padding: Spacing.md,
  },
  alignRight: {
    alignItems: 'flex-end',
  },
  textRight: {
    textAlign: 'right',
  },
  statusPill: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    borderWidth: 1,
    maxWidth: 180,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
  },
  statusPillText: {
    fontSize: 12,
    fontWeight: '800',
  },
  neutralPill: {
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
  },
  successPill: {
    backgroundColor: '#ECFDF5',
    borderColor: '#BBF7D0',
  },
  warnPill: {
    backgroundColor: '#FFF7ED',
    borderColor: '#FED7AA',
  },
  dangerPill: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
  },
  successText: {
    color: Colors.light.success,
  },
  warnText: {
    color: Colors.light.caution,
  },
  dangerText: {
    color: Colors.light.destructive,
  },
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.48)',
    flex: 1,
    justifyContent: 'center',
    padding: Spacing.xl,
  },
  modalCard: {
    backgroundColor: Colors.light.surface,
    borderRadius: Radius.md,
    gap: Spacing.md,
    maxWidth: 480,
    padding: Spacing.xl,
    width: '100%',
  },
  modalTitle: {
    color: Colors.light.text,
    fontSize: 20,
    fontWeight: '800',
  },
  modalTitleOnly: {
    marginBottom: Spacing.md,
  },
  modalMessage: {
    color: Colors.light.text,
    fontSize: 15,
    lineHeight: 22,
  },
  modalActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  modalButton: {
    flex: 1,
  },
  paginationBar: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
    justifyContent: 'space-between',
    paddingTop: Spacing.sm,
  },
  paginationText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  paginationControls: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  pageSizeMenuWrap: {
    position: 'relative',
  },
  pageSizeSelect: {
    ...WEB_TRANSITION,
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: '#CBD5E1',
    borderRadius: Radius.sm,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.xs,
    height: 34,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  pageSizeSelectHovered: {
    backgroundColor: ADMIN_HOVER,
  },
  pageSizeSelectText: {
    color: BRAND_NAVY,
    fontSize: 13,
    fontWeight: '400',
    lineHeight: 18,
  },
  pageSizeMenu: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    bottom: 40,
    boxShadow: '0 14px 32px rgba(15, 23, 42, 0.18)',
    minWidth: 160,
    padding: Spacing.xs,
    position: 'absolute',
    right: 0,
    zIndex: 10,
  },
  pageSizeMenuItem: {
    ...WEB_TRANSITION,
    alignItems: 'center',
    borderRadius: Radius.sm,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 36,
    paddingHorizontal: Spacing.sm,
  },
  pageSizeMenuItemActive: {
    backgroundColor: '#EEF2FF',
  },
  pageSizeMenuItemHovered: {
    backgroundColor: ADMIN_HOVER,
  },
  pageSizeMenuItemText: {
    color: Colors.light.text,
    fontSize: 13,
    fontWeight: '400',
  },
  paginationButton: {
    ...WEB_TRANSITION,
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    height: 34,
    justifyContent: 'center',
    minWidth: 34,
    paddingHorizontal: Spacing.sm,
  },
  paginationButtonActive: {
    backgroundColor: BRAND_NAVY,
    borderColor: BRAND_NAVY,
  },
  paginationButtonHovered: {
    backgroundColor: ADMIN_HOVER,
  },
  paginationButtonDisabled: {
    opacity: 0.42,
  },
  paginationButtonText: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  paginationButtonTextActive: {
    color: Colors.light.surface,
  },
  paginationEllipsis: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '800',
    paddingHorizontal: Spacing.xs,
  },
  pressed: {
    opacity: 0.82,
  },
});
