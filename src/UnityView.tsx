import React from 'react';

import NativeUnityView, { Commands } from './specs/UnityViewNativeComponent';
import type { DirectEventHandler } from 'react-native/Libraries/Types/CodegenTypes';
import { Platform } from 'react-native';
import type { ViewProps } from 'react-native';

type UnityViewContentUpdateEvent = Readonly<{
  message: string;
  /**
   * Android: the `playerTag` prop of the view the Unity player was created for. Events of an
   * older player (still unloading) can reach a newer view; compare tags to tell them apart.
   * iOS sends no tag.
   */
  playerTag?: number;
}>;

type RNUnityViewProps = ViewProps & {
  androidKeepPlayerMounted?: boolean;
  fullScreen?: boolean;
  /** Android: stamped on the player this view creates and echoed in its events. */
  playerTag?: number;
  onUnityMessage?: DirectEventHandler<UnityViewContentUpdateEvent>;
  onPlayerUnload?: DirectEventHandler<UnityViewContentUpdateEvent>;
  onPlayerQuit?: DirectEventHandler<UnityViewContentUpdateEvent>;
};

type ComponentRef = InstanceType<typeof NativeUnityView>;

export default class UnityView extends React.Component<RNUnityViewProps> {
  ref = React.createRef<ComponentRef>();

  public postMessage = (
    gameObject: string,
    methodName: string,
    message: string
  ) => {
    if (this.ref.current) {
      Commands.postMessage(this.ref.current, gameObject, methodName, message);
    }
  };

  public unloadUnity = () => {
    if (this.ref.current) {
      Commands.unloadUnity(this.ref.current);
    }
  };

  public pauseUnity(pause: boolean) {
    if (this.ref.current) {
      Commands.pauseUnity(this.ref.current, pause);
    }
  }

  public resumeUnity() {
    if (this.ref.current) {
      Commands.resumeUnity(this.ref.current);
    }
  }

  public windowFocusChanged(hasFocus = true) {
    if (Platform.OS !== 'android') return;

    if (this.ref.current) {
      Commands.windowFocusChanged(this.ref.current, hasFocus);
    }
  }

  private getProps() {
    return {
      ...this.props,
    };
  }

  componentWillUnmount() {
    // Full React unmount is a permanent teardown, so we unload the engine here.
    // This is intentionally different from Fabric view *recycling*
    // (RNUnityView.prepareForRecycle), which only pauses the shared instance so
    // it can be reused on the next mount. See issue #180.
    if (this.ref.current) {
      Commands.unloadUnity(this.ref.current);
    }
  }

  render() {
    return <NativeUnityView ref={this.ref} {...this.getProps()} />;
  }
}
