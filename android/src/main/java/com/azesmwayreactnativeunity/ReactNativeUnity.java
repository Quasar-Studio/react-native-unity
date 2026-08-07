package com.azesmwayreactnativeunity;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import java.lang.reflect.InvocationTargetException;

public class ReactNativeUnity {
    private static UPlayer unityPlayer;
    public static boolean _isUnityReady;
    public static boolean _isUnityPaused;
    public static boolean _fullScreen = true;

    /**
     * `unload()` was called and Unity has not reported back yet. The runtime is still tearing
     * itself down, so we must neither touch it nor start a second instance in the meantime.
     */
    private static boolean _isUnityUnloading;

    /**
     * Unity was quit/destroyed. Unity as a Library cannot boot a second time inside the same
     * process, so every later create request is refused instead of crashing natively.
     */
    private static boolean _isUnityQuit;

    /**
     * A player is being built. Creation is posted to the UI thread, so two views mounted in the
     * same frame both saw `unityPlayer == null` and each started its own Unity runtime.
     */
    private static boolean _isUnityCreating;

    // A create request that arrived while the previous runtime was still unloading.
    private static Activity _pendingActivity;
    private static UnityPlayerCallback _pendingCallback;

    // Safety net: some Unity versions never deliver onUnityPlayerUnloaded. Without it we would
    // stay in the "unloading" state forever and never accept a new player.
    private static final long UNLOAD_TIMEOUT_MS = 5000;

    private static final String TAG = "ReactNativeUnity";

    public static UPlayer getPlayer() {
        if (!_isUnityReady) {
            return null;
        }
        return unityPlayer;
    }

    public static boolean isUnityReady() {
        return _isUnityReady;
    }

    public static boolean isUnityPaused() {
        return _isUnityPaused;
    }

    public static void createPlayer(final Activity activity, final UnityPlayerCallback callback) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if (_isUnityQuit) {
            // Unity kills its own runtime on quit; a fresh UnityPlayer would crash the process.
            Log.e(TAG, "Unity has been quit — it cannot be started again in this process");
            return;
        }

        if (_isUnityUnloading) {
            // Remember the request and replay it from onUnload(): two Unity runtimes cannot
            // coexist in one process, and the unload is asynchronous.
            _pendingActivity = activity;
            _pendingCallback = callback;
            return;
        }

        if (unityPlayer != null && _isUnityReady) {
            callback.onReady();

            return;
        }

        if (_isUnityCreating) {
            // Already booting: the pending onReady attaches the player to whichever view is
            // current by then, so a second runtime must not be started here.
            return;
        }

        // A player left over from an unloaded runtime cannot be reused — drop it and build a
        // new one below. Previously it was handed out as if it were alive, so the view attached
        // itself to a dead Unity instance and stayed black (or crashed).
        unityPlayer = null;

