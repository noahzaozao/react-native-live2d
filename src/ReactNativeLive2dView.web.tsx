import * as React from 'react';

import { ReactNativeLive2dViewProps } from './ReactNativeLive2d.types';

export default function ReactNativeLive2dView(props: ReactNativeLive2dViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
