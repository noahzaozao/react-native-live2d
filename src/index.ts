// Reexport the native module. On web, it will be resolved to ReactNativeLive2dModule.web.ts
// and on native platforms to ReactNativeLive2dModule.ts
export * from './ReactNativeLive2d.types';
export { default as ReactNativeLive2dModule } from './ReactNativeLive2dModule';
export { default as ReactNativeLive2dView } from './ReactNativeLive2dView';

