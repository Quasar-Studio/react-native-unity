package com.azesmwayreactnativeunity;

import static com.azesmwayreactnativeunity.ReactNativeUnity.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@ReactModule(name = ReactNativeUnityViewManager.NAME)
public class ReactNativeUnityViewManager extends ReactNativeUnityViewManagerSpec<ReactNativeUnityView> implements LifecycleEventListener, View.OnAttachStateChangeListener {
  ReactApplicationContext context;
  static ReactNativeUnityView view;
  public static final String NAME = "RNUnityView";

  public ReactNativeUnityViewManager(ReactApplicationContext context) {
    super();
    this.context = context;
    context.addLifecycleEventListener(this);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @NonNull
  @Override
  public ReactNativeUnityView createViewInstance(@NonNull ThemedReactContext context) {
    // Build the view with the ThemedReactContext handed to us, not with the
    // ReactApplicationContext: on Fabric the surfaceId (needed to route events to JS) is only
    // reachable through a ThemedReactContext, and it is what every other RN view manager does.
    view = new ReactNativeUnityView(context);
    view.addOnAttachStateChangeListener(this);

    if (getPlayer() != null) {
        try {
            view.setUnityPlayer(getPlayer());
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {}
    } else {
        try {
            createPlayer(context.getCurrentActivity(), new UnityPlayerCallback() {
              @Override
              public void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
                // Unity boots asynchronously (createPlayer sleeps ~1s before signalling), so the
                // screen may already be closed by the time we get here. Re-read the current view
                // instead of assuming the one we created is still alive.
                ReactNativeUnityView target = ReactNativeUnityViewManager.view;
                if (target == null || getPlayer() == null) {
                  return;
                }
                target.setUnityPlayer(getPlayer());
              }

              @Override
              public void onUnload() {
                emitEvent("onPlayerUnload", "MyMessage");
              }

              @Override
              public void onQuit() {
                emitEvent("onPlayerQuit", "MyMessage");
              }
            });
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {}
    }

    return view;
  }

  @Override
  public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
    Map<String, Object> export = super.getExportedCustomDirectEventTypeConstants();

    if (export == null) {
      export = MapBuilder.newHashMap();
    }

    export.put("onUnityMessage", MapBuilder.of("registrationName", "onUnityMessage"));
    export.put("onPlayerUnload", MapBuilder.of("registrationName", "onPlayerUnload"));
    export.put("onPlayerQuit", MapBuilder.of("registrationName", "onPlayerQuit"));

    return export;
  }

  @Override
  public void receiveCommand(@NonNull ReactNativeUnityView view, String commandType, @Nullable ReadableArray args) {
    Assertions.assertNotNull(view);
    Assertions.assertNotNull(args);

    switch (commandType) {
      case "postMessage":
        assert args != null;
        postMessage(view, args.getString(0), args.getString(1), args.getString(2));
        return;
      case "unloadUnity":
        unloadUnity(view);
        return;
      case "pauseUnity":
        assert args != null;
        pauseUnity(view, args.getBoolean(0));
        return;
      case "resumeUnity":
        resumeUnity(view);
        return;
      case "windowFocusChanged":
        assert args != null;
        windowFocusChanged(view, args.getBoolean(0));
        return;
      default:
        throw new IllegalArgumentException(String.format(
          "Unsupported command %s received by %s.",
          commandType,
          getClass().getSimpleName()));
    }
  }

  @Override
  public void unloadUnity(ReactNativeUnityView view) {
    if (isUnityReady()) {
      unload();
    }
  }

  @Override
  public void pauseUnity(ReactNativeUnityView view, boolean pause) {
    if (isUnityReady()) {
      // Honor the argument (previously always paused) and go through
      // ReactNativeUnity.pause/resume so `_isUnityPaused` stays in sync — the flag
      // is what onHostResume/restoreUnityUserState rely on.
      if (pause) {
        ReactNativeUnity.pause();
      } else {
        ReactNativeUnity.resume();
      }
    }
  }

  @Override
  public void resumeUnity(ReactNativeUnityView view) {
    if (isUnityReady()) {
      ReactNativeUnity.resume();
    }
  }

  @Override
  public void windowFocusChanged(ReactNativeUnityView view, boolean hasFocus) {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().windowFocusChanged(hasFocus);
    }
  }

  public static void sendMessageToMobileApp(String message) {
    emitEvent("onUnityMessage", message);
  }

