import React, { useEffect } from 'react';
import { Text, View } from 'react-native';
import type { ReactNativeLive2DViewProps } from './ReactNativeLive2d.types';

/**
 * Web 端占位实现：
 * - Expo Web 不支持 NativeView（requireNativeViewManager 在 web 上不可用）
 * - 这里提供一个可渲染组件，避免打包/SSR 时报错
 */
export default function ReactNativeLive2dView(props: ReactNativeLive2DViewProps) {
  const { style, onError } = props;

  useEffect(() => {
    const message = 'ReactNativeLive2dView is not supported on web.';
    // 保持行为可观察：在开发时提示一次即可
    console.warn(`[react-native-live2d] ${message}`);
    onError?.(message);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View style={style}>
      <Text selectable={false}>
        Live2D（Native）组件在 Web 上不可用
      </Text>
    </View>
  );
}


