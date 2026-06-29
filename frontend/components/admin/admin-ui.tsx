import { Link, Redirect, type Href, usePathname } from 'expo-router';
import { useCallback, useMemo, useState, type ReactNode } from 'react';
import {
  Modal,
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
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';

const BRAND_NAVY = '#052344';
export const ADMIN_TABLET_MIN_WIDTH = 768;

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
  title: string;
};

type AdminMetricProps = {
  label: string;
  tone?: 'danger' | 'neutral' | 'success' | 'warn';
  value: string;
};

type AdminDataColumn<T> = {
  align?: 'left' | 'right';
  key: string;
  render: (item: T) => ReactNode;
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

type AdminConfirmModalProps = {
  confirmLabel: string;
  danger?: boolean;
  message: string;
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
  pendingLabel: string;
  title: string;
  visible: boolean;
};

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
  const { adminToken, clearSession, setAdminToken, user } = useAuthSession();
  const { width } = useWindowDimensions();
  const isPhone = width < ADMIN_TABLET_MIN_WIDTH;

  if (isPhone) {
    return (
      <AdminPhoneFallback
        adminToken={adminToken}
        onClearToken={() => setAdminToken(null)}
        onLogout={clearSession}
        onSetToken={setAdminToken}
      />
    );
  }

  if (!adminToken) {
    return (
      <AdminTokenPrompt
        accountLabel={user?.email ?? user?.username ?? 'admin account'}
        onLogout={clearSession}
        onSaveToken={setAdminToken}
      />
    );
  }

  return (
    <View style={styles.shell}>
      <AdminSideNav onClearToken={() => setAdminToken(null)} onLogout={clearSession} />
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

export function AdminSection({ action, children, title }: AdminSectionProps) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <Text selectable style={styles.sectionTitle}>
          {title}
        </Text>
        {action}
      </View>
      {children}
    </View>
  );
}

export function AdminMetric({ label, tone = 'neutral', value }: AdminMetricProps) {
  return (
    <View style={styles.metric}>
      <Text selectable style={styles.metricLabel}>
        {label}
      </Text>
      <Text selectable numberOfLines={1} style={[styles.metricValue, getToneTextStyle(tone)]}>
        {value}
      </Text>
    </View>
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
            style={({ pressed }) => [
              styles.tab,
              active ? styles.tabActive : undefined,
              pressed ? styles.pressed : undefined,
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
  if (loading) {
    return <SkeletonRows count={5} />;
  }

  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator>
      <View style={styles.table}>
        <View style={[styles.tableRow, styles.tableHeaderRow]}>
          {columns.map((column) => (
            <Text
              key={column.key}
              style={[styles.tableHeaderText, getColumnTextStyle(column), getTextAlignStyle(column.align)]}>
              {column.title}
            </Text>
          ))}
        </View>
        {rows.map((row) => {
          const cells = columns.map((column) => (
            <View key={column.key} style={[styles.tableCell, getColumnStyle(column)]}>
              <View style={column.align === 'right' ? styles.alignRight : undefined}>
                {column.render(row)}
              </View>
            </View>
          ));

          if (!onRowPress) {
            return (
              <View key={keyExtractor(row)} style={styles.tableRow}>
                {cells}
              </View>
            );
          }

          return (
            <Pressable
              accessibilityRole="button"
              key={keyExtractor(row)}
              onPress={() => onRowPress(row)}
              style={({ pressed }) => [styles.tableRow, pressed ? styles.tableRowPressed : undefined]}>
              {cells}
            </Pressable>
          );
        })}
      </View>
    </ScrollView>
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
          <Text selectable style={styles.modalTitle}>
            {title}
          </Text>
          <Text selectable style={styles.modalMessage}>
            {message}
          </Text>
          <View style={styles.modalActions}>
            <ActionButton disabled={pending} label="Cancel" onPress={onCancel} style={styles.modalButton} variant="ghost" />
            <ActionButton
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
        <ActionButton label="Log out" onPress={onLogout} variant="ghost" />
      </View>
    </View>
  );
}

function AdminPhoneFallback({
  adminToken,
  onClearToken,
  onLogout,
  onSetToken,
}: {
  adminToken: string | null;
  onClearToken: () => void;
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
          <ActionButton label="Clear admin token" onPress={onClearToken} variant="secondary" />
        ) : (
          <AdminTokenInlineForm onSaveToken={onSetToken} />
        )}
        <ActionButton label="Log out" onPress={onLogout} variant="ghost" />
      </View>
    </View>
  );
}