  /**
   * Deliver a view event to JS defensively.
   *
   * <p>Unity's lifecycle callbacks (onUnityPlayerUnloaded / onUnityPlayerQuitted) and
   * UnitySendMessage fire asynchronously from Unity's own code, long after the command that
   * triggered them returned. Closing the Unity screen unmounts &lt;UnityView&gt;, which dispatches
   * `unloadUnity` and then drops the view instance — `onDropViewInstance` clears the static
   * `view`, so by the time Unity reports "unloaded" there is nothing to emit on and the old
   * `view.getContext()` crashed with an NPE on the main thread.
   *
   * <p>Besides the null view, the previous implementation had three more ways to blow up or
   * silently misbehave: it could run off the UI thread, it could touch a React context whose
   * instance was already torn down, and `getJSModule(RCTEventEmitter)` does not deliver events to
   * Fabric views on the new architecture. Going through the EventDispatcher covers both
   * architectures.
   */
  private static void emitEvent(final String eventName, final String message) {
    emitEvent(view, eventName, message);
  }

  private static void emitEvent(@Nullable final ReactNativeUnityView target, final String eventName, final String message) {
    // Snapshot the view: the static field can be nulled out by onDropViewInstance at any moment.
    if (target == null) {
      return;
    }

    if (!UiThreadUtil.isOnUiThread()) {
      UiThreadUtil.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          emitEvent(target, eventName, message);
        }
      });
      return;
    }

    final int viewTag = target.getId();
    if (viewTag == View.NO_ID) {
      // View is no longer registered with the UIManager — nothing to dispatch to.
      return;
    }

    final ReactContext reactContext = resolveReactContext(target);
    if (reactContext == null || !reactContext.hasActiveReactInstance()) {
      // The React instance is gone (screen/app teardown); dropping the event is the only
      // sane thing to do here.
      return;
    }

    final EventDispatcher dispatcher = UIManagerHelper.getEventDispatcherForReactTag(reactContext, viewTag);
    if (dispatcher == null) {
      return;
    }

    WritableMap data = Arguments.createMap();
    data.putString("message", message);
    dispatcher.dispatchEvent(new UnityViewEvent(UIManagerHelper.getSurfaceId(target), viewTag, eventName, data));
  }

  @Nullable
  private static ReactContext resolveReactContext(View target) {
    Context context = target.getContext();
    while (context instanceof ContextWrapper) {
      if (context instanceof ReactContext) {
        return (ReactContext) context;
      }
      context = ((ContextWrapper) context).getBaseContext();
    }

    return context instanceof ReactContext ? (ReactContext) context : null;
  }

  /**
   * Generic direct event carrying `{ message }`. Works on both architectures: Paper uses
   * `dispatch()` (via getEventData), Fabric uses `dispatchModern()` with the surfaceId.
   */
  private static class UnityViewEvent extends Event<UnityViewEvent> {
    private final String eventName;
    private final WritableMap eventData;

    UnityViewEvent(int surfaceId, int viewTag, String eventName, WritableMap eventData) {
      super(surfaceId, viewTag);
      this.eventName = eventName;
      this.eventData = eventData;
    }

    @NonNull
    @Override
    public String getEventName() {
      return eventName;
    }

    @Nullable
    @Override
    protected WritableMap getEventData() {
      return eventData;
    }
  }

  @Override
  public void onDropViewInstance(ReactNativeUnityView view) {
    view.removeOnAttachStateChangeListener(this);
    // Clear the static reference so we don't leak this destroyed view (and its
    // Activity/Context) or dispatch Unity callbacks to a stale instance.
    if (ReactNativeUnityViewManager.view == view) {
      ReactNativeUnityViewManager.view = null;
    }
    super.onDropViewInstance(view);
  }

  @Override
  public void onHostResume() {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().resume();
      restoreUnityUserState();
    }
  }

  @Override
  public void onHostPause() {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().pause();
    }
  }

  @Override
  public void onHostDestroy() {
    if (isUnityReady()) {
      // Go through ReactNativeUnity so the shared state is cleared with the player — calling
      // UPlayer.destroy() directly left `_isUnityReady` true for a destroyed runtime.
      ReactNativeUnity.destroy();
    }
  }

  private void restoreUnityUserState() {
    // restore the unity player state
    if (isUnityPaused()) {
      Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (getPlayer() != null) {
            getPlayer().pause();
          }
        }
      }, 300);
    }
  }

  @Override
  public void onViewAttachedToWindow(View v) {
    restoreUnityUserState();
  }

  @Override
  public void onViewDetachedFromWindow(View v) {}

  @ReactProp(name = "androidKeepPlayerMounted", defaultBoolean = false)
  public void setAndroidKeepPlayerMounted(ReactNativeUnityView view, boolean keepPlayerMounted) {
    view.keepPlayerMounted = keepPlayerMounted;
  }

  @ReactProp(name = "fullScreen", defaultBoolean = true)
  public void setFullScreen(ReactNativeUnityView view, boolean fullScreen) {
    _fullScreen = fullScreen;
  }

  @Override
  public void postMessage(ReactNativeUnityView view, String gameObject, String methodName, String message) {
    if (isUnityReady()) {
      assert getPlayer() != null;
      UPlayer.UnitySendMessage(gameObject, methodName, message);
    }
  }
}
