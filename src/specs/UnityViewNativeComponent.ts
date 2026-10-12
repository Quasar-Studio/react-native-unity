import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import codegenNativeCommands from 'react-native/Libraries/Utilities/codegenNativeCommands';
import type { HostComponent, ViewProps } from 'react-native';
import type {
  DirectEventHandler,
  Int32,
} from 'react-native/Libraries/Types/CodegenTypes';

// Android also puts `playerTag: number` into every event payload (see UnityView.tsx). It is not
// declared here on purpose: a codegen event field would make the iOS Fabric emitter send 0.
export type UnityViewContentUpdateEvent = Readonly<{
  message: string;
}>;

export interface NativeProps extends ViewProps {
  androidKeepPlayerMounted?: boolean;
  fullScreen?: boolean;
  playerTag?: Int32;
  onUnityMessage?: DirectEventHandler<UnityViewContentUpdateEvent>;
  onPlayerUnload?: DirectEventHandler<UnityViewContentUpdateEvent>;
  onPlayerQuit?: DirectEventHandler<UnityViewContentUpdateEvent>;
}

export interface NativeCommands {
  postMessage: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>,
    gameObject: string,
    methodName: string,
    message: string
  ) => void;
  unloadUnity: (viewRef: React.ElementRef<HostComponent<NativeProps>>) => void;
  pauseUnity: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>,
    pause: boolean
  ) => void;
  resumeUnity: (viewRef: React.ElementRef<HostComponent<NativeProps>>) => void;
  windowFocusChanged: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>,
    hasFocus: boolean
  ) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: [
    'postMessage',
    'unloadUnity',
    'pauseUnity',
    'resumeUnity',
    'windowFocusChanged',
  ],
});

export default codegenNativeComponent<NativeProps>(
  'RNUnityView'
) as HostComponent<NativeProps>;
