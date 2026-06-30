import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { adminApi } from '@/api/admin';
import {
  AdminButton,
  AdminDataTable,
  AdminFieldText,
  AdminInlineError,
  AdminPage,
  AdminPagination,
  AdminSection,
  AdminSearchInput,
  AdminStatusPill,
  formatAdminDate,
  formatAdminEnum,
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

const PAGE_SIZE = 15;
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

  useEffect(() => {
    const handle = setTimeout(() => {
      const search = searchInput.trim();
      setFilters((current) => (current.search === search ? current : { ...current, page: 0, search }));
    }, 300);

    return () => clearTimeout(handle);
  }, [searchInput]);

  const setRoleFilter = useCallback((role: AdminUserRole | '') => {
    setFilters((current) => ({ ...current, page: 0, role }));
  }, []);

  const setStatusFilter = useCallback((status: AdminUserStatus | '') => {
    setFilters((current) => ({ ...current, page: 0, status }));
  }, []);

  return (
    <AdminPage
      title="Users"
      actions={<AdminButton label="Refresh" onPress={loadUsers} variant="secondary" />}>
      <AdminInlineError message={errorMessage} />

      <AdminSection title="Search and filters">
        <View style={styles.filterStack}>
          <View style={styles.searchRow}>
            <AdminSearchInput
              accessibilityLabel="Search users by email or username"
              onChangeText={setSearchInput}
              placeholder="Search email or username"
              value={searchInput}
            />
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

      <AdminSection title="User accounts">
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
              sortValue: (item) => item.userId,
              render: (item) => <AdminFieldText>#{item.userId}</AdminFieldText>,
            },
            {
              key: 'email',
              title: 'User email',
              width: 300,
              sortValue: (item) => item.email,
              render: (item) => <AdminFieldText>{item.email}</AdminFieldText>,
            },
            {
              key: 'username',
              title: 'Username',
              width: 170,
              sortValue: (item) => item.username,
              render: (item) => <AdminFieldText>{item.username}</AdminFieldText>,
            },
            {
              key: 'role',
              title: 'Role',
              width: 170,
              sortValue: (item) => item.role,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.role)}</AdminFieldText>,
            },
            {
              key: 'status',
              title: 'Status',
              width: 150,
              sortValue: (item) => item.status,
              render: (item) => <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />,
            },
            {
              key: 'lastLogin',
              title: 'Last login',
              width: 190,
              sortValue: (item) => item.lastLoginAt,
              render: (item) => <AdminFieldText>{formatAdminDate(item.lastLoginAt)}</AdminFieldText>,
            },
            {
              key: 'created',
              title: 'Created',
              width: 190,
              sortValue: (item) => item.createdAt,
              render: (item) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/users/${item.userId}`)}
        />
        <AdminPagination
          itemLabel="users"
          onPageChange={(page) => setFilters((current) => ({ ...current, page }))}
          onSizeChange={(size) => setFilters((current) => ({ ...current, page: 0, size }))}
          page={response?.page ?? filters.page ?? 0}
          size={response?.size ?? filters.size ?? PAGE_SIZE}
          totalElements={response?.totalElements ?? 0}
          totalPages={Math.max(response?.totalPages ?? 1, 1)}
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
      style={(state) => [
        styles.chip,
        active ? styles.chipActive : undefined,
        !active && isHovered(state) ? styles.chipHovered : undefined,
        state.pressed ? styles.pressed : undefined,
      ]}>
      <Text style={[styles.chipText, active ? styles.chipTextActive : undefined]}>{label}</Text>
    </Pressable>
  );
}

function isHovered(state: unknown) {
  return Boolean((state as { hovered?: boolean }).hovered);
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
  chipHovered: {
    backgroundColor: '#F1F5F9',
  },
  chipText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  chipTextActive: {
    color: Colors.light.surface,
  },
  pressed: {
    opacity: 0.82,
  },
});
