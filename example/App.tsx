import React, { useState } from 'react';
import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import ReactNativeLive2dView from '../src/ReactNativeLive2dView';

export default function App() {
  const [currentMotion, setCurrentMotion] = useState('idle');
  const [currentExpression, setCurrentExpression] = useState('f01');
  
  const motions = ['idle', 'tap', 'shake'];
  const expressions = ['f01', 'f02', 'f03'];

  const handleModelLoaded = () => {
    Alert.alert('成功', 'Live2D 模型加载完成！');
  };

  const handleError = (error: string) => {
    Alert.alert('错误', error);
  };

  const handleTap = () => {
    // 随机播放一个动作
    const randomMotion = motions[Math.floor(Math.random() * motions.length)];
    setCurrentMotion(randomMotion);
  };

  const changeExpression = (expression: string) => {
    setCurrentExpression(expression);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Live2D 示例</Text>
      
      <View style={styles.live2dContainer}>
        <ReactNativeLive2dView
          modelPath="models/Haru/Haru.model3.json"
          motionGroup={currentMotion}
          expression={currentExpression}
          autoBreath={true}
          autoBlink={true}
          scale={1.0}
          onModelLoaded={handleModelLoaded}
          onError={handleError}
          onTap={handleTap}
        />
      </View>

      <View style={styles.controls}>
        <Text style={styles.controlTitle}>动作控制</Text>
        <View style={styles.buttonRow}>
          {motions.map((motion) => (
            <TouchableOpacity
              key={motion}
              style={[
                styles.button,
                currentMotion === motion && styles.activeButton
              ]}
              onPress={() => setCurrentMotion(motion)}
            >
              <Text style={styles.buttonText}>{motion}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={styles.controlTitle}>表情控制</Text>
        <View style={styles.buttonRow}>
          {expressions.map((expression) => (
            <TouchableOpacity
              key={expression}
              style={[
                styles.button,
                currentExpression === expression && styles.activeButton
              ]}
              onPress={() => changeExpression(expression)}
            >
              <Text style={styles.buttonText}>{expression}</Text>
            </TouchableOpacity>
          ))}
        </View>

      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f0f0f0',
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
    color: '#333',
  },
  live2dContainer: {
    height: 300,
    backgroundColor: '#fff',
    borderRadius: 10,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  controls: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 15,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  controlTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333',
  },
  buttonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 15,
  },
  button: {
    backgroundColor: '#e0e0e0',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 5,
    marginRight: 8,
    marginBottom: 8,
  },
  activeButton: {
    backgroundColor: '#007AFF',
  },
  buttonText: {
    color: '#333',
    fontSize: 12,
  },
});