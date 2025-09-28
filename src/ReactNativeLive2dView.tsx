import React from 'react';
import { requireNativeViewManager } from 'expo-modules-core';
import { Live2DViewProps } from './ReactNativeLive2d.types';

const NativeView: React.ComponentType<Live2DViewProps> = requireNativeViewManager('ReactNativeLive2d');

export default function ReactNativeLive2dView(props: Live2DViewProps) {
  const {
    modelPath,
    motionGroup,
    expression,
    autoBreath = true,
    autoBlink = true,
    scale = 1.0,
    offsetX = 0,
    offsetY = 0,
    onTap,
    onModelLoaded,
    onError,
    style,
    ...otherProps
  } = props;

  const handleTap = () => {
    onTap?.();
  };

  return (
    <NativeView
      modelPath={modelPath}
      motionGroup={motionGroup}
      expression={expression}
      autoBreath={autoBreath}
      autoBlink={autoBlink}
      scale={scale}
      offsetX={offsetX}
      offsetY={offsetY}
      onTouchEnd={handleTap}
      style={[{ flex: 1, backgroundColor: 'transparent' }, style]}
      {...otherProps}
    />
  );
}