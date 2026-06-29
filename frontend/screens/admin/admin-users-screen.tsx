import { router } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { adminApi } from '@/api/admin';
import { ActionButton } from '@/components/foundation/action-button';
import {
  AdminDataTable,
  AdminFieldText,
  AdminInlineError,
  AdminMutedText,
  AdminPage,
  AdminSection,
  AdminStatusPill,
  formatAdminDate,
  formatAdminEnum,
  formatAdminNumber,
  getStatusTone,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import { Colors, Radius, Spacing } from '@/constants/theme';
import type {
  AdminPageResponse,
  AdminUserListFilters,
  AdminUserListItemResponse,
  AdminUserRole,
  AdminUserStatus,
} from '@/types/admin';

const PAGE_SIZE = 20;
const roleOptions: Array<{ label: string; value: AdminUserRole | '' }> = [
  { label: 'All roles', value: '' },
  { label: 'Beginner', value: 'BEGINNER_INVESTOR' },
  { label: 'Admin', value: 'ADMIN' },
];
const statusOptions: Array<{ label: string; value: AdminUserStatus | '' }> = [
  { label: 'All statuses', value: '' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Suspended', value: 'SUSPENDED' },
];

export function AdminUsersScreen() {
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [searchInput, setSearchInput] = useState('');
  const [filters, setFilters] = useState<AdminUserListFilters>({
    page: 0,
    role: '',
    search: '',
    size: PAGE_SIZE,
    status: '',
  });
  const [response, setResponse] = useState<AdminPageResponse<AdminUserListItemResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadUsers = useCallback(async () => {
    if (!credentials || !adminToken) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const nextResponse = await adminApi.listUsers(credentials, adminToken, filters);
      setResponse(nextResponse);
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [adminToken, credentials, filters, handleAdminError]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const pageText = useMemo(() => {
    if (!response) {
      return 'Page 1 of 1';
    }
    return `Page ${response.page + 1} of ${Math.max(response.totalPages, 1)}`;
  }, [response]);

  const applySearch = useCallback(() => {
    setFilters((current) => ({
      ...current,
      page: 0,
      search: searchInput.trim(),
    }));
  }, [searchInput]);

  const setRoleFilter = useCallback((role: AdminUserRole | '') => {
    setFilters((current) => ({ ...current, page: 0, role }));
  }, []);

  const setStatusFilter = useCallback((status: AdminUserStatus | '') => {
    setFilters((current) => ({ ...current, page: 0, status }));
  }, []);

  const canGoBack = (response?.page ?? 0) > 0;
  const canGoNext = response ? response.page + 1 < response.totalPages : false;

  return (
    <AdminPage
      title="Users"
      actions={<ActionButton label="Refresh" onPress={loadUsers} variant="secondary" />}>
      <AdminInlineError message={errorMessage} />

      <AdminSection title="Search and filters">
        <View style={styles.filterStack}>
          <View style={styles.searchRow}>
            <TextInput
              accessibilityLabel="Search users by email or username"
              autoCapitalize="none"
              autoCorrect={false}
              onChangeText={setSearchInput}
              onSubmitEditing={applySearch}
              placeholder="Search email or username"
              returnKeyType="search"
              style={styles.input}
              value={searchInput}
            />
            <ActionButton label="Search" onPress={applySearch} />
          </View>

          <View style={styles.filterGroup}>
            <Text selectable style={styles.filterLabel}>
              Role
            </Text>
            <View style={styles.chips}>
              {roleOptions.map((option) => (
                <FilterChip
                  key={option.label}
                  active={filters.role === option.value}
                  label={option.label}
                  onPress={() => setRoleFilter(option.value)}
                />
              ))}
            </View>
          </View>

          <View style={styles.filterGroup}>
            <Text selectable style={styles.filterLabel}>
              Status
            </Text>
            <View style={styles.chips}>
              {statusOptions.map((option) => (
                <FilterChip
                  key={option.label}
                  active={filters.status === option.value}
                  label={option.label}
                  onPress={() => setStatusFilter(option.value)}
                />
              ))}
            </View>
          </View>
        </View>
      </AdminSection>

      <AdminSection
        title="User accounts"
        action={
          <View style={styles.paginationActions}>
            <ActionButton
              disabled={!canGoBack}
              label="Previous"
              onPress={() => setFilters((current) => ({ ...current, page: Math.max((current.page ?? 0) - 1, 0) }))}
              variant="ghost"
            />
            <ActionButton
              disabled={!canGoNext}
              label="Next"
              onPress={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))}
              variant="ghost"
            />
          </View>
        }>
        <AdminMutedText>
          {formatAdminNumber(response?.totalElements ?? 0)} users found. {pageText}
        </AdminMutedText>
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.userId)}
          emptyTitle="No users found"
          emptyDescription="Try clearing the search text or filters."
          columns={[
            {
              key: 'id',
              title: 'ID',
              width: 80,
              render: (item) => <AdminFieldText>#{item.userId}</AdminFieldText>,
            },
            {
              key: 'email',
              title: 'Email',
              width: 230,
              render: (item) => <AdminFieldText>{item.email}</AdminFieldText>,
            },
            {
              key: 'username',
              title: 'Username',
              width: 170,
              render: (item) => <AdminFieldText>{item.username}</AdminFieldText>,
            },
            {
              key: 'role',
              title: 'Role',
              width: 170,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.role)}</AdminFieldText>,
            },
            {
              key: 'status',
              title: 'Status',
              width: 150,
              render: (item) => <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />,
            },
            {
              key: 'lastLogin',
              title: 'Last login',
              width: 190,
              render: (item) => <AdminFieldText>{formatAdminDate(item.lastLoginAt)}</AdminFieldText>,
            },
            {
              key: 'created',
              title: 'Created',
              width: 190,
              render: (item) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/users/${item.userId}`)}
        />
      </AdminSection>
    </AdminPage>
  );
}

function FilterChip({
  active,
  label,
  onPress,
}: {
  active: boolean;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        active ? styles.chipActive : undefined,
        pressed ? styles.pressed : undefined,
      ]}>
      <Text style={[styles.chipText, active ? styles.chipTextActive : undefined]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  filterStack: {
    gap: Spacing.md,
  },
  searchRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  input: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    color: Colors.light.text,
    flex: 1,
    fontSize: 15,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
  },
  filterGroup: {
    gap: Spacing.sm,
  },
  filterLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  chip: {
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  chipActive: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  chipText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  chipTextActive: {
    color: Colors.light.surface,
  },
  paginationActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  pressed: {
    opacity: 0.82,
  },
});
