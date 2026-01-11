import { requireNativeViewManager } from 'expo-modules-core';
import React, { useCallback, useEffect, useMemo } from 'react';
import { ReactNativeLive2DViewProps } from './ReactNativeLive2d.types';

const NativeView: React.ComponentType<ReactNativeLive2DViewProps> = requireNativeViewManager('ReactNativeLive2d');

function ReactNativeLive2dView(props: ReactNativeLive2DViewProps) {
  const {
    modelPath,
    motionGroup,
    expression,
    autoBreath = true,
    autoBlink = true,
    scale = 1.0,
    position = { x: 0, y: 0 },
    onTap,
    onModelLoaded,
    onError,
    onTouchEnd,
    style,
    ...otherProps
  } = props;

  // 添加日志来跟踪 modelPath 的变化
  useEffect(() => {
    console.log('[ReactNativeLive2dView] modelPath:', modelPath);
  }, [modelPath]);

  useEffect(() => {
    console.log('[ReactNativeLive2dView] Props received:', JSON.stringify({
      modelPath,
      motionGroup,
      expression,
      autoBreath,
      autoBlink,
      scale,
      position
    }));
  }, [modelPath, motionGroup, expression, autoBreath, autoBlink, scale, position]);

  // 使用 useCallback 稳定事件处理函数的引用
  const handleTap = useCallback(() => {
    console.log('[ReactNativeLive2dView] Tap event triggered');
    // Android 原生 View 事件名为 onTap，这里统一走 onTap。
    onTap?.();
    // 兼容：历史上曾使用 onTouchEnd 作为点击回调名
    onTouchEnd?.();
  }, [onTap, onTouchEnd]);

  const handleModelLoaded = useCallback(() => {
    console.log('[ReactNativeLive2dView] Model loaded event received');
    onModelLoaded?.();
  }, [onModelLoaded]);

  const handleError = useCallback((error: any) => {
    console.log('[ReactNativeLive2dView] Error event received:', error);
    onError?.(error);
  }, [onError]);

  // 缓存 style 数组，避免每次渲染都创建新数组
  const combinedStyle = useMemo(() => [
    { flex: 1, backgroundColor: 'transparent' as const }, 
    style
  ], [style]);

  console.log('[ReactNativeLive2dView] Rendering with modelPath:', modelPath);

  return (
    <NativeView
      modelPath={modelPath}
      motionGroup={motionGroup}
      expression={expression}
      autoBreath={autoBreath}
      autoBlink={autoBlink}
      scale={scale}
      position={position}
      onTap={handleTap}
      onModelLoaded={handleModelLoaded}
      onError={handleError}
      style={combinedStyle}
      {...otherProps}
    />
  );
}

// 使用 React.memo 避免父组件重新渲染时不必要的子组件渲染
export default React.memo(ReactNativeLive2dView);