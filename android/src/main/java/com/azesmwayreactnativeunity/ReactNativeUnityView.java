package com.azesmwayreactnativeunity;

import static com.azesmwayreactnativeunity.ReactNativeUnity.*;

import android.content.Context;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.util.Log;
import android.widget.FrameLayout;

import java.lang.reflect.InvocationTargetException;

@SuppressLint("ViewConstructor")
public class ReactNativeUnityView extends FrameLayout {
  private static final String TAG = "ReactNativeUnityView";

  private UPlayer view;
  public boolean keepPlayerMounted = false;

  public ReactNativeUnityView(Context context) {
    super(context);
  }

  public void setUnityPlayer(UPlayer player) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    this.view = player;
    addUnityViewToGroup(this);
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);

    // `view` outlives the runtime: after unload() the UPlayer reference is still here but the
    // Unity runtime behind it is gone, so guard on the ready flag before calling into it.
    if (view == null || !isUnityReady()) {
      return;
    }

    view.windowFocusChanged(hasWindowFocus);

    if (!keepPlayerMounted || !_isUnityReady) {
      return;
    }

    // pause Unity on blur, resume on focus
    if (hasWindowFocus && _isUnityPaused) {
      // view.requestFocus();
      view.resume();
    } else if (!hasWindowFocus && !_isUnityPaused) {
      view.pause();
    }
  }

  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (view != null) {
      view.configurationChanged(newConfig);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    if (!this.keepPlayerMounted) {
        try {
            addUnityViewToBackground();
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            // This runs exactly when the Unity screen is being closed. Rethrowing turned a
            // recoverable reflection failure (parking the player off-screen) into a hard crash;
            // the worst case without the move is a player left without a parent.
            Log.e(TAG, "Failed to move the Unity view back to the background", e);
        }
    }

    super.onDetachedFromWindow();
  }
}
