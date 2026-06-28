import { Stack } from 'expo-router';

export default function WatchlistLayout() {
  return (
    <Stack
      screenOptions={{
        animation: 'slide_from_right',
        headerShown: false,
      }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="edit" />
    </Stack>
  );
}
