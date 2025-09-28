# React Native Live2D

一个用于在 React Native 应用中显示 Live2D 角色的原生模块。

## 功能特性

- 🎭 支持 Live2D Cubism 5.0 模型
- 🎬 动作播放和表情控制
- 👁️ 自动眨眼和呼吸动画
- 📱 支持 Android 平台
- 🎮 触摸交互
- ⚡ 高性能 OpenGL ES 渲染

## 安装

```bash
npm install react-native-live2d
```

## 使用方法

### 基本用法

```tsx
import React from 'react';
import { View } from 'react-native';
import { ReactNativeLive2dView } from 'react-native-live2d';

export default function App() {
  return (
    <View style={{ flex: 1 }}>
      <ReactNativeLive2dView
        modelPath="models/Haru/Haru.model3.json"
        motionGroup="idle"
        expression="f01"
        autoBreath={true}
        autoBlink={true}
        onTap={() => console.log('角色被点击了！')}
        onModelLoaded={() => console.log('模型加载完成')}
        onError={(error) => console.error('加载错误:', error)}
      />
    </View>
  );
}
```

### 高级用法

```tsx
import React, { useState } from 'react';
import { ReactNativeLive2dView, ReactNativeLive2dModule } from 'react-native-live2d';

export default function AdvancedExample() {
  const [currentMotion, setCurrentMotion] = useState('idle');
  const [currentExpression, setCurrentExpression] = useState('f01');

  const handleMotionChange = async (motionGroup: string) => {
    try {
      // 预加载模型
      await ReactNativeLive2dModule.preloadModel('models/Haru/Haru.model3.json');
      
      // 获取可用动作
      const motions = await ReactNativeLive2dModule.getAvailableMotions('models/Haru/Haru.model3.json');
      console.log('可用动作:', motions);
      
      setCurrentMotion(motionGroup);
    } catch (error) {
      console.error('动作切换失败:', error);
    }
  };

  return (
    <ReactNativeLive2dView
      modelPath="models/Haru/Haru.model3.json"
      motionGroup={currentMotion}
      expression={currentExpression}
      scale={1.2}
      offsetX={0}
      offsetY={50}
      onTap={() => handleMotionChange('tap')}
    />
  );
}
```

## API 参考

### ReactNativeLive2dView Props

| 属性 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `modelPath` | `string` | - | Live2D 模型文件路径（相对于 assets 目录） |
| `motionGroup` | `string` | - | 动作组名称 |
| `expression` | `string` | - | 表情 ID |
| `autoBreath` | `boolean` | `true` | 是否自动播放呼吸动画 |
| `autoBlink` | `boolean` | `true` | 是否自动眨眼 |
| `scale` | `number` | `1.0` | 模型缩放比例 |
| `offsetX` | `number` | `0` | 模型 X 轴偏移 |
| `offsetY` | `number` | `0` | 模型 Y 轴偏移 |
| `onTap` | `() => void` | - | 点击事件回调 |
| `onModelLoaded` | `() => void` | - | 模型加载完成回调 |
| `onError` | `(error: string) => void` | - | 错误回调 |

### ReactNativeLive2dModule 方法

| 方法 | 参数 | 返回值 | 描述 |
|------|------|--------|------|
| `preloadModel` | `modelPath: string` | `Promise<void>` | 预加载模型资源 |
| `releaseModel` | `modelPath: string` | `Promise<void>` | 释放模型资源 |
| `getAvailableMotions` | `modelPath: string` | `Promise<string[]>` | 获取可用动作列表 |
| `getAvailableExpressions` | `modelPath: string` | `Promise<string[]>` | 获取可用表情列表 |

## 模型文件准备

1. 将 Live2D 模型文件（.model3.json, .moc3, .png 等）放入 `android/app/src/main/assets/` 目录
2. 确保模型文件结构正确，例如：
   ```
   assets/
   └── models/
       └── Haru/
           ├── Haru.model3.json
           ├── Haru.moc3
           ├── Haru.2048/
           │   └── texture_00.png
           └── motions/
               └── idle/
                   └── 01.motion3.json
   ```

## 开发说明

### 需要复制的 Cubism SDK 文件

在集成 Cubism SDK 时，需要将以下文件复制到模块中：

```
packages/react-native-live2d/android/src/main/java/com/live2d/sdk/cubism/framework/
```

从 `CubismSdkForJava-5-r.4.1/Framework/framework/src/main/java/com/live2d/sdk/cubism/framework/` 复制所有 Java 文件。

### 构建要求

- Android API 24+
- OpenGL ES 2.0+
- Live2D Cubism Core for Java

## 许可证

MIT License