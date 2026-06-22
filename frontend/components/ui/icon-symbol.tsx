// Fallback for using MaterialIcons on Android and web.

import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { SymbolWeight, SymbolViewProps } from 'expo-symbols';
import { ComponentProps } from 'react';
import { OpaqueColorValue, type StyleProp, type TextStyle } from 'react-native';

type IconMapping = Record<SymbolViewProps['name'], ComponentProps<typeof MaterialIcons>['name']>;
type IconSymbolName = keyof typeof MAPPING;

/**
 * Add your SF Symbols to Material Icons mappings here.
 * - see Material Icons in the [Icons Directory](https://icons.expo.fyi).
 * - see SF Symbols in the [SF Symbols](https://developer.apple.com/sf-symbols/) app.
 */
const MAPPING = {
  'house.fill': 'home',
  'paperplane.fill': 'send',
  'chart.line.uptrend.xyaxis': 'show-chart',
  'lightbulb.fill': 'lightbulb',
  'briefcase.fill': 'business-center',
  'person.crop.circle.fill': 'account-circle',
  'lock.shield.fill': 'admin-panel-settings',
  'chevron.left.forwardslash.chevron.right': 'code',
  'chevron.left': 'chevron-left',
  'chevron.right': 'chevron-right',
  'chevron.down': 'keyboard-arrow-down',
  'chevron.up': 'keyboard-arrow-up',
  'arrow.up': 'arrow-upward',
  'arrow.down': 'arrow-downward',
  'arrowtriangle.up.fill': 'arrow-drop-up',
  'arrowtriangle.down.fill': 'arrow-drop-down',
  'minus': 'remove',
  magnifyingglass: 'search',
  'arrow.clockwise': 'refresh',
  heart: 'favorite-border',
  'heart.fill': 'favorite',
  'arrow.up.arrow.down': 'swap-vert',
  'line.3.horizontal.decrease': 'sort',
  trash: 'delete-outline',
  'hammer.fill': 'construction',
  xmark: 'close',
  'xmark.circle.fill': 'cancel',
  checkmark: 'check',
  'checkmark.circle.fill': 'check-circle',
  eye: 'visibility',
  'eye.slash': 'visibility-off',
} as IconMapping;

/**
 * An icon component that uses native SF Symbols on iOS, and Material Icons on Android and web.
 * This ensures a consistent look across platforms, and optimal resource usage.
 * Icon `name`s are based on SF Symbols and require manual mapping to Material Icons.
 */
export function IconSymbol({
  name,
  size = 24,
  color,
  style,
}: {
  name: IconSymbolName;
  size?: number;
  color: string | OpaqueColorValue;
  style?: StyleProp<TextStyle>;
  weight?: SymbolWeight;
}) {
  return <MaterialIcons color={color} size={size} name={MAPPING[name]} style={style} />;
}
