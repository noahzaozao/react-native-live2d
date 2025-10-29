import { StyleProp, ViewStyle } from 'react-native';

export interface ReactNativeLive2DViewProps {
  /**
   * Live2D 模型文件路径 (相对于 assets 目录)
   * 例如: "models/Haru/Haru.model3.json"
   */
  modelPath?: string;
  
  /**
   * 动作组名称
   * 例如: "idle", "tap", "shake"
   */
  motionGroup?: string;
  
  /**
   * 表情 ID
   * 例如: "f01", "f02"
   */
  expression?: string;
  
  /**
   * 是否自动播放呼吸动画
   * @default true
   */
  autoBreath?: boolean;
  
  /**
   * 是否自动眨眼
   * @default true
   */
  autoBlink?: boolean;
  
  /**
   * 模型缩放比例
   * @default 1.0
   */
  scale?: number;
  
  /**
   * 模型位置 (X, Y 坐标)
   * @default { x: 0, y: 0 }
   */
  position?: { x: number; y: number };
  
  /**
   * 视图样式
   */
  style?: StyleProp<ViewStyle>;

  /**
   * 触摸结束事件
   */
  onTouchEnd?: () => void;
  
  /**
   * 点击事件回调
   */
  onTap?: () => void;
  
  /**
   * 模型加载完成回调
   */
  onModelLoaded?: () => void;
  
  /**
   * 错误回调
   */
  onError?: (error: string) => void;
}

export interface Live2DModule {
  /**
   * 初始化 Live2D 框架
   */
  initializeLive2D(): Promise<boolean>;

  /**
   * 获取可用模型列表
   */
  getAvailableModels(): Promise<string[]>;
  
  /**
   * 获取可用动作列表
   * @param modelPath 模型文件路径
   */
  getAvailableMotions(modelPath: string): Promise<string[]>;
  
  /**
   * 获取可用表情列表
   * @param modelPath 模型文件路径
   */
  getAvailableExpressions(modelPath: string): Promise<string[]>;
  
  /**
   * 开始播放动作
   * @param motionGroup 动作组名称
   * @param motionIndex 动作索引
   * @param priority 优先级
   */
  startMotion(motionGroup: string, motionIndex: number, priority: number): Promise<boolean>;
  
  /**
   * 设置表情
   * @param expressionId 表情 ID
   */
  setExpression(expressionId: string): Promise<boolean>;
  
  /**
   * 设置口型同步值（用于实时口型同步）
   * @param value 嘴巴开合度（0.0 ~ 1.0）
   */
  setMouthValue(value: number): void;
  
  /**
   * 获取当前口型同步值
   * @returns 当前嘴巴开合度（0.0 ~ 1.0）
   */
  getMouthValue(): number;
}