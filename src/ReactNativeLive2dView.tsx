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
    position = { x: 0, y: 0 },
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
      position={position}
      onTouchEnd={handleTap}
      style={[{ flex: 1, backgroundColor: 'transparent' }, style]}
      {...otherProps}
    />
  );
}