import type { Live2DModule } from './ReactNativeLive2d.types';

/**
 * Web 端占位实现：
 * Expo Web 无法加载原生模块（requireNativeModule 在 web 上不可用）。
 * 这里返回一组 no-op/降级实现，确保打包阶段不报错。
 */
const ReactNativeLive2dModule: Live2DModule = {
  async initializeLive2D() {
    console.warn('[react-native-live2d] initializeLive2D() is not supported on web.');
    return false;
  },
  async getAvailableModels() {
    console.warn('[react-native-live2d] getAvailableModels() is not supported on web.');
    return [];
  },
  async getAvailableMotions(_modelPath: string) {
    console.warn('[react-native-live2d] getAvailableMotions() is not supported on web.');
    return [];
  },
  async getAvailableExpressions(_modelPath: string) {
    console.warn('[react-native-live2d] getAvailableExpressions() is not supported on web.');
    return [];
  },
  async startMotion(_motionGroup: string, _motionIndex: number, _priority: number) {
    console.warn('[react-native-live2d] startMotion() is not supported on web.');
    return false;
  },
  async setExpression(_expressionId: string) {
    console.warn('[react-native-live2d] setExpression() is not supported on web.');
    return false;
  },
  setMouthValue(_value: number) {
    // no-op
  },
  getMouthValue() {
    return 0;
  }
};

export default ReactNativeLive2dModule;
export { ReactNativeLive2dModule };


