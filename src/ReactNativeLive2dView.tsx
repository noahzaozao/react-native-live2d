import { requireNativeView } from 'expo';
import * as React from 'react';

import { ReactNativeLive2dViewProps } from './ReactNativeLive2d.types';

const NativeView: React.ComponentType<ReactNativeLive2dViewProps> =
  requireNativeView('ReactNativeLive2d');

export default function ReactNativeLive2dView(props: ReactNativeLive2dViewProps) {
  return <NativeView {...props} />;
}