        if (activity != null) {
            _isUnityCreating = true;

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().setFormat(PixelFormat.RGBA_8888);
                    int flag = activity.getWindow().getAttributes().flags;
                    boolean fullScreen = false;
                    if ((flag & WindowManager.LayoutParams.FLAG_FULLSCREEN) == WindowManager.LayoutParams.FLAG_FULLSCREEN) {
                        fullScreen = true;
                    }

                    try {
                        // Wrap the caller's callback so the shared state is reset *before* the
                        // consumer sees onUnload/onQuit — otherwise `_isUnityReady` stays true
                        // for a runtime that no longer exists.
                        unityPlayer = new UPlayer(activity, wrapCallback(callback));
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        // Previously swallowed silently, which left unityPlayer == null and
                        // caused an unrelated NPE below (windowFocusChanged) with no trace of
                        // the real cause. Log and abort initialization instead.
                        Log.e(TAG, "Failed to create Unity player", e);
                        _isUnityCreating = false;
                        return;
                    }

                    try {
                        // wait a moment. fix unity cannot start when startup.
                        Thread.sleep(1000);
                    } catch (Exception e) {}

                    // start unity
                    try {
                        addUnityViewToBackground();
                    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {}

                    unityPlayer.windowFocusChanged(true);

                    try {
                        unityPlayer.requestFocusPlayer();
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {}

                    unityPlayer.resume();

                    // `fullScreen` reflects the current window flags; `_fullScreen` is the
                    // component prop (defaults true). Honor the prop additively so passing
                    // fullScreen={false} actually forces non-fullscreen, while the default
                    // keeps the previous behavior untouched.
                    if (!fullScreen || !_fullScreen) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    }

                    _isUnityReady = true;
                    _isUnityPaused = false;
                    _isUnityCreating = false;

                    try {
                        callback.onReady();
                    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {}
                }
            });
        }
    }

    // pause/resume guard on `_isUnityReady`, not just on the reference: between unload() and
    // onUnityPlayerUnloaded the field still points at a runtime that is shutting down.
    public static void pause() {
        if (unityPlayer != null && _isUnityReady) {
            unityPlayer.pause();
            _isUnityPaused = true;
        }
    }

    public static void resume() {
        if (unityPlayer != null && _isUnityReady) {
            unityPlayer.resume();
            _isUnityPaused = false;
        }
    }

    public static void unload() {
        if (unityPlayer == null || _isUnityUnloading) {
            return;
        }

        // Mark the runtime as gone right away. `unload()` is asynchronous — Unity reports back
        // through onUnityPlayerUnloaded much later — and anything sent to it in between
        // (pause/resume/windowFocusChanged/UnitySendMessage) hits a runtime that is tearing down.
        _isUnityUnloading = true;
        _isUnityReady = false;
        _isUnityPaused = false;

        final UPlayer player = unityPlayer;
        player.unload();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (_isUnityUnloading && unityPlayer == player) {
                    Log.w(TAG, "Unity did not report onUnityPlayerUnloaded within "
                        + UNLOAD_TIMEOUT_MS + "ms — releasing the player anyway");
                    onPlayerGone(false);
                }
            }
        }, UNLOAD_TIMEOUT_MS);
    }

    public static void destroy() {
        if (unityPlayer == null) {
            return;
        }

        unityPlayer.destroy();
        // A destroyed UnityPlayer cannot be revived inside this process, so treat it like a quit.
        onPlayerGone(true);
    }

    /**
     * The Unity runtime is gone (unloaded, quit or destroyed): reset the shared state so nothing
     * hands out a dead player, and honor a mount that happened while the unload was in flight.
     */
    private static void onPlayerGone(boolean quit) {
        _isUnityReady = false;
        _isUnityPaused = false;
        _isUnityUnloading = false;
        _isUnityCreating = false;
        unityPlayer = null;

        final Activity activity = _pendingActivity;
        final UnityPlayerCallback callback = _pendingCallback;
        _pendingActivity = null;
        _pendingCallback = null;

        if (quit) {
            _isUnityQuit = true;
            return;
        }

        if (activity != null && callback != null) {
            // The Unity screen was reopened while the old runtime was still unloading.
            try {
                createPlayer(activity, callback);
            } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                Log.e(TAG, "Failed to recreate the Unity player after unload", e);
            }
        }
    }

    private static UnityPlayerCallback wrapCallback(final UnityPlayerCallback callback) {
        return new UnityPlayerCallback() {
            @Override
            public void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
                callback.onReady();
            }

            @Override
            public void onUnload() {
                onPlayerGone(false);
                callback.onUnload();
            }

            @Override
            public void onQuit() {
                onPlayerGone(true);
                callback.onQuit();
            }
        };
    }

    public static void addUnityViewToBackground() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if (unityPlayer == null) {
            return;
        }

        FrameLayout frame = unityPlayer.requestFrame();
        if (frame == null) {
            Log.e(TAG, "addUnityViewToBackground: Unity frame unavailable (incompatible Unity version?)");
            return;
        }

        if (unityPlayer.getParentPlayer() != null) {
            // NOTE: If we're being detached as part of the transition, make sure
            // to explicitly finish the transition first, as it might still keep
            // the view's parent around despite calling `removeView()` here. This
            // prevents a crash on an `addContentView()` later on.
            // Otherwise, if there's no transition, it's a no-op.
            // See https://stackoverflow.com/a/58247331
            ((ViewGroup) unityPlayer.getParentPlayer()).endViewTransition(frame);
            ((ViewGroup) unityPlayer.getParentPlayer()).removeView(frame);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            unityPlayer.setZ(-1f);
        }

        final Activity activity = ((Activity) unityPlayer.getContextPlayer());
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(1, 1);
        activity.addContentView(frame, layoutParams);
    }

    public static void addUnityViewToGroup(ViewGroup group) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (unityPlayer == null) {
            return;
        }

        FrameLayout frame = unityPlayer.requestFrame();
        if (frame == null) {
            Log.e(TAG, "addUnityViewToGroup: Unity frame unavailable (incompatible Unity version?)");
            return;
        }

        if (unityPlayer.getParentPlayer() != null) {
            ((ViewGroup) unityPlayer.getParentPlayer()).removeView(frame);
        }

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        group.addView(frame, 0, layoutParams);
        unityPlayer.windowFocusChanged(true);
        unityPlayer.requestFocusPlayer();
        unityPlayer.resume();
    }

    public interface UnityPlayerCallback {
        void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

        void onUnload();

        void onQuit();
    }
}
