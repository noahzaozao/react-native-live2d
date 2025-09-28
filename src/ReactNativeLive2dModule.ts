import { NativeModules, Platform } from 'react-native';
import { Live2DModule } from './ReactNativeLive2d.types';

const LINKING_ERROR =
  `The package 'react-native-live2d' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'cd ios && pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const ReactNativeLive2dModule = NativeModules.ReactNativeLive2dModule
  ? NativeModules.ReactNativeLive2dModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export default ReactNativeLive2dModule as Live2DModule;
export { ReactNativeLive2dModule };
