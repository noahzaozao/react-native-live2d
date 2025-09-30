import { requireNativeViewManager } from 'expo-modules-core';
import React, { useEffect } from 'react';
import { ReactNativeLive2DViewProps } from './ReactNativeLive2d.types';

const NativeView: React.ComponentType<ReactNativeLive2DViewProps> = requireNativeViewManager('ReactNativeLive2d');

export default function ReactNativeLive2dView(props: ReactNativeLive2DViewProps) {
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
    style,
    ...otherProps
  } = props;

  // 添加日志来跟踪 modelPath 的变化
  useEffect(() => {
    console.log('[ReactNativeLive2dView] modelPath changed:', modelPath);
  }, [modelPath]);

  useEffect(() => {
    console.log('[ReactNativeLive2dView] Props received:', {
      modelPath,
      motionGroup,
      expression,
      autoBreath,
      autoBlink,
      scale,
      position
    });
  }, [modelPath, motionGroup, expression, autoBreath, autoBlink, scale, position]);

  const handleTap = () => {
    console.log('[ReactNativeLive2dView] Tap event triggered');
    onTap?.();
  };

  const handleModelLoaded = () => {
    console.log('[ReactNativeLive2dView] Model loaded event received');
    onModelLoaded?.();
  };

  const handleError = (error: any) => {
    console.log('[ReactNativeLive2dView] Error event received:', error);
    onError?.(error);
  };

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
      onTouchEnd={handleTap}
      onModelLoaded={handleModelLoaded}
      onError={handleError}
      style={[{ flex: 1, backgroundColor: 'transparent' }, style]}
      {...otherProps}
    />
  );
}