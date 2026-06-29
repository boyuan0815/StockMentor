import { Stack } from 'expo-router';

import { AdminGate } from '@/components/admin/admin-ui';

export default function AdminLayout() {
  return (
    <AdminGate>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="admin" options={{ title: 'Admin dashboard' }} />
        <Stack.Screen name="admin/users/index" options={{ title: 'Users' }} />
        <Stack.Screen name="admin/users/[userId]" options={{ title: 'User detail' }} />
        <Stack.Screen name="admin/stocks/maintenance" options={{ title: 'Stock maintenance' }} />
        <Stack.Screen name="admin/ai-suggestions/index" options={{ title: 'AI monitoring' }} />
        <Stack.Screen
          name="admin/ai-suggestions/batches/[batchId]"
          options={{ title: 'AI batch detail' }}
        />
        <Stack.Screen
          name="admin/ai-suggestions/jobs/[jobId]"
          options={{ title: 'Refresh job detail' }}
        />
      </Stack>
    </AdminGate>
  );
}
