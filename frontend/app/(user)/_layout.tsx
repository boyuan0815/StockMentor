import { Tabs } from 'expo-router';
import { Text } from 'react-native';

import { HapticTab } from '@/components/haptic-tab';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors } from '@/constants/theme';
import { ProtectedRoute } from '@/utils/route-guards';

export default function UserLayout() {
  return (
    <ProtectedRoute allowedRoles={['BEGINNER_INVESTOR']} requireCompletedOnboarding>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: Colors.light.tint,
          tabBarInactiveTintColor: Colors.light.tabIconDefault,
          tabBarButton: HapticTab,
          tabBarStyle: {
            backgroundColor: Colors.light.surface,
            borderTopColor: Colors.light.border,
          },
          tabBarLabelStyle: {
            fontSize: 9.5,
          },
          sceneStyle: {
            backgroundColor: Colors.light.background,
          },
        }}>
        <Tabs.Screen
          name="watchlist"
          options={{
            title: 'Watchlist',
            tabBarIcon: ({ color }) => <IconSymbol name="heart.fill" color={color} />,
          }}
        />
        <Tabs.Screen
          name="stocks/index"
          options={{
            title: 'Stocks',
            tabBarIcon: ({ color }) => (
              <IconSymbol name="chart.line.uptrend.xyaxis" color={color} />
            ),
          }}
        />
        <Tabs.Screen
          name="suggestions/index"
          options={{
            title: 'Suggestions',
            tabBarLabel: ({ color }) => (
              <Text
                allowFontScaling={false}
                numberOfLines={1}
                style={{ color, fontSize: 8.5, fontWeight: '600', includeFontPadding: false }}>
                Suggestions
              </Text>
            ),
            tabBarIcon: ({ color }) => <IconSymbol name="lightbulb.fill" color={color} />
          }}
        />
        <Tabs.Screen
          name="paper-trading/index"
          options={{
            title: 'Portfolio',
            tabBarIcon: ({ color }) => <IconSymbol name="briefcase.fill" color={color} />,
          }}
        />
        <Tabs.Screen
          name="profile"
          options={{
            title: 'Profile',
            tabBarIcon: ({ color }) => (
              <IconSymbol name="person.crop.circle.fill" color={color} />
            ),
          }}
        />
        <Tabs.Screen
          name="stocks/search"
          options={{
            href: null,
            title: 'Search',
            tabBarIcon: ({ color }) => <IconSymbol name="magnifyingglass" color={color} />,
          }}
        />
        <Tabs.Screen name="stocks/[symbol]" options={{ href: null }} />
        <Tabs.Screen name="stocks/[symbol]/explanation" options={{ href: null }} />
        <Tabs.Screen name="stocks/search-context" options={{ href: null }} />
        <Tabs.Screen name="dashboard" options={{ href: null }} />
        <Tabs.Screen name="paper-trading/buy" options={{ href: null }} />
        <Tabs.Screen name="paper-trading/sell" options={{ href: null }} />
        <Tabs.Screen name="paper-trading/transactions" options={{ href: null }} />
        <Tabs.Screen name="paper-trading/transactions/[transactionId]" options={{ href: null }} />
      </Tabs>
    </ProtectedRoute>
  );
}
