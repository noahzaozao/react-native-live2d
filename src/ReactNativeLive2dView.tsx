import React, { useEffect, useRef } from 'react';
import { View, ViewStyle } from 'react-native';
import { Live2DViewProps } from './ReactNativeLive2d.types';

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
    ...otherProps
  } = props;

  const viewRef = useRef<View>(null);

  useEffect(() => {
    // 当 modelPath 改变时，通知原生组件加载新模型
    if (modelPath && viewRef.current) {
      // 这里会通过原生桥接调用 loadModel
      console.log('Loading model:', modelPath);
    }
  }, [modelPath]);

  useEffect(() => {
    // 当 motionGroup 改变时，播放对应动作
    if (motionGroup && viewRef.current) {
      console.log('Playing motion:', motionGroup);
    }
  }, [motionGroup]);

  useEffect(() => {
    // 当 expression 改变时，设置表情
    if (expression && viewRef.current) {
      console.log('Setting expression:', expression);
    }
  }, [expression]);

  const handleTap = () => {
    onTap?.();
  };

  const style: ViewStyle = {
    flex: 1,
    backgroundColor: 'transparent',
  };

  return (
    <View
      ref={viewRef}
      style={style}
      onTouchEnd={handleTap}
      {...otherProps}
    />
  );
}