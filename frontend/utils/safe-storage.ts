import AsyncStorage from '@react-native-async-storage/async-storage';

const memoryStorage = new Map<string, string>();

export async function safeGetItem(key: string): Promise<string | null> {
  try {
    const value = await AsyncStorage.getItem(key);
    if (value !== null) {
      memoryStorage.set(key, value);
      return value;
    }
  } catch {
    return memoryStorage.get(key) ?? null;
  }

  return memoryStorage.get(key) ?? null;
}

export async function safeSetItem(key: string, value: string): Promise<void> {
  memoryStorage.set(key, value);

  try {
    await AsyncStorage.setItem(key, value);
  } catch {
    // Keep the in-memory value so Expo Go/native-module failures never red-screen.
  }
}

export async function safeRemoveItem(key: string): Promise<void> {
  memoryStorage.delete(key);

  try {
    await AsyncStorage.removeItem(key);
  } catch {
    // The in-memory fallback is already cleared.
  }
}
