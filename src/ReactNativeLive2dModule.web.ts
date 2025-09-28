import { registerWebModule, NativeModule } from 'expo';

import { ReactNativeLive2dModuleEvents } from './ReactNativeLive2d.types';

class ReactNativeLive2dModule extends NativeModule<ReactNativeLive2dModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ReactNativeLive2dModule, 'ReactNativeLive2dModule');
