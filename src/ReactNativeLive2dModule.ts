import { NativeModule, requireNativeModule } from 'expo';

import { ReactNativeLive2dModuleEvents } from './ReactNativeLive2d.types';

declare class ReactNativeLive2dModule extends NativeModule<ReactNativeLive2dModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReactNativeLive2dModule>('ReactNativeLive2d');
