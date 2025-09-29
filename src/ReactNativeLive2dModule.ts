import { requireNativeModule } from 'expo-modules-core';
import { Live2DModule } from './ReactNativeLive2d.types';

// 与 Android 模块定义 Name("ReactNativeLive2d") 对应
const ReactNativeLive2dModule = requireNativeModule('ReactNativeLive2d');

export default ReactNativeLive2dModule as Live2DModule;
export { ReactNativeLive2dModule };