function AdminTokenPrompt({
  accountLabel,
  onLogout,
  onSaveToken,
}: {
  accountLabel: string;
  onLogout: () => void;
  onSaveToken: (token: string | null) => void;
}) {
  return (
    <View style={styles.centerScreen}>
      <View style={styles.centerCard}>
        <Text selectable style={styles.eyebrow}>
          Admin token
        </Text>
        <Text selectable style={styles.pageTitle}>
          Enter admin token
        </Text>
        <Text selectable style={styles.mutedText}>
          Signed in as {accountLabel}. The token is kept only in React state for this app session.
        </Text>
        <AdminTokenInlineForm onSaveToken={onSaveToken} />
        <ActionButton label="Log out" onPress={onLogout} variant="ghost" />
      </View>
    </View>
  );
}

function AdminTokenInlineForm({ onSaveToken }: { onSaveToken: (token: string | null) => void }) {
  const [token, setToken] = useState('');
  const trimmedToken = token.trim();

  return (
    <View style={styles.tokenForm}>
      <Text selectable style={styles.inputLabel}>
        Admin token
      </Text>
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
      <ActionButton
        disabled={!trimmedToken}
        label="Use token for this session"
        onPress={() => onSaveToken(trimmedToken)}
      />
    </View>
  );
}

function AdminSideNav({
  onClearToken,
  onLogout,
}: {
  onClearToken: () => void;
  onLogout: () => void;
}) {
  const pathname = usePathname();

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

  return (
    <View style={styles.sideNav}>
      <View style={styles.sideNavBrand}>
        <Text selectable style={styles.sideNavTitle}>
          StockMentor
        </Text>
        <Text selectable style={styles.sideNavSubtitle}>
          Admin console
        </Text>
      </View>
      <View style={styles.navItems}>
        {adminNavItems.map((item) => {
          const active = activeHref === item.href;
          return (
            <Link asChild href={item.href} key={item.label}>
              <Pressable
                accessibilityRole="link"
                accessibilityState={{ selected: active }}
                style={({ pressed }) => [
                  styles.navItem,
                  active ? styles.navItemActive : undefined,
                  pressed ? styles.pressed : undefined,
                ]}>
                <Text style={[styles.navItemText, active ? styles.navItemTextActive : undefined]}>
                  {item.label}
                </Text>
              </Pressable>
            </Link>
          );
        })}
      </View>
      <View style={styles.sideNavActions}>
        <ActionButton label="Clear admin token" onPress={onClearToken} variant="secondary" />
        <ActionButton label="Log out" onPress={onLogout} variant="ghost" />
      </View>
    </View>
  );
}

function getAdminErrorMessage(error: ApiError) {
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
  return {
    width: column.width ?? 140,
  };
}

function getColumnTextStyle<T>(column: AdminDataColumn<T>): StyleProp<TextStyle> {
  return {
    width: column.width ?? 140,
  };
}

function getTextAlignStyle(align?: 'left' | 'right'): StyleProp<TextStyle> {
  return align === 'right' ? styles.textRight : undefined;
}

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
    width: 260,
  },
  sideNavBrand: {
    gap: Spacing.xs,
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
    gap: Spacing.sm,
  },
  navItem: {
    borderColor: 'transparent',
    borderRadius: Radius.md,
    borderWidth: 1,
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  navItemActive: {
    backgroundColor: '#FFFFFF',
    borderColor: '#FFFFFF',
  },
  navItemText: {
    color: '#DBEAFE',
    fontSize: 15,
    fontWeight: '700',
  },
  navItemTextActive: {
    color: BRAND_NAVY,
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
  tokenForm: {
    gap: Spacing.sm,
  },
  inputLabel: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  input: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    color: Colors.light.text,
    fontSize: 16,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
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
    fontWeight: '800',
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
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  sectionTitle: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '800',
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
  },
  metric: {
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
  },
  tableHeaderRow: {
    backgroundColor: '#F8FAFC',
  },
  tableRow: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 52,
  },
  tableRowPressed: {
    backgroundColor: '#EFF6FF',
  },
  tableHeaderText: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontWeight: '800',
    padding: Spacing.md,
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
  pressed: {
    opacity: 0.82,
  },
});
