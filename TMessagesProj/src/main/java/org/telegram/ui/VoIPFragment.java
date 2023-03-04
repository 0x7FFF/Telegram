package org.telegram.ui;

import static org.telegram.ui.GroupCallActivity.TRANSITION_DURATION;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.voip.AcceptDeclineView;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPOverlayBackground;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton;
import org.telegram.ui.Components.voip.VoIPWindowView;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

public class VoIPFragment implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final int currentAccount;

    Activity activity;

    TLRPC.User currentUser;
    TLRPC.User callingUser;

    VoIPToggleButton[] bottomButtons = new VoIPToggleButton[4];

    private ViewGroup fragmentView;
    private VoIPOverlayBackground overlayBackground;

    private BackupImageView callingUserPhotoView;
    private BackupImageView callingUserPhotoViewMini;

    private TextView callingUserTitle;

    private VoIPStatusTextView statusTextView;
    private ImageView backIcon;
    private ImageView speakerPhoneIcon;

    LinearLayout emojiLayout;
    TextView emojiRationalTextView;
    ImageView[] emojiViews = new ImageView[4];
    private View avatarWavesView;
    private FrameLayout avatarAndWavesWrap;
    private AvatarWavesDrawable avatarWaves;
    private LinearLayout emojiOverlay, emojiAnimatedLayout;
    private RLottieImageView[] animatedEmojiViews = new RLottieImageView[4];
    private Button hideEmojiBtn;
    private TextView emojiTitleView;
    private Animator emojiExpandAnimation;
    private TextView emojiTooltip;

    private LinearLayout emojiTooltipContainer;
    private TriangleView emojiTooltipTriangle;
    private VoIPToggleButton acceptButton;
    private View acceptWaves;
    private boolean ratingMode;
    private int ratingStarsValue = -1;
    Emoji.EmojiDrawable[] emojiDrawables = new Emoji.EmojiDrawable[4];
    LinearLayout statusLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private VoIPFloatingLayout callingUserMiniFloatingLayout;
    private boolean currentUserCameraIsFullscreen;

    private TextureViewRenderer callingUserMiniTextureRenderer;
    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;

    private AcceptDeclineView acceptDeclineView;

    View bottomShadow;
    View topShadow;

    private VoIPButtonsLayout buttonsLayout;
    Paint overlayPaint = new Paint();
    Paint overlayBottomPaint = new Paint();

    boolean isOutgoing;
    boolean callingUserIsVideo;
    boolean currentUserIsVideo;

    private PrivateVideoPreviewDialog previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;

    float touchSlop;

    private static VoIPFragment instance;
    private VoIPWindowView windowView;
    private int statusLayoutAnimateToOffset;

    private AccessibilityManager accessibilityManager;

    private boolean uiVisible = true;
    float uiVisibilityAlpha = 1f;
    private boolean canHideUI;
    private Animator cameraShowingAnimator;
    private boolean emojiLoaded;
    private boolean emojiExpanded;

    private boolean canSwitchToPip;
    private boolean switchingToPip;

    private float enterTransitionProgress;
    private boolean isFinished;
    boolean cameraForceExpanded;
    boolean enterFromPiP;
    private boolean deviceIsLocked;

    long lastContentTapTime;
    int animationIndex = -1;
    VoIPNotificationsLayout notificationsLayout;

    HintView tapToVideoTooltip;

    ValueAnimator uiVisibilityAnimator;
    ValueAnimator.AnimatorUpdateListener statusbarAnimatorListener = valueAnimator -> {
        uiVisibilityAlpha = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    float fillNaviagtionBarValue;
    boolean fillNaviagtionBar;
    ValueAnimator naviagtionBarAnimator;
    ValueAnimator.AnimatorUpdateListener navigationBarAnimationListener = valueAnimator -> {
        fillNaviagtionBarValue = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    boolean hideUiRunnableWaiting;
    Runnable hideUIRunnable = () -> {
        hideUiRunnableWaiting = false;
        if (canHideUI && uiVisible && !emojiExpanded) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }
    };
    private boolean lockOnScreen;
    private boolean screenWasWakeup;
    private boolean isVideoCall;

    /* === pinch to zoom === */
    private float pinchStartCenterX;
    private float pinchStartCenterY;
    private float pinchStartDistance;
    private float pinchTranslationX;
    private float pinchTranslationY;
    private boolean isInPinchToZoomTouchMode;

    private float pinchCenterX;
    private float pinchCenterY;

    private int pointerId1, pointerId2;

    float pinchScale = 1f;
    private boolean zoomStarted;
    private boolean canZoomGesture;
    ValueAnimator zoomBackAnimator;
    /* === pinch to zoom === */
    private RLottieDrawable lottieBtToSpeaker, lottieCallAccept, lottieCallDecline, lottieCallMute, lottieCallUnmute, lottieCameraFlip, lottieSpeakerToBt, lottieStar, lottieVideoStart, lottieVideoStop;
    private final VoIPColorScheme voipColorScheme = VoIPColorScheme.getInstance();
    private BackgroundGLView backgroundGLView;

    private int counterForeground = 0;

    private final int durationMs = 5000;

    private int numberOfColorSchemesToRotate = 2;

    private final Runnable backgroundSwitchRunnable = new Runnable() {
        @Override
        public void run() {
            switch (counterForeground) {
                case 0: {
                    Vector4[] newColors = voipColorScheme.getInstance().getBlueViolet();
                    backgroundGLView.interpolateColors(newColors, durationMs);
                    break;
                }
                case 1: {
                    Vector4[] newColors = voipColorScheme.getInstance().getBlueGreen();
                    backgroundGLView.interpolateColors(newColors, durationMs);
                    break;
                }
                case 2: {
                    Vector4[] newColors = voipColorScheme.getInstance().getGreen();
                    backgroundGLView.interpolateColors(newColors, durationMs);
                    break;
                }
            }
            counterForeground = (counterForeground + 1) % numberOfColorSchemesToRotate;

            // Schedule the next call in 7 seconds
            AndroidUtilities.runOnUIThread(this, durationMs);
        }
    };

    private boolean wasWeakNetwork = false;

    public void onWeakNetwork() {
        if (VoIPService.getSharedInstance().overdrawState == 2) {
            AndroidUtilities.cancelRunOnUIThread(backgroundSwitchRunnable);
            Vector4[] newColors = voipColorScheme.getInstance().getOrangeRed();
            backgroundGLView.interpolateColors(newColors, durationMs);
            wasWeakNetwork = true;
        }
    }

    public void onNormalNetwork() {
        if (wasWeakNetwork && VoIPService.getSharedInstance().overdrawState == 2) {
            AndroidUtilities.runOnUIThread(backgroundSwitchRunnable, 1000);
            wasWeakNetwork = false;
        }
    }

    public static void show(Activity activity, int account) {
        show(activity, false, account);
    }

    public static void show(Activity activity, boolean overlay, int account) {
        if (instance != null && instance.windowView.getParent() == null) {
            if (instance != null) {
                instance.callingUserTextureView.renderer.release();
                instance.currentUserTextureView.renderer.release();
                instance.callingUserMiniTextureRenderer.release();
                instance.destroy();
            }
            instance = null;
        }
        if (instance != null || activity.isFinishing()) {
            return;
        }
        boolean transitionFromPip = VoIPPiPView.getInstance() != null;
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().getUser() == null) {
            return;
        }
        VoIPFragment fragment = new VoIPFragment(account);
        fragment.activity = activity;
        instance = fragment;
        VoIPWindowView windowView = new VoIPWindowView(activity, !transitionFromPip) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (fragment.isFinished || fragment.switchingToPip) {
                    return false;
                }
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && !fragment.lockOnScreen) {
                    fragment.onBackPressed();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (fragment.currentState == VoIPService.STATE_WAITING_INCOMING) {
                        final VoIPService service = VoIPService.getSharedInstance();
                        if (service != null) {
                            service.stopRinging();
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        instance.deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }
        instance.screenWasWakeup = !screenOn;
        windowView.setLockOnScreen(instance.deviceIsLocked);
        fragment.windowView = windowView;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            windowView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    fragment.setInsets(windowInsets);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            });
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = windowView.createWindowLayoutParams();
        if (overlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        }
        wm.addView(windowView, layoutParams);
        View view = fragment.createView(activity);
        windowView.addView(view);

        if (transitionFromPip) {
            fragment.enterTransitionProgress = 0f;
            fragment.startTransitionFromPiP();
        } else {
            fragment.enterTransitionProgress = 1f;
            fragment.updateSystemBarColors();
        }
    }

    private void onBackPressed() {
        if (isFinished || switchingToPip) {
            return;
        }
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
            cameraForceExpanded = false;
            currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
            currentUserCameraIsFullscreen = false;
            previousState = currentState;
            updateViewState();
            return;
        }
        if (emojiExpanded) {
            expandEmoji(false);
        } else {
            if (emojiOverlay.getVisibility() != View.GONE) {
                return;
            }
            if (canSwitchToPip && !lockOnScreen) {
                if (AndroidUtilities.checkInlinePermissions(activity)) {
                    switchToPip();
                } else {
                    requestInlinePermissions();
                }
            } else {
                windowView.finish();
            }
        }
    }

    public static void clearInstance() {
        if (instance != null) {
            if (VoIPService.getSharedInstance() != null) {
                int h = instance.windowView.getMeasuredHeight();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                    h -= instance.lastInsets.getSystemWindowInsetBottom();
                }
                if (instance.canSwitchToPip) {
                    VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                        VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                        VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
                    }
                }
            }
            instance.callingUserTextureView.renderer.release();
            instance.currentUserTextureView.renderer.release();
            instance.callingUserMiniTextureRenderer.release();
            instance.destroy();
        }
        instance = null;
    }

    public static VoIPFragment getInstance() {
        return instance;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsets(WindowInsets windowInsets) {
        lastInsets = windowInsets;
        /*
        ((FrameLayout.LayoutParams) buttonsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) acceptDeclineView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) backIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) speakerPhoneIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) topShadow.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(68) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) emojiLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(17) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserPhotoViewMini.getLayoutParams()).topMargin = AndroidUtilities.dp(68) + lastInsets.getSystemWindowInsetTop();

        ((FrameLayout.LayoutParams) currentUserCameraFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserTextureView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();

        ((FrameLayout.LayoutParams) bottomShadow.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
         */

        fragmentView.setPadding(lastInsets.getSystemWindowInsetLeft(), lastInsets.getSystemWindowInsetTop(), lastInsets.getSystemWindowInsetRight(), lastInsets.getSystemWindowInsetBottom());

        setInsetsToLayoutParams(lastInsets, (FrameLayout.LayoutParams) currentUserCameraFloatingLayout.getLayoutParams());
        setInsetsToLayoutParams(lastInsets, (FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams());
        setInsetsToLayoutParams(lastInsets, (FrameLayout.LayoutParams) callingUserTextureView.getLayoutParams());

        currentUserCameraFloatingLayout.setInsets(lastInsets);
        callingUserMiniFloatingLayout.setInsets(lastInsets);
        fragmentView.requestLayout();
        if (previewDialog != null) {
            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
            setInsetsToLayoutParams(lastInsets, (FrameLayout.LayoutParams) previewDialog.getLayoutParams());
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topShadow.getLayoutParams();
        lp.topMargin = -lastInsets.getStableInsetTop();
        lp.height = lastInsets.getStableInsetTop();
        lp = (FrameLayout.LayoutParams) bottomShadow.getLayoutParams();
        lp.bottomMargin = -lastInsets.getStableInsetBottom();
        lp.height = lastInsets.getStableInsetBottom();
        fragmentView.requestLayout();
    }

    private RLottieDrawable loadButtonLottieDrawable(@RawRes int resID) {
        return new RLottieDrawable(resID, resID + "", AndroidUtilities.dp(52), AndroidUtilities.dp(52));
    }

    public VoIPFragment(int account) {
        currentAccount = account;
        currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        callingUser = VoIPService.getSharedInstance().getUser();
        VoIPService.getSharedInstance().registerStateListener(this);
        isOutgoing = VoIPService.getSharedInstance().isOutgoing();
        previousState = -1;
        currentState = VoIPService.getSharedInstance().getCallState();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);

        lottieBtToSpeaker = loadButtonLottieDrawable(R.raw.bt_to_speaker);
        lottieCallAccept = loadButtonLottieDrawable(R.raw.call_accept);
        lottieCallDecline = loadButtonLottieDrawable(R.raw.call_decline);
        lottieCallMute = loadButtonLottieDrawable(R.raw.call_mute);
        lottieCallUnmute = loadButtonLottieDrawable(R.raw.call_unmute);
        lottieCameraFlip = loadButtonLottieDrawable(R.raw.camera_flip);
        lottieSpeakerToBt = loadButtonLottieDrawable(R.raw.speaker_to_bt);
        lottieStar = loadButtonLottieDrawable(R.raw.star);
        lottieVideoStart = loadButtonLottieDrawable(R.raw.video_start);
        lottieVideoStop = loadButtonLottieDrawable(R.raw.video_stop);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
    }

    @Override
    public void onProximitySensorChange(boolean isNear) {
        if (isNear) {
            backgroundGLView.onPause();
        } else {
            backgroundGLView.onResume();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsetsToLayoutParams(WindowInsets insets, FrameLayout.LayoutParams lp) {
        lp.topMargin = -insets.getSystemWindowInsetTop();
        lp.bottomMargin = -insets.getSystemWindowInsetBottom();
        lp.leftMargin = -insets.getSystemWindowInsetLeft();
        lp.rightMargin = -insets.getSystemWindowInsetRight();
    }

    @Override
    public void onStateChanged(int state) {
        if (currentState != state) {
            previousState = currentState;
            currentState = state;
            if (windowView != null) {
                updateViewState();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.voipServiceCreated) {
            if (currentState == VoIPService.STATE_BUSY && VoIPService.getSharedInstance() != null) {
                currentUserTextureView.renderer.release();
                callingUserTextureView.renderer.release();
                callingUserMiniTextureRenderer.release();
                initRenderers();
                VoIPService.getSharedInstance().registerStateListener(this);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            updateKeyView(true);
        } else if (id == NotificationCenter.closeInCallActivity) {
            windowView.finish();
        } else if (id == NotificationCenter.webRtcMicAmplitudeEvent) {
            float level = (Float) args[0];
            avatarWaves.setAmplitude(level * 14);
        }
    }

    @Override
    public void onSignalBarsCountChanged(int count) {
        if (statusTextView != null) {
            statusTextView.setSignalBarCount(count);
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        updateButtons(true);
    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        previousState = currentState;
        if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
            topShadow.setVisibility(View.VISIBLE);
            bottomShadow.setVisibility(View.VISIBLE);
            isVideoCall = true;
        } else if (videoState == Instance.VIDEO_STATE_INACTIVE && isVideoCall) {
            topShadow.setVisibility(View.INVISIBLE);
            bottomShadow.setVisibility(View.INVISIBLE);
            isVideoCall = false;
        }
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        previousState = currentState;
        updateViewState();
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {
        previousState = currentState;
        if (isAvailable && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onScreenOnChange(boolean screenOn) {

    }

    public View createView(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        accessibilityManager = ContextCompat.getSystemService(context, AccessibilityManager.class);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), lastInsets.getSystemWindowInsetTop(), overlayPaint);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, getMeasuredHeight() - lastInsets.getSystemWindowInsetBottom(), getMeasuredWidth(), getMeasuredHeight(), overlayBottomPaint);
                }
            }

            float pressedX;
            float pressedY;
            boolean check;
            long pressedTime;

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                /* === pinch to zoom === */
                if (!canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    finishZoom();
                    return false;
                }
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    canZoomGesture = false;
                    isInPinchToZoomTouchMode = false;
                    zoomStarted = false;
                }
                VoIPTextureView currentTextureView = getFullscreenTextureView();

                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        AndroidUtilities.rectTmp.set(currentTextureView.getX(), currentTextureView.getY(), currentTextureView.getX() + currentTextureView.getMeasuredWidth(), currentTextureView.getY() + currentTextureView.getMeasuredHeight());
                        AndroidUtilities.rectTmp.inset((currentTextureView.getMeasuredHeight() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredHeight()) / 2, (currentTextureView.getMeasuredWidth() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredWidth()) / 2);
                        if (!GroupCallActivity.isLandscapeMode) {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.bottom = Math.min(AndroidUtilities.rectTmp.bottom, currentTextureView.getMeasuredHeight() - AndroidUtilities.dp(90));
                        } else {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.right = Math.min(AndroidUtilities.rectTmp.right, currentTextureView.getMeasuredWidth() - AndroidUtilities.dp(90));
                        }
                        canZoomGesture = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                        if (!canZoomGesture) {
                            finishZoom();
                        }
                    }
                    if (canZoomGesture && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                        pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                        pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                        pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                        pinchScale = 1f;

                        pointerId1 = ev.getPointerId(0);
                        pointerId2 = ev.getPointerId(1);
                        isInPinchToZoomTouchMode = true;
                    }
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
                    int index1 = -1;
                    int index2 = -1;
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        if (pointerId1 == ev.getPointerId(i)) {
                            index1 = i;
                        }
                        if (pointerId2 == ev.getPointerId(i)) {
                            index2 = i;
                        }
                    }
                    if (index1 == -1 || index2 == -1) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                        finishZoom();
                    } else {
                        pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
                        if (pinchScale > 1.005f && !zoomStarted) {
                            pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                            pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                            pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                            pinchScale = 1f;
                            pinchTranslationX = 0f;
                            pinchTranslationY = 0f;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            zoomStarted = true;
                            isInPinchToZoomTouchMode = true;
                        }

                        float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                        float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

                        float moveDx = pinchStartCenterX - newPinchCenterX;
                        float moveDy = pinchStartCenterY - newPinchCenterY;
                        pinchTranslationX = -moveDx / pinchScale;
                        pinchTranslationY = -moveDy / pinchScale;
                        invalidate();
                    }
                } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    finishZoom();
                }
                fragmentView.invalidate();

                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressedX = ev.getX();
                        pressedY = ev.getY();
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            float dx = ev.getX() - pressedX;
                            float dy = ev.getY() - pressedY;
                            long currentTime = System.currentTimeMillis();
                            if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (emojiExpanded) {
                                    expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return canZoomGesture || check;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == callingUserPhotoView && isAnyVideoActive()) {
                    return false;
                }
                if (
                    child == callingUserPhotoView ||
                        child == callingUserTextureView ||
                        (child == currentUserCameraFloatingLayout && currentUserCameraIsFullscreen)
                ) {
                    if (zoomStarted || zoomBackAnimator != null) {
                        canvas.save();
                        canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY);
                        canvas.translate(pinchTranslationX, pinchTranslationY);
                        boolean b = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return b;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        frameLayout.setBackgroundColor(0xff000000);
        updateSystemBarColors();
        fragmentView = frameLayout;
        frameLayout.setFitsSystemWindows(true);
        callingUserPhotoView = new BackupImageView(context);
        callingUserPhotoView.setRoundRadius(AndroidUtilities.dp(66));

        int maxRadius = 100;
        avatarWavesView = new View(context);
        avatarWaves = new AvatarWavesDrawable(AndroidUtilities.dp(70), AndroidUtilities.dp(80), AndroidUtilities.dp(80), AndroidUtilities.dp(90));
        avatarWavesView.setBackground(avatarWaves);
        avatarWaves.setShowWaves(true);
        avatarWaves.setAmplitude(0);
        avatarAndWavesWrap = new FrameLayout(context);
        avatarAndWavesWrap.addView(avatarWavesView);
        avatarAndWavesWrap.addView(callingUserPhotoView, LayoutHelper.createFrame(132, 132, Gravity.CENTER));
        avatarAndWavesWrap.setPivotX(AndroidUtilities.dp(maxRadius));
        avatarAndWavesWrap.setPivotY(AndroidUtilities.dp(maxRadius + 66));

        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.renderer.setRotateTextureWithScreen(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;


        final GradientCircleOverdrawRenderer gradientCircleOverdrawRenderer = new GradientCircleOverdrawRenderer(
            voipColorScheme.vBlueVioletTL.asArray(),
            voipColorScheme.vBlueVioletTR.asArray(),
            voipColorScheme.vBlueVioletBL.asArray(),
            voipColorScheme.vBlueVioletBR.asArray(),
            voipColorScheme.vBlueGreenBL.asArray(),
            voipColorScheme.vBlueGreenTR.asArray(),
            voipColorScheme.vBlueGreenBL.asArray(),
            voipColorScheme.vBlueGreenBR.asArray()
        );

        AndroidUtilities.runOnUIThread(backgroundSwitchRunnable);
        backgroundGLView = new BackgroundGLView(context, gradientCircleOverdrawRenderer);
        frameLayout.addView(backgroundGLView);
        FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(maxRadius * 2, maxRadius * 2, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 116 - (maxRadius - 132 / 2), 0, 0);
        frameLayout.addView(avatarAndWavesWrap, layoutParams);
        frameLayout.addView(callingUserTextureView);

        AvatarDrawable avatarDrawable = new AvatarDrawable(callingUser);
        int color1 = avatarDrawable.getColor();
        int color2 = avatarDrawable.getColor2();

        BackgroundGradientDrawable callingUserGradientThumb = new BackgroundGradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[]{color1, color2}
        );

        overlayBackground = new VoIPOverlayBackground(context);
        overlayBackground.setVisibility(View.GONE);

        callingUserPhotoView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            ImageReceiver.BitmapHolder bmp = imageReceiver.getBitmapSafe();
            if (bmp != null) {
                overlayBackground.setBackground(bmp);
            }
        });
        callingUserPhotoView.setImage(
            ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_BIG),
            null,
            Theme.createCircularDrawable(AndroidUtilities.dp(135), callingUserGradientThumb),
            callingUser
        );
        callingUserPhotoView.setRoundRadius(AndroidUtilities.dp(135));

        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserCameraIsFullscreen = true;
        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
                currentUserCameraIsFullscreen = true;
                cameraForceExpanded = true;
                previousState = currentState;
                updateViewState();
            }
        });
        currentUserTextureView.renderer.setMirror(true);
        currentUserCameraFloatingLayout.addView(currentUserTextureView);

        callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
        callingUserMiniFloatingLayout.alwaysFloating = true;
        callingUserMiniFloatingLayout.setFloatingMode(true, false);
        callingUserMiniTextureRenderer = new TextureViewRenderer(context);
        callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
        callingUserMiniTextureRenderer.setIsCamera(false);
        callingUserMiniTextureRenderer.setFpsReduction(30);
        callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        View backgroundView = new View(context);
        backgroundView.setBackgroundColor(0xff1b1f23);
        callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
                if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                    AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                    hideUiRunnableWaiting = false;
                    lastContentTapTime = System.currentTimeMillis();
                    currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                    currentUserCameraIsFullscreen = false;
                    cameraForceExpanded = false;
                    previousState = currentState;
                    updateViewState();
                }
            }
        );
        callingUserMiniFloatingLayout.setVisibility(View.GONE);

        frameLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(callingUserMiniFloatingLayout);
        frameLayout.addView(overlayBackground);

        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (100 / 255)), Color.TRANSPARENT}));
        bottomShadow.setVisibility(View.INVISIBLE);
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (100 / 255)), Color.TRANSPARENT}));
        topShadow.setVisibility(View.INVISIBLE);
        frameLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP));

        emojiLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setVisibleToUser(emojiLoaded);
            }
        };
        emojiLayout.setOrientation(LinearLayout.HORIZONTAL);
        emojiLayout.setPadding(0, 0, 0, AndroidUtilities.dp(30));
        emojiLayout.setClipToPadding(false);

        emojiLayout.setOnClickListener(view -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            lastContentTapTime = System.currentTimeMillis();
            if (emojiLoaded) {
                expandEmoji(!emojiExpanded);
            }
        });

        emojiRationalTextView = new TextView(context);
        emojiRationalTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser)));
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);

        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new ImageView(context);
            emojiViews[i].setScaleType(ImageView.ScaleType.FIT_XY);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(22, 22, i == 0 ? 0 : 4, 0, 0, 0));
        }

        statusLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                final VoIPService service = VoIPService.getSharedInstance();
                final CharSequence callingUserTitleText = callingUserTitle.getText();
                if (service != null && !TextUtils.isEmpty(callingUserTitleText)) {
                    final StringBuilder builder = new StringBuilder(callingUserTitleText);

                    builder.append(", ");
                    if (service.privateCall != null && service.privateCall.video) {
                        builder.append(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding));
                    } else {
                        builder.append(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
                    }

                    final long callDuration = service.getCallDuration();
                    if (callDuration > 0) {
                        builder.append(", ");
                        builder.append(LocaleController.formatDuration((int) (callDuration / 1000)));
                    }

                    info.setText(builder);
                }
            }
        };
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.setFocusable(true);
        statusLayout.setFocusableInTouchMode(true);

        callingUserPhotoViewMini = new BackupImageView(context);
        callingUserPhotoViewMini.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(135), 0xFF000000), callingUser);
        callingUserPhotoViewMini.setRoundRadius(AndroidUtilities.dp(135) / 2);
        callingUserPhotoViewMini.setVisibility(View.GONE);

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        CharSequence name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
        name = Emoji.replaceEmoji(name, callingUserTitle.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
        callingUserTitle.setText(name);
        callingUserTitle.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusTextView = new VoIPStatusTextView(context);
        ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusLayout.setClipChildren(false);
        statusLayout.setClipToPadding(false);
        statusLayout.setPadding(0, 0, 0, AndroidUtilities.dp(15));

        frameLayout.addView(callingUserPhotoViewMini, LayoutHelper.createFrame(135, 135, Gravity.CENTER_HORIZONTAL, 0, 68, 0, 0));

        FrameLayout.LayoutParams statusLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 273, 0, 0);
        frameLayout.addView(
            statusLayout,
            statusLayoutParams
        );
        emojiAnimatedLayout = new LinearLayout(context);
        emojiAnimatedLayout.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            RLottieImageView rLottieImageView = new RLottieImageView(context);
            rLottieImageView.setAutoRepeat(true);
            animatedEmojiViews[i] = rLottieImageView;
            emojiAnimatedLayout.addView(rLottieImageView, LayoutHelper.createLinear(42, 42, 6, 0, 6, 0));
        }

        emojiTitleView = new TextView(context);
        emojiTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emojiTitleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emojiTitleView.setTextColor(0xFFFFFFFF);
        emojiTitleView.setGravity(Gravity.CENTER);
//        emojiTitleView.setText(LocaleController.getString("VoipEncryptionKeyTitle", R.string.VoipEncryptionKeyTitle));

        emojiOverlay = new LinearLayout(context);
        emojiOverlay.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        emojiOverlay.setBackground(
            new RoundedBackgroundDrawable(Color.BLACK, 20, 90)
        );
        emojiOverlay.setOrientation(LinearLayout.VERTICAL);
        emojiOverlay.addView(emojiAnimatedLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        emojiOverlay.addView(emojiTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        emojiOverlay.addView(emojiRationalTextView);
        emojiOverlay.setVisibility(View.GONE);

        frameLayout.addView(emojiOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 112, 32, 0));
        frameLayout.addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 17, 0, 0));
//        frameLayout.addView(emojiRationalTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 32, 24, 0));

        hideEmojiBtn = new Button(context);
        hideEmojiBtn.setText(LocaleController.getString("VoipHideEmoji", R.string.VoipHideEmoji));
        hideEmojiBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hideEmojiBtn.setAllCaps(false);
        hideEmojiBtn.setTextColor(0xFFFFFFFF);
        hideEmojiBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        hideEmojiBtn.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        hideEmojiBtn.setBackground(
            new RoundedBackgroundDrawable(Color.BLACK, 12, 90)
        );
        hideEmojiBtn.setOnClickListener(v -> expandEmoji(false));
        hideEmojiBtn.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hideEmojiBtn.setStateListAnimator(null);
        }
        frameLayout.addView(hideEmojiBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));

        if (needShowEmojiTooltip()) {
            emojiTooltipContainer = new LinearLayout(context);
            emojiTooltipContainer.setOrientation(LinearLayout.VERTICAL);

            emojiTooltip = new TextView(context);
            emojiTooltip.setText(LocaleController.getString("VoipEncryptionKeyTooltip", R.string.VoipEncryptionKeyTooltip));
            emojiTooltip.setTextColor(0xFFFFFFFF);
            emojiTooltip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            emojiTooltip.setGravity(Gravity.CENTER);
            emojiTooltip.setBackground(
                new RoundedBackgroundDrawable(Color.BLACK, 5, 90)
            );
            emojiTooltip.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));

            emojiTooltipTriangle = new TriangleView(
                context
            );
            emojiTooltipTriangle.setSize(25);
            emojiTooltipTriangle.setColor(0x5A000000);
            emojiTooltipTriangle.setOrientation(TriangleView.Orientation.UP);

//            emojiTooltip.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), 0);
//            emojiTooltip.setLayerType(View.LAYER_TYPE_HARDWARE, null); // required for background masking
//            emojiTooltip.setVisibility(View.GONE);
//            frameLayout.addView(emojiTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 44, 0, 0));
            emojiTooltipContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null); // required for background masking
            emojiTooltipContainer.addView(emojiTooltipTriangle, LayoutHelper.createLinear(25, 25, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, -8));
            emojiTooltipContainer.addView(emojiTooltip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, Gravity.TOP, 0, 0, 0, 6));
//            emojiTooltipContainer.setVisibility(View.GONE);
            frameLayout.addView(emojiTooltipContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 44, 0, 0));
        }

        buttonsLayout = new VoIPButtonsLayout(context);
        boolean test = true;
        for (int i = 0; i < 4; i++) {
            bottomButtons[i] = new VoIPToggleButton(context);
            buttonsLayout.addView(bottomButtons[i]);
        }

        acceptButton = new VoIPToggleButton(context);
        acceptButton.setVisibility(View.GONE);
        acceptButton.setOnClickListener(v -> {
            if (currentState != VoIPService.STATE_WAITING_INCOMING)
                return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            } else {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().acceptIncomingCall();
                    if (currentUserIsVideo) {
                        VoIPService.getSharedInstance().requestVideoCall(false);
                    }
                }
            }
        });
        acceptWaves = new View(context);
        AvatarWavesDrawable acceptWavesDrawable = new AvatarWavesDrawable(AndroidUtilities.dp(26), AndroidUtilities.dp(36), AndroidUtilities.dp(30), AndroidUtilities.dp(40));
        acceptWavesDrawable.setAmplitude(0);
        acceptWavesDrawable.setShowWaves(true);
        acceptWaves.setBackground(acceptWavesDrawable);
        acceptWaves.setVisibility(View.GONE);

        acceptDeclineView = new AcceptDeclineView(context);
        acceptDeclineView.setListener(new AcceptDeclineView.Listener() {
            @Override
            public void onAccept() {
                if (currentState == VoIPService.STATE_BUSY) {
                    Intent intent = new Intent(activity, VoIPService.class);
                    intent.putExtra("user_id", callingUser.id);
                    intent.putExtra("is_outgoing", true);
                    intent.putExtra("start_incall_activity", false);
                    intent.putExtra("video_call", isVideoCall);
                    intent.putExtra("can_video_call", isVideoCall);
                    intent.putExtra("account", currentAccount);
                    try {
                        activity.startService(intent);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                    } else {
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().acceptIncomingCall();
                            if (currentUserIsVideo) {
                                VoIPService.getSharedInstance().requestVideoCall(false);
                            }
                        }
                    }
                    animateAvatar();
                }
            }

            @Override
            public void onDecline() {
                if (currentState == VoIPService.STATE_BUSY) {
                    windowView.finish();
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().declineIncomingCall();
                    }
                }
            }
        });
        acceptDeclineView.setScreenWasWakeup(screenWasWakeup);

        frameLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        frameLayout.addView(acceptDeclineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.BOTTOM));
        frameLayout.addView(acceptWaves, LayoutHelper.createFrame(112, 112, Gravity.LEFT | Gravity.BOTTOM, 22, 0, 0, 44));
        frameLayout.addView(acceptButton, LayoutHelper.createFrame(68, 80, Gravity.LEFT | Gravity.BOTTOM, 42, 0, 0, 46));

        backIcon = new ImageView(context);
        backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        backIcon.setImageResource(R.drawable.msg_calls_minimize);
        backIcon.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        backIcon.setContentDescription(LocaleController.getString("Back", R.string.Back));
        frameLayout.addView(backIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));

        speakerPhoneIcon = new ImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName(ToggleButton.class.getName());
                info.setCheckable(true);
                VoIPService service = VoIPService.getSharedInstance();
                if (service != null) {
                    info.setChecked(service.isSpeakerphoneOn());
                }
            }
        };
        speakerPhoneIcon.setContentDescription(LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker));
        speakerPhoneIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        speakerPhoneIcon.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        frameLayout.addView(speakerPhoneIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        speakerPhoneIcon.setOnClickListener(view -> {
            if (speakerPhoneIcon.getTag() == null) {
                return;
            }
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });

        backIcon.setOnClickListener(view -> {
            if (!lockOnScreen) {
                onBackPressed();
            }
        });
        if (windowView.isLockOnScreen()) {
            backIcon.setVisibility(View.GONE);
        }

        notificationsLayout = new VoIPNotificationsLayout(context);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        tapToVideoTooltip = new HintView(context, 4);
        tapToVideoTooltip.setText(LocaleController.getString("TapToTurnCamera", R.string.TapToTurnCamera));
        frameLayout.addView(tapToVideoTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        tapToVideoTooltip.setBottomOffset(AndroidUtilities.dp(4));
        tapToVideoTooltip.setVisibility(View.GONE);

        updateViewState();

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (!isVideoCall) {
                isVideoCall = service.privateCall != null && service.privateCall.video;
            }
            initRenderers();
        }

        return frameLayout;
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    private VoIPTextureView getFullscreenTextureView() {
        if (callingUserIsVideo) {
            return callingUserTextureView;
        }
        return currentUserTextureView;
    }

    private boolean needShowEmojiTooltip() {
        return !MessagesController.getGlobalMainSettings().getBoolean("voipEmojiTooltipShown", false);
    }

    public void showRatingScreen(TLRPC.PhoneCall call) {
        ratingMode = true;

        if (emojiExpanded) {
            expandEmoji(false);
        }

        callingUserTitle.setText(LocaleController.getString("VoipCallEnded", R.string.VoipCallEnded));
        statusTextView.setSignalBarCount(-1);

        LinearLayout ratingBox = new LinearLayout(activity);
        Drawable bg;
        if (isAnyVideoActive()) {
            bg = Theme.createRoundRectDrawable(AndroidUtilities.dp(20), 0x64000000);
        } else {
            bg = new RoundedBackgroundDrawable(Color.BLACK, 20, 90);
        }
        ratingBox.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        ratingBox.setBackground(bg);
        ratingBox.setOrientation(LinearLayout.VERTICAL);

        TextView ratingTitle = new TextView(activity);
        ratingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        ratingTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        ratingTitle.setTextColor(0xffffffff);
        ratingTitle.setGravity(Gravity.CENTER);
        ratingTitle.setText(LocaleController.getString("VoipRateTitle", R.string.VoipRateTitle));
        ratingBox.addView(ratingTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView ratingText = new TextView(activity);
        ratingText.setText(LocaleController.getString("VoipRateCallAlert", R.string.VoipRateCallAlert));
        ratingText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        ratingText.setTextColor(Color.WHITE);
        ratingText.setGravity(Gravity.CENTER);
        ratingBox.addView(ratingText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout starsLayout = new LinearLayout(activity);
        starsLayout.setOrientation(LinearLayout.HORIZONTAL);
        RLottieImageView[] stars = new RLottieImageView[5];
        final HashSet<Animator> starAnimations = new HashSet<>();
        View.OnClickListener starClickListener = v -> {
            for (Animator a : (HashSet<Animator>) starAnimations.clone()) {
                a.end();
            }
            int idx = (Integer) v.getTag();
            for (int i = 0; i < 5; i++) {
                if (i <= idx) {
                    if (!stars[i].isPlaying())
                        stars[i].playAnimation();

                    ObjectAnimator animIn = ObjectAnimator.ofFloat(stars[i], AndroidUtilities.VIEW_SCALE, 0.8f);
                    animIn.setDuration(167);
                    animIn.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00));
                    ObjectAnimator animOut = ObjectAnimator.ofFloat(stars[i], AndroidUtilities.VIEW_SCALE, 1);
                    animOut.setDuration(167);
                    animOut.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00));
                    AnimatorSet set = new AnimatorSet();
                    set.playSequentially(animIn, animOut);
                    set.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            starAnimations.remove(set);
                        }
                    });
                    starAnimations.add(set);
                    set.start();
                } else {
                    stars[i].stopAnimation();
                    stars[i].getAnimatedDrawable().setCurrentFrame(0, false);
                }
            }
            ratingStarsValue = idx;
            if (idx >= 3) {
                playRatingOverlayAnimation(v);
            }
        };
        for (int i = 0; i < 5; i++) {
            RLottieImageView star = new RLottieImageView(activity);
            star.setAnimation(R.raw.star, 32, 32);
            star.setTag(i);
            star.setOnClickListener(starClickListener);
            star.setBackground(Theme.createSelectorDrawable(0x33FFFFFF));
            starsLayout.addView(star, LayoutHelper.createLinear(32, 32, 3, 0, 3, 0));
            stars[i] = star;
        }
        ratingBox.addView(starsLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL));

        ratingBox.setClipToPadding(false);
        ratingBox.setClipChildren(false);
        fragmentView.addView(ratingBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 32, 321, 32, 0));

        FrameLayout buttonWrap = new FrameLayout(activity);
        final Property<View, Float> cornerRadiusProperty;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            buttonWrap.setBackgroundColor(0xFFFFFFFF);
            cornerRadiusProperty = new Property<View, Float>(Float.class, "cornerRadius") {
                private float radius = AndroidUtilities.dp(26);

                @Override
                public Float get(View object) {
                    return radius;
                }

                @Override
                public void set(View object, Float value) {
                    radius = value;
                    object.invalidateOutline();
                }
            };
            buttonWrap.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusProperty.get(view));
                }
            });
            buttonWrap.setClipToOutline(true);
            buttonWrap.setForeground(new BitmapDrawable(lottieCallDecline.getAnimatedBitmap()) {
                @Override
                public int getIntrinsicWidth() {
                    return AndroidUtilities.dp(52);
                }

                @Override
                public int getIntrinsicHeight() {
                    return AndroidUtilities.dp(52);
                }
            });
            buttonWrap.setForegroundGravity(Gravity.CENTER);
        } else {
            buttonWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), 0xffffffff));
            cornerRadiusProperty = null;
        }

        FrameLayout buttonLayer = new FrameLayout(activity);
        buttonLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        buttonWrap.addView(buttonLayer);
        Button closeButton = new Button(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            closeButton.setStateListAnimator(null);
        }
        closeButton.setAllCaps(false);
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeButton.setTextColor(Color.BLACK);
        closeButton.setBackground(Theme.createSelectorDrawable(0x19000000, Theme.RIPPLE_MASK_ALL));
        closeButton.setText(LocaleController.getString("Close", R.string.Close));
        closeButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        Paint buttonPaint = new Paint();
        buttonPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        closeButton.setLayerType(View.LAYER_TYPE_HARDWARE, buttonPaint);
        buttonLayer.addView(closeButton);
        closeButton.setOnClickListener(v -> submitCallRating(call));

        fragmentView.addView(buttonWrap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM, 22, 0, 22, 32));

        ratingBox.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ratingBox.getViewTreeObserver().removeOnPreDrawListener(this);

                ArrayList<Animator> motionAnims = new ArrayList<>(), alphaAnims = new ArrayList<>(), starsMotionAnims = new ArrayList<>(), starsAlphaAnims = new ArrayList<>();
                AnimatorSet buttonAnims = new AnimatorSet();

                motionAnims.add(ObjectAnimator.ofFloat(avatarWavesView, AndroidUtilities.VIEW_SCALE, .5f));
                alphaAnims.add(ObjectAnimator.ofFloat(avatarWavesView, View.ALPHA, 0f));
                for (ImageView emoji : emojiViews) {
                    motionAnims.add(ObjectAnimator.ofFloat(emoji, AndroidUtilities.VIEW_SCALE, .001f));
                    alphaAnims.add(ObjectAnimator.ofFloat(emoji, View.ALPHA, 0f));
                }
                ratingBox.setPivotX(ratingBox.getWidth() / 2f);
                ratingBox.setPivotY(0f);
                motionAnims.add(ObjectAnimator.ofFloat(ratingBox, AndroidUtilities.VIEW_SCALE, 0.7f, 1f));
                motionAnims.add(ObjectAnimator.ofFloat(ratingBox, View.TRANSLATION_Y, AndroidUtilities.dp(32), 0));
                alphaAnims.add(ObjectAnimator.ofFloat(ratingBox, View.ALPHA, 0f, 1f));

                motionAnims.add(ObjectAnimator.ofFloat(statusLayout, View.TRANSLATION_Y, AndroidUtilities.dp(-25)));
                motionAnims.add(ObjectAnimator.ofFloat(avatarAndWavesWrap, View.TRANSLATION_Y, AndroidUtilities.dp(-25)));

                for (int i = 0; i < 4; i++) {
                    alphaAnims.add(ObjectAnimator.ofFloat(bottomButtons[i], View.ALPHA, 0f));
                    motionAnims.add(ObjectAnimator.ofFloat(bottomButtons[i], AndroidUtilities.VIEW_SCALE, 0.7f));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    int endBtnX = bottomButtons[3].getLeft() + bottomButtons[3].getWidth() / 2 - AndroidUtilities.dp(26) + buttonsLayout.getLeft();
                    buttonLayer.setAlpha(0);
                    ObjectAnimator layerAlpha;
                    buttonAnims.playTogether(
                        ObjectAnimator.ofInt(buttonWrap, "left", endBtnX, buttonWrap.getLeft()).setDuration(550),
                        ObjectAnimator.ofInt(buttonWrap, "top", buttonsLayout.getTop(), buttonWrap.getTop()).setDuration(550),
                        ObjectAnimator.ofInt(buttonWrap, "right", endBtnX + AndroidUtilities.dp(52), buttonWrap.getRight()).setDuration(550),
                        ObjectAnimator.ofInt(buttonWrap, "bottom", buttonsLayout.getTop() + AndroidUtilities.dp(52), buttonWrap.getBottom()).setDuration(550),
                        ObjectAnimator.ofFloat(buttonWrap, cornerRadiusProperty, AndroidUtilities.dp(8)).setDuration(550),
                        ObjectAnimator.ofArgb(buttonWrap, "backgroundColor", 0xFFF01D2C, 0xffffffff).setDuration(550),
                        ObjectAnimator.ofInt(buttonWrap.getForeground(), "alpha", 0).setDuration(275),
                        layerAlpha = ObjectAnimator.ofFloat(buttonLayer, View.ALPHA, 0, 1).setDuration(275)
                    );
                    layerAlpha.setStartDelay(275);
                    buttonAnims.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.11, 1.00));
                }

                for (int i = 0; i < 5; i++) {
                    stars[i].setAlpha(0f);
                    stars[i].setTranslationY(AndroidUtilities.dp(16));
                    stars[i].setScaleX(0.7f);
                    stars[i].setScaleY(0.7f);
                    ObjectAnimator alpha = ObjectAnimator.ofFloat(stars[i], View.ALPHA, 1f);
                    ObjectAnimator translation = ObjectAnimator.ofFloat(stars[i], View.TRANSLATION_Y, 0);
                    ObjectAnimator scale = ObjectAnimator.ofFloat(stars[i], AndroidUtilities.VIEW_SCALE, 1f);
                    long startDelay = i * 17;
                    alpha.setStartDelay(startDelay);
                    translation.setStartDelay(startDelay);
                    scale.setStartDelay(startDelay);
                    starsAlphaAnims.add(alpha);
                    starsMotionAnims.add(translation);
                    starsMotionAnims.add(scale);
                }

                AnimatorSet motion = new AnimatorSet();
                motion.playTogether(motionAnims);
                motion.setDuration(500);
                motion.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.11, 1.00));

                AnimatorSet alpha = new AnimatorSet();
                alpha.playTogether(alphaAnims);
                alpha.setDuration(200);
                alpha.setInterpolator(new LinearInterpolator());

                AnimatorSet starsAlpha = new AnimatorSet();
                starsAlpha.playTogether(starsAlphaAnims);
                starsAlpha.setDuration(133);
                starsAlpha.setInterpolator(new LinearInterpolator());

                AnimatorSet starsMotion = new AnimatorSet();
                starsMotion.playTogether(starsMotionAnims);
                starsMotion.setDuration(250);
                starsMotion.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.11, 1.00));

                AnimatorSet set = new AnimatorSet();
                ArrayList<Animator> allAnims = new ArrayList<>();
                allAnims.add(motion);
                allAnims.add(alpha);
                allAnims.add(starsAlpha);
                allAnims.add(starsMotion);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    allAnims.add(buttonAnims);
                }
                set.playTogether(allAnims);
                set.start();

                return true;
            }
        });
    }

    private void playRatingOverlayAnimation(View star) {
        RLottieImageView img = new RLottieImageView(activity);
        img.setAnimation(R.raw.stars_effect, 120, 120);
        img.playAnimation();
        img.setOnAnimationEndListener(() -> img.post(() -> fragmentView.removeView(img)));
        int[] location = {0, 0};
        star.getLocationInWindow(location);
        int starX = location[0], starY = location[1];
        starX += star.getWidth() / 2;
        starY += star.getHeight() / 2;
        fragmentView.addView(img, LayoutHelper.createFrame(120, 120, Gravity.TOP | Gravity.LEFT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && lastInsets != null) {
            starX -= lastInsets.getSystemWindowInsetLeft();
            starY -= lastInsets.getSystemWindowInsetTop();
        }
        img.setTranslationX(starX - AndroidUtilities.dp(60));
        img.setTranslationY(starY - AndroidUtilities.dp(60));
    }

    private void submitCallRating(TLRPC.PhoneCall call) {
        windowView.finish();
        if (ratingStarsValue == -1) {
            return;
        }
        TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
        req.peer = new TLRPC.TL_inputPhoneCall();
        req.peer.access_hash = call.access_hash;
        req.peer.id = call.id;
        req.rating = ratingStarsValue + 1;
        req.comment = "";
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, err) -> {
            if (response instanceof TLRPC.TL_updates) {
                TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            }
        });
    }

    private void finishZoom() {
        if (zoomStarted) {
            zoomStarted = false;
            zoomBackAnimator = ValueAnimator.ofFloat(1f, 0);

            float fromScale = pinchScale;
            float fromTranslateX = pinchTranslationX;
            float fromTranslateY = pinchTranslationY;
            zoomBackAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                pinchScale = fromScale * v + 1f * (1f - v);
                pinchTranslationX = fromTranslateX * v;
                pinchTranslationY = fromTranslateY * v;
                fragmentView.invalidate();
            });

            zoomBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    zoomBackAnimator = null;
                    pinchScale = 1f;
                    pinchTranslationX = 0;
                    pinchTranslationY = 0;
                    fragmentView.invalidate();
                }
            });
            zoomBackAnimator.setDuration(TRANSITION_DURATION);
            zoomBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            zoomBackAnimator.start();
        }
        canZoomGesture = false;
        isInPinchToZoomTouchMode = false;
    }

    private void initRenderers() {
        currentUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        });
        callingUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        }, EglBase.CONFIG_PLAIN, new GlRectDrawer());

        callingUserMiniTextureRenderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
    }

    public void switchToPip() {
        if (isFinished || !AndroidUtilities.checkInlinePermissions(activity) || instance == null) {
            return;
        }
        isFinished = true;
        if (VoIPService.getSharedInstance() != null) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_TRANSITION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }
        if (VoIPPiPView.getInstance() == null) {
            return;
        }

        speakerPhoneIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        backIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        emojiLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        buttonsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        bottomShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        topShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        callingUserMiniFloatingLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        notificationsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        VoIPPiPView.switchingToPip = true;
        switchingToPip = true;
        Animator animator = createPiPTransition(false);
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                VoIPPiPView.getInstance().windowView.setAlpha(1f);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                    VoIPPiPView.getInstance().onTransitionEnd();
                    currentUserCameraFloatingLayout.setCornerRadius(-1f);
                    callingUserTextureView.renderer.release();
                    currentUserTextureView.renderer.release();
                    callingUserMiniTextureRenderer.release();
                    destroy();
                    windowView.finishImmediate();
                    VoIPPiPView.switchingToPip = false;
                    switchingToPip = false;
                    instance = null;
                }, 200);
            }
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void startTransitionFromPiP() {
        enterFromPiP = true;
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
            callingUserTextureView.setStub(VoIPPiPView.getInstance().callingUserTextureView);
            currentUserTextureView.setStub(VoIPPiPView.getInstance().currentUserTextureView);
        }
        windowView.setAlpha(0f);
        updateViewState();
        switchingToPip = true;
        VoIPPiPView.switchingToPip = true;
        VoIPPiPView.prepareForTransition();
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        AndroidUtilities.runOnUIThread(() -> {
            windowView.setAlpha(1f);
            Animator animator = createPiPTransition(true);

            backIcon.setAlpha(0f);
            emojiLayout.setAlpha(0f);
            statusLayout.setAlpha(0f);
            buttonsLayout.setAlpha(0f);
            bottomShadow.setAlpha(0f);
            topShadow.setAlpha(0f);
            speakerPhoneIcon.setAlpha(0f);
            notificationsLayout.setAlpha(0f);
            callingUserPhotoView.setAlpha(0f);

            currentUserCameraFloatingLayout.switchingToPip = true;
            AndroidUtilities.runOnUIThread(() -> {
                VoIPPiPView.switchingToPip = false;
                VoIPPiPView.finish();

                speakerPhoneIcon.animate().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                backIcon.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                emojiLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                buttonsLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                bottomShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                topShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                notificationsLayout.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                callingUserPhotoView.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                        currentUserCameraFloatingLayout.setCornerRadius(-1f);
                        switchingToPip = false;
                        currentUserCameraFloatingLayout.switchingToPip = false;
                        previousState = currentState;
                        updateViewState();
                    }
                });
                animator.setDuration(350);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }, 32);
        }, 32);

    }

    public Animator createPiPTransition(boolean enter) {
        currentUserCameraFloatingLayout.animate().cancel();
        float toX = VoIPPiPView.getInstance().windowLayoutParams.x + VoIPPiPView.getInstance().xOffset;
        float toY = VoIPPiPView.getInstance().windowLayoutParams.y + VoIPPiPView.getInstance().yOffset;

        float cameraFromX = currentUserCameraFloatingLayout.getX();
        float cameraFromY = currentUserCameraFloatingLayout.getY();
        float cameraFromScale = currentUserCameraFloatingLayout.getScaleX();
        boolean animateCamera = true;

        float callingUserFromX = 0;
        float callingUserFromY = 0;
        float callingUserFromScale = 1f;
        float callingUserToScale, callingUserToX, callingUserToY;
        float cameraToScale, cameraToX, cameraToY;

        float pipScale = VoIPPiPView.isExpanding() ? 0.4f : 0.25f;
        callingUserToScale = pipScale;
        callingUserToX = toX - (callingUserTextureView.getMeasuredWidth() - callingUserTextureView.getMeasuredWidth() * callingUserToScale) / 2f;
        callingUserToY = toY - (callingUserTextureView.getMeasuredHeight() - callingUserTextureView.getMeasuredHeight() * callingUserToScale) / 2f;
        if (callingUserIsVideo) {
            int currentW = currentUserCameraFloatingLayout.getMeasuredWidth();
            if (currentUserIsVideo && currentW != 0) {
                cameraToScale = (windowView.getMeasuredWidth() / (float) currentW) * pipScale * 0.4f;
                cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f +
                    VoIPPiPView.getInstance().parentWidth * pipScale - VoIPPiPView.getInstance().parentWidth * pipScale * 0.4f - AndroidUtilities.dp(4);
                cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f +
                    VoIPPiPView.getInstance().parentHeight * pipScale - VoIPPiPView.getInstance().parentHeight * pipScale * 0.4f - AndroidUtilities.dp(4);
            } else {
                cameraToScale = 0;
                cameraToX = 1f;
                cameraToY = 1f;
                animateCamera = false;
            }
        } else {
            cameraToScale = pipScale;
            cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f;
            cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f;
        }

        float cameraCornerRadiusFrom = callingUserIsVideo ? AndroidUtilities.dp(4) : 0;
        float cameraCornerRadiusTo = AndroidUtilities.dp(4) * 1f / cameraToScale;

        float fromCameraAlpha = 1f;
        float toCameraAlpha = 1f;
        if (callingUserIsVideo) {
            fromCameraAlpha = VoIPPiPView.isExpanding() ? 1f : 0f;
        }

        if (enter) {
            if (animateCamera) {
                currentUserCameraFloatingLayout.setScaleX(cameraToScale);
                currentUserCameraFloatingLayout.setScaleY(cameraToScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraToX);
                currentUserCameraFloatingLayout.setTranslationY(cameraToY);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusTo);
                currentUserCameraFloatingLayout.setAlpha(fromCameraAlpha);
            }
            callingUserTextureView.setScaleX(callingUserToScale);
            callingUserTextureView.setScaleY(callingUserToScale);
            callingUserTextureView.setTranslationX(callingUserToX);
            callingUserTextureView.setTranslationY(callingUserToY);
            callingUserTextureView.setRoundCorners(AndroidUtilities.dp(6) * 1f / callingUserToScale);

            callingUserPhotoView.setAlpha(0f);
            callingUserPhotoView.setScaleX(callingUserToScale);
            callingUserPhotoView.setScaleY(callingUserToScale);
            callingUserPhotoView.setTranslationX(callingUserToX);
            callingUserPhotoView.setTranslationY(callingUserToY);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(enter ? 1f : 0, enter ? 0 : 1f);

        enterTransitionProgress = enter ? 0f : 1f;
        updateSystemBarColors();

        boolean finalAnimateCamera = animateCamera;
        float finalFromCameraAlpha = fromCameraAlpha;
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            enterTransitionProgress = 1f - v;
            updateSystemBarColors();

            if (finalAnimateCamera) {
                float cameraScale = cameraFromScale * (1f - v) + cameraToScale * v;
                currentUserCameraFloatingLayout.setScaleX(cameraScale);
                currentUserCameraFloatingLayout.setScaleY(cameraScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraFromX * (1f - v) + cameraToX * v);
                currentUserCameraFloatingLayout.setTranslationY(cameraFromY * (1f - v) + cameraToY * v);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusFrom * (1f - v) + cameraCornerRadiusTo * v);
                currentUserCameraFloatingLayout.setAlpha(toCameraAlpha * (1f - v) + finalFromCameraAlpha * v);
            }

            float callingUserScale = callingUserFromScale * (1f - v) + callingUserToScale * v;
            callingUserTextureView.setScaleX(callingUserScale);
            callingUserTextureView.setScaleY(callingUserScale);
            float tx = callingUserFromX * (1f - v) + callingUserToX * v;
            float ty = callingUserFromY * (1f - v) + callingUserToY * v;

            callingUserTextureView.setTranslationX(tx);
            callingUserTextureView.setTranslationY(ty);
            callingUserTextureView.setRoundCorners(v * AndroidUtilities.dp(4) * 1 / callingUserScale);
            if (!currentUserCameraFloatingLayout.measuredAsFloatingMode) {
                currentUserTextureView.setScreenshareMiniProgress(v, false);
            }

            callingUserPhotoView.setScaleX(callingUserScale);
            callingUserPhotoView.setScaleY(callingUserScale);
            callingUserPhotoView.setTranslationX(tx);
            callingUserPhotoView.setTranslationY(ty);
            callingUserPhotoView.setAlpha(1f - v);
        });
        return animator;
    }

    private void expandEmoji(boolean expanded) {
        if (!emojiLoaded || emojiExpanded == expanded || !uiVisible) {
            return;
        }
        emojiExpanded = expanded;
        if (emojiExpandAnimation != null)
            emojiExpandAnimation.cancel();
        if (expanded) {
//            if (emojiTooltip != null) {
//                emojiTooltip.animate().alpha(0).setInterpolator(new LinearInterpolator()).withEndAction(() -> emojiTooltip.setVisibility(View.GONE)).setDuration(67).start();
//                MessagesController.getGlobalMainSettings().edit().putBoolean("voipEmojiTooltipShown", true).apply();
//            }

            if (emojiTooltipContainer != null) {
                emojiTooltipContainer.animate().alpha(0).setInterpolator(new LinearInterpolator()).withEndAction(() -> emojiTooltipContainer.setVisibility(View.GONE)).setDuration(67).start();
                MessagesController.getGlobalMainSettings().edit().putBoolean("voipEmojiTooltipShown", true).apply();
            }
            emojiOverlay.setVisibility(View.VISIBLE);
            hideEmojiBtn.setVisibility(View.VISIBLE);
            emojiOverlay.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    emojiOverlay.getViewTreeObserver().removeOnPreDrawListener(this);

                    emojiOverlay.setPivotX(emojiOverlay.getWidth() / 2f);
                    emojiOverlay.setPivotY(AndroidUtilities.dp(41));
                    float scale = emojiViews[0].getHeight() / (float) emojiAnimatedLayout.getHeight();
                    AnimatorSet motion = new AnimatorSet();
                    ArrayList<Animator> motionAnims = new ArrayList<>();
                    ArrayList<Animator> alphaAnims = new ArrayList<>();

                    motionAnims.add(ObjectAnimator.ofFloat(emojiOverlay, View.TRANSLATION_Y, -emojiOverlay.getTop() + emojiLayout.getTop() - AndroidUtilities.dp(41) + emojiViews[0].getHeight() / 2f, 0));
                    motionAnims.add(ObjectAnimator.ofFloat(emojiOverlay, AndroidUtilities.VIEW_SCALE, scale, 1));
                    motionAnims.add(ObjectAnimator.ofFloat(emojiLayout, View.TRANSLATION_Y, -(-emojiOverlay.getTop() + emojiLayout.getTop() - AndroidUtilities.dp(52) + emojiViews[0].getHeight() / 2f)));
                    motionAnims.add(ObjectAnimator.ofFloat(emojiLayout, AndroidUtilities.VIEW_SCALE, 1f / scale));
                    motionAnims.add(ObjectAnimator.ofFloat(avatarAndWavesWrap, AndroidUtilities.VIEW_SCALE, 0.001f));
                    alphaAnims.add(ObjectAnimator.ofFloat(avatarAndWavesWrap, View.ALPHA, 0f));
                    motionAnims.add(ObjectAnimator.ofFloat(hideEmojiBtn, AndroidUtilities.VIEW_SCALE, .5f, 1f));
                    alphaAnims.add(ObjectAnimator.ofFloat(hideEmojiBtn, View.ALPHA, 0, 1));
                    if (!isAnyVideoActive()) {
                        motionAnims.add(ObjectAnimator.ofFloat(statusLayout, View.TRANSLATION_Y, AndroidUtilities.dp(34)));
                    } else {
                        alphaAnims.add(ObjectAnimator.ofFloat(statusLayout, View.ALPHA, 0));
                    }
                    motion.playTogether(motionAnims);
                    motion.setDuration(500);
                    motion.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.11, 1.00));

                    alphaAnims.add(ObjectAnimator.ofFloat(emojiOverlay, View.ALPHA, 0, 1));
                    alphaAnims.add(ObjectAnimator.ofFloat(emojiLayout, View.ALPHA, 1, 0));
                    AnimatorSet alpha = new AnimatorSet();
                    alpha.playTogether(alphaAnims);
                    alpha.setDuration(200);
                    alpha.setInterpolator(new LinearInterpolator());

                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(motion, alpha);
                    set.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            emojiExpandAnimation = null;
                        }
                    });
                    set.start();
                    emojiExpandAnimation = set;

                    return true;
                }
            });
        } else {
            float scale = emojiViews[0].getHeight() / (float) emojiAnimatedLayout.getHeight();
            AnimatorSet motion = new AnimatorSet();
            ArrayList<Animator> motionAnims = new ArrayList<>();
            ArrayList<Animator> alphaAnims = new ArrayList<>();
            motionAnims.add(ObjectAnimator.ofFloat(emojiOverlay, View.TRANSLATION_Y, 0, -emojiOverlay.getTop() + emojiLayout.getTop() - AndroidUtilities.dp(41) + emojiViews[0].getHeight() / 2f));
            motionAnims.add(ObjectAnimator.ofFloat(emojiOverlay, AndroidUtilities.VIEW_SCALE, scale));
            motionAnims.add(ObjectAnimator.ofFloat(emojiLayout, View.TRANSLATION_Y, 0));
            motionAnims.add(ObjectAnimator.ofFloat(emojiLayout, AndroidUtilities.VIEW_SCALE, 1f));
            motionAnims.add(ObjectAnimator.ofFloat(avatarAndWavesWrap, AndroidUtilities.VIEW_SCALE, 1f));
            alphaAnims.add(ObjectAnimator.ofFloat(avatarAndWavesWrap, View.ALPHA, 1f));
            motionAnims.add(ObjectAnimator.ofFloat(hideEmojiBtn, AndroidUtilities.VIEW_SCALE, .5f));
            alphaAnims.add(ObjectAnimator.ofFloat(hideEmojiBtn, View.ALPHA, 0));
            if (!isAnyVideoActive()) {
                motionAnims.add(ObjectAnimator.ofFloat(statusLayout, View.TRANSLATION_Y, 0));
            } else {
                alphaAnims.add(ObjectAnimator.ofFloat(statusLayout, View.ALPHA, 1));
            }
            motion.playTogether(motionAnims);
            motion.setDuration(333);
            motion.setInterpolator(new CubicBezierInterpolator(0.33, 0.00, 0.11, 1.00));

            alphaAnims.add(ObjectAnimator.ofFloat(emojiOverlay, View.ALPHA, 1, 0));
            alphaAnims.add(ObjectAnimator.ofFloat(emojiLayout, View.ALPHA, 0, 1));
            AnimatorSet alpha = new AnimatorSet();
            alpha.playTogether(alphaAnims);
            alpha.setDuration(100);
            alpha.setInterpolator(new LinearInterpolator());

            AnimatorSet set = new AnimatorSet();
            set.playTogether(motion, alpha);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    emojiOverlay.setVisibility(View.GONE);
                    hideEmojiBtn.setVisibility(View.GONE);
                    emojiExpandAnimation = null;
                }
            });
            set.start();
            emojiExpandAnimation = set;
        }
    }

    private void updateViewState() {
        if (isFinished || switchingToPip) {
            return;
        }
        lockOnScreen = false;
        boolean animated = previousState != -1;
        boolean showAcceptDeclineView = false;
        boolean showTimer = false;
        boolean showReconnecting = false;
        boolean showCallingAvatarMini = false;
        int statusLayoutOffset = 0;
        VoIPService service = VoIPService.getSharedInstance();

        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = true;
                statusLayoutOffset = AndroidUtilities.dp(24);
                acceptDeclineView.setRetryMod(false);
                if (service != null && service.privateCall.video) {
                    if (currentUserIsVideo && callingUser.photo != null) {
                        showCallingAvatarMini = true;
                    } else {
                        showCallingAvatarMini = false;
                    }
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), true, animated);
                    acceptDeclineView.setTranslationY(-AndroidUtilities.dp(60));
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), true, animated);
                    acceptDeclineView.setTranslationY(0);
                }
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
                break;
            case VoIPService.STATE_REQUESTING:
                statusTextView.setText(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true, animated);
                break;
            case VoIPService.STATE_HANGING_UP:
                break;
            case VoIPService.STATE_BUSY:
                showAcceptDeclineView = true;
                statusTextView.setText(LocaleController.getString("VoipBusy", R.string.VoipBusy), false, animated);
                acceptDeclineView.setRetryMod(true);
                currentUserIsVideo = false;
                callingUserIsVideo = false;
                break;
            case VoIPService.STATE_ESTABLISHED:
                avatarWaves.isCallEstablished = true;
                if (service.overdrawState == 0) {
                    animateAvatar();
                }
            case VoIPService.STATE_RECONNECTING:
                updateKeyView(animated);
                showTimer = true;
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = true;
                }
                break;
            case VoIPService.STATE_ENDED:
                currentUserTextureView.saveCameraLastBitmap();
                AndroidUtilities.runOnUIThread(() -> {
                    if (!ratingMode)
                        windowView.finish();
                }, 200);
                break;
            case VoIPService.STATE_FAILED:
                statusTextView.setText(LocaleController.getString("VoipFailed", R.string.VoipFailed), false, animated);
                final VoIPService voipService = VoIPService.getSharedInstance();
                final String lastError = voipService != null ? voipService.getLastError() : Instance.ERROR_UNKNOWN;
                if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
                    if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
                        if (isVideoCall) {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name);
                            boolean[] callAgain = new boolean[1];
                            AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                                .setMessage(AndroidUtilities.replaceTags(message))
                                .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> windowView.finish())
                                .setPositiveButton(LocaleController.getString("VoipPeerVideoOutdatedMakeVoice", R.string.VoipPeerVideoOutdatedMakeVoice), (dialogInterface, i) -> {
                                    callAgain[0] = true;
                                    currentState = VoIPService.STATE_BUSY;
                                    Intent intent = new Intent(activity, VoIPService.class);
                                    intent.putExtra("user_id", callingUser.id);
                                    intent.putExtra("is_outgoing", true);
                                    intent.putExtra("start_incall_activity", false);
                                    intent.putExtra("video_call", false);
                                    intent.putExtra("can_video_call", false);
                                    intent.putExtra("account", currentAccount);
                                    try {
                                        activity.startService(intent);
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
                                })
                                .show();
                            dlg.setCanceledOnTouchOutside(true);
                            dlg.setOnDismissListener(dialog -> {
                                if (!callAgain[0]) {
                                    windowView.finish();
                                }
                            });
                        } else {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name);
                            showErrorDialog(AndroidUtilities.replaceTags(message));
                        }
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
                        showErrorDialog("Error initializing audio hardware");
                    } else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
                        windowView.finish();
                    } else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
                        showErrorDialog(LocaleController.getString("VoipErrorUnknown", R.string.VoipErrorUnknown));
                    } else {
                        AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                }
                break;
        }
        if (previewDialog != null) {
            return;
        }

        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        notificationsLayout.setIsVideo(isAnyVideoActive());
        statusTextView.setIsVideo(isAnyVideoActive());

        if (animated) {
            currentUserCameraFloatingLayout.saveRelativePosition();
            callingUserMiniFloatingLayout.saveRelativePosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                callingUserPhotoView.setAlpha(1f);
            }
            if (animated) {
                callingUserTextureView.animate().alpha(1f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(1f);
            }
            if (!callingUserTextureView.renderer.isFirstFrameRendered() && !enterFromPiP) {
                callingUserIsVideo = false;
            }
        }

        if (isAnyVideoActive()) {
            fillNavigationBar(true, animated);
        } else {
            fillNavigationBar(false, animated);
            callingUserPhotoView.setVisibility(View.VISIBLE);
            if (animated) {
                callingUserTextureView.animate().alpha(0f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(0f);
            }
        }

        if (!isAnyVideoActive()) {
            cameraForceExpanded = false;
        }

        boolean showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded;

        showCallingUserAvatarMini(showCallingAvatarMini, animated);
        statusLayoutOffset += callingUserPhotoViewMini.getTag() == null ? 0 : AndroidUtilities.dp(135) + AndroidUtilities.dp(24);

        if (showAcceptDeclineView && currentState == VoIPService.STATE_WAITING_INCOMING) {
            showAcceptDeclineView(false, false);
            acceptButton.setVisibility(View.VISIBLE);
            acceptWaves.setVisibility(View.VISIBLE);
            acceptButton.setData(R.drawable.ic_call, 0xffffffff, 0xFF40C749, LocaleController.getString("AcceptCall", R.string.AcceptCall), true, false);
            buttonsLayout.setTranslationY(AndroidUtilities.dp(-46));
            bottomButtons[0].setVisibility(View.INVISIBLE);
            bottomButtons[1].setVisibility(View.INVISIBLE);
            bottomButtons[2].setVisibility(View.INVISIBLE);
            bottomButtons[3].setVisibility(View.VISIBLE);
            bottomButtons[3].setTranslationX(AndroidUtilities.dp(-31));
            bottomButtons[3].setData(R.drawable.calls_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("DeclineCall", R.string.DeclineCall), true, animated);
            bottomButtons[3].setOnClickListener(v -> {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                }
            });
        } else {
            showAcceptDeclineView(showAcceptDeclineView, animated);
        }

        windowView.setLockOnScreen(lockOnScreen || deviceIsLocked);
        canHideUI = currentState == VoIPService.STATE_ESTABLISHED && isAnyVideoActive();
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }

        if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
            hideUiRunnableWaiting = true;
        } else if (service != null && service.isMicMute()) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
        }
        if (!uiVisible) {
            statusLayoutOffset -= AndroidUtilities.dp(50);
        }

        if (animated) {
            if (lockOnScreen || !uiVisible) {
                if (backIcon.getVisibility() != View.VISIBLE) {
                    backIcon.setVisibility(View.VISIBLE);
                    backIcon.setAlpha(0f);
                }
                backIcon.animate().alpha(0f).start();
            } else {
                backIcon.animate().alpha(1f).start();
            }
            notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (!lockOnScreen) {
                backIcon.setVisibility(View.VISIBLE);
            }
            backIcon.setAlpha(lockOnScreen ? 0 : 1f);
            notificationsLayout.setTranslationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0));
        }

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            updateButtons(animated);
        }

        if (showTimer) {
            statusTextView.showTimer(animated);
        }

        statusTextView.showReconnect(showReconnecting, animated);

        if (animated) {
            if (statusLayoutOffset != statusLayoutAnimateToOffset) {
                statusLayout.animate().translationY(statusLayoutOffset).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        } else {
            statusLayout.setTranslationY(statusLayoutOffset);
        }
        statusLayoutAnimateToOffset = statusLayoutOffset;
        overlayBackground.setShowBlackout(isAnyVideoActive(), animated);
        canSwitchToPip = currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY && isAnyVideoActive();

        int floatingViewsOffset;
        if (service != null) {
            if (currentUserIsVideo) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
            }
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());
            service.setSinks(currentUserIsVideo && !service.isScreencast() ? currentUserTextureView.renderer : null, showCallingUserVideoMini ? callingUserMiniTextureRenderer : callingUserTextureView.renderer);

            if (animated) {
                notificationsLayout.beforeLayoutChanges();
            }
            if (isAnyVideoActive() && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video", animated);

                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                notificationsLayout.removeNotification("video");
            }

            if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.showForView(bottomButtons[1], true);
            } else if (notificationsLayout.getChildCount() != 0) {
                tapToVideoTooltip.hide();
            }

            if (animated) {
                notificationsLayout.animateLayoutChanges();
            }
        }

        floatingViewsOffset = notificationsLayout.getChildsHight();

        callingUserMiniFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        callingUserMiniFloatingLayout.setUiVisible(uiVisible);

        if (currentUserIsVideo) {
            if (!callingUserIsVideo || cameraForceExpanded) {
                showFloatingLayout(STATE_FULLSCREEN, animated);
            } else {
                showFloatingLayout(STATE_FLOATING, animated);
            }
        } else {
            showFloatingLayout(STATE_GONE, animated);
        }

        if (showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() == null) {
            callingUserMiniFloatingLayout.setIsActive(true);
            if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
                callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
                callingUserMiniFloatingLayout.setAlpha(0f);
                callingUserMiniFloatingLayout.setScaleX(0.5f);
                callingUserMiniFloatingLayout.setScaleY(0.5f);
            }
            callingUserMiniFloatingLayout.animate().setListener(null).cancel();
            callingUserMiniFloatingLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150).start();
            callingUserMiniFloatingLayout.setTag(1);
        } else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() != null) {
            callingUserMiniFloatingLayout.setIsActive(false);
            callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (callingUserMiniFloatingLayout.getTag() == null) {
                        callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    }
                }
            }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserMiniFloatingLayout.setTag(null);
        }

        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();

        updateSpeakerPhoneIcon();
    }

    private void fillNavigationBar(boolean fill, boolean animated) {
        if (switchingToPip) {
            return;
        }
        if (!animated) {
            if (naviagtionBarAnimator != null) {
                naviagtionBarAnimator.cancel();
            }
            fillNaviagtionBarValue = fill ? 1 : 0;
            overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * (fill ? 1f : 0.5f))));
        } else if (fill != fillNaviagtionBar) {
            if (naviagtionBarAnimator != null) {
                naviagtionBarAnimator.cancel();
            }
            naviagtionBarAnimator = ValueAnimator.ofFloat(fillNaviagtionBarValue, fill ? 1 : 0);
            naviagtionBarAnimator.addUpdateListener(navigationBarAnimationListener);
            naviagtionBarAnimator.setDuration(300);
            naviagtionBarAnimator.setInterpolator(new LinearInterpolator());
            naviagtionBarAnimator.start();
        }
        fillNaviagtionBar = fill;
    }

    private void showUi(boolean show) {
        if (uiVisibilityAnimator != null) {
            uiVisibilityAnimator.cancel();
        }

        if (!show && uiVisible) {
            speakerPhoneIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(0).translationY(AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            bottomShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 0);
            uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
            uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
            uiVisibilityAnimator.start();
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            buttonsLayout.setEnabled(false);
        } else if (show && !uiVisible) {
            tapToVideoTooltip.hide();
            speakerPhoneIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            bottomShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 1f);
            uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
            uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
            uiVisibilityAnimator.start();
            buttonsLayout.setEnabled(true);
        }

        uiVisible = show;
        windowView.requestFullscreen(!show);
        notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    private void showFloatingLayout(int state, boolean animated) {
        if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) {
            currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        }
        if (!animated && cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
        }
        if (state == STATE_GONE) {
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != STATE_GONE) {
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
                    );
                    if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_FLOATING) {
                        animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
                        );
                    }
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentUserCameraFloatingLayout.setTranslationX(0);
                            currentUserCameraFloatingLayout.setTranslationY(0);
                            currentUserCameraFloatingLayout.setScaleY(1f);
                            currentUserCameraFloatingLayout.setScaleX(1f);
                            currentUserCameraFloatingLayout.setVisibility(View.GONE);
                        }
                    });
                    cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    cameraShowingAnimator.setStartDelay(50);
                    cameraShowingAnimator.start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
        } else {
            boolean switchToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                switchToFloatAnimated = false;
            }
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                    if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
                        currentUserCameraFloatingLayout.setAlpha(0f);
                        currentUserCameraFloatingLayout.setScaleX(0.7f);
                        currentUserCameraFloatingLayout.setScaleY(0.7f);
                        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
                    }
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
                        ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
                    );
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.setDuration(150).start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
            }
            if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
                currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
                currentUserCameraIsFullscreen = true;
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated);
            currentUserCameraIsFullscreen = state != STATE_FLOATING;
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    private void showCallingUserAvatarMini(boolean show, boolean animated) {
        if (animated) {
            if (show && callingUserPhotoViewMini.getTag() == null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.setVisibility(View.VISIBLE);
                callingUserPhotoViewMini.setAlpha(0);
                callingUserPhotoViewMini.setTranslationY(-AndroidUtilities.dp(135));
                callingUserPhotoViewMini.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            } else if (!show && callingUserPhotoViewMini.getTag() != null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.animate().alpha(0).translationY(-AndroidUtilities.dp(135)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            callingUserPhotoViewMini.setVisibility(View.GONE);
                        }
                    }).start();
            }
        } else {
            callingUserPhotoViewMini.animate().setListener(null).cancel();
            callingUserPhotoViewMini.setTranslationY(0);
            callingUserPhotoViewMini.setAlpha(1f);
            callingUserPhotoViewMini.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        callingUserPhotoViewMini.setTag(show ? 1 : null);
    }

    private void updateKeyView(boolean animated) {
        if (emojiLoaded) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        byte[] auth_key = null;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(service.getEncryptionKey());
            buf.write(service.getGA());
            auth_key = buf.toByteArray();
        } catch (Exception checkedExceptionsAreBad) {
            FileLog.e(checkedExceptionsAreBad, false);
        }
        if (auth_key == null) {
            return;
        }
        byte[] sha256 = Utilities.computeSHA256(auth_key, 0, auth_key.length);
        String[] emoji = EncryptionKeyEmojifier.emojifyForCall(sha256);
        boolean hasAllAnimated = true;
        TLRPC.Document[] animatedEmojis = new TLRPC.Document[4];
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(emoji[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                drawable.preload();
                emojiViews[i].setImageDrawable(drawable);
                emojiViews[i].setContentDescription(emoji[i]);
                emojiViews[i].setVisibility(View.GONE);
            }
            emojiDrawables[i] = drawable;
            TLRPC.Document sticker = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji[i]);
            if (sticker == null) {
                hasAllAnimated = false;
            } else {
                animatedEmojis[i] = sticker;
            }
        }
        if (hasAllAnimated) {
            for (int i = 0; i < 4; i++) {
                animatedEmojiViews[i].setAnimation(animatedEmojis[i], 42, 42);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                Drawable ed = Emoji.getEmojiDrawable(emoji[i]);
                if (ed == null)
                    continue;
                InsetDrawable inset = new InsetDrawable(ed, AndroidUtilities.dp(3.5f));
                animatedEmojiViews[i].setImageDrawable(inset);
            }
        }

        checkEmojiLoaded(animated);

        if (emojiTooltipContainer != null) {
            emojiTooltipContainer.setVisibility(View.VISIBLE);
            emojiTooltipContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    emojiTooltipContainer.getViewTreeObserver().removeOnPreDrawListener(this);

                    emojiTooltipContainer.setPivotX(emojiTooltipContainer.getWidth() / 2f);
                    emojiTooltipContainer.setPivotY(0);

                    ObjectAnimator scale1 = ObjectAnimator.ofFloat(emojiTooltipContainer, AndroidUtilities.VIEW_SCALE, 0.6f, 1.02f).setDuration(167);
                    scale1.setInterpolator(new CubicBezierInterpolator(0.48, 0.00, 0.35, 0.49));
                    ObjectAnimator scale2 = ObjectAnimator.ofFloat(emojiTooltipContainer, AndroidUtilities.VIEW_SCALE, 1f).setDuration(150);
                    scale2.setInterpolator(new CubicBezierInterpolator(0.25, -3.66, 0.57, 1.00));
                    AnimatorSet scale = new AnimatorSet();
                    scale.playSequentially(scale1, scale2);
                    ObjectAnimator alpha = ObjectAnimator.ofFloat(emojiTooltipContainer, View.ALPHA, 0f, 1f).setDuration(100);
                    alpha.setInterpolator(new LinearInterpolator());

                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(scale, alpha);
                    set.start();

                    return true;
                }
            });
        }
    }

    private void checkEmojiLoaded(boolean animated) {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            if (emojiDrawables[i] != null && emojiDrawables[i].isLoaded()) {
                count++;
            }
        }

        if (count == 4) {
            emojiLoaded = true;
            for (int i = 0; i < 4; i++) {
                if (emojiViews[i].getVisibility() != View.VISIBLE) {
                    emojiViews[i].setVisibility(View.VISIBLE);
                    if (animated) {
                        emojiViews[i].setAlpha(0f);
                        emojiViews[i].setTranslationY(AndroidUtilities.dp(30));
                        emojiViews[i].animate().alpha(1f).translationY(0f).setDuration(200).setStartDelay(20 * i).start();
                    }
                }
            }
        }
    }

    private void showAcceptDeclineView(boolean show, boolean animated) {
        if (!animated) {
            acceptDeclineView.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            if (show && acceptDeclineView.getTag() == null) {
                acceptDeclineView.animate().setListener(null).cancel();
                if (acceptDeclineView.getVisibility() == View.GONE) {
                    acceptDeclineView.setVisibility(View.VISIBLE);
                    acceptDeclineView.setAlpha(0);
                }
                acceptDeclineView.animate().alpha(1f);
            }
            if (!show && acceptDeclineView.getTag() != null) {
                acceptDeclineView.animate().setListener(null).cancel();
                acceptDeclineView.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        acceptDeclineView.setVisibility(View.GONE);
                    }
                }).alpha(0f);
            }
        }

        acceptDeclineView.setEnabled(show);
        acceptDeclineView.setTag(show ? 1 : null);
    }

    private void updateButtons(boolean animated) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionSet transitionSet = new TransitionSet();
            Visibility visibility = new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(100), 0);
                    if (view instanceof VoIPToggleButton) {
                        view.setTranslationY(AndroidUtilities.dp(100));
                        animator.setStartDelay(((VoIPToggleButton) view).animationDelay);
                    }
                    return animator;
                }

                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                }
            };
            transitionSet
                .addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
                .addTransition(new ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT));
            transitionSet.excludeChildren(VoIPToggleButton.class, true);
            TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
        }

        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && isAnyVideoActive()) {
                    setFrontalCameraAction(bottomButtons[0], service, animated);
                    if (uiVisible) {
                        speakerPhoneIcon.animate().alpha(1f).start();
                    }
                } else {
                    setSpeakerPhoneAction(bottomButtons[0], service, animated);
                    speakerPhoneIcon.animate().alpha(0).start();
                }
                setVideoAction(bottomButtons[1], service, animated);
                setMicrohoneAction(bottomButtons[2], service, animated);
            } else {
                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);
            }
            bottomButtons[3].setVisibility(View.GONE);
        } else {
            if (instance == null) {
                return;
            }
            if (!service.isScreencast() && isAnyVideoActive()) {
                setFrontalCameraAction(bottomButtons[0], service, animated);
                if (uiVisible) {
                    speakerPhoneIcon.setTag(1);
                    speakerPhoneIcon.animate().alpha(1f).start();
                }
            } else {
                setSpeakerPhoneAction(bottomButtons[0], service, animated);
                speakerPhoneIcon.setTag(null);
                speakerPhoneIcon.animate().alpha(0f).start();
            }
            setVideoAction(bottomButtons[1], service, animated);
            setMicrohoneAction(bottomButtons[2], service, animated);

            bottomButtons[3].setData(R.drawable.calls_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), false, animated);
            bottomButtons[3].setOnClickListener(view -> {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().hangUp();
                }
            });
        }

        int animationDelay = 0;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() == View.VISIBLE) {
                bottomButtons[i].animationDelay = animationDelay;
                animationDelay += 16;
            }
        }
        updateSpeakerPhoneIcon();
    }

    private void setMicrohoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isMicMute()) {
            bottomButton.setData(R.drawable.calls_unmute, Color.BLACK, Color.WHITE, LocaleController.getString("VoipUnmute", R.string.VoipUnmute), true, animated);
        } else {
            bottomButton.setData(R.drawable.calls_unmute, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipMute", R.string.VoipMute), false, animated);
        }
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
                final boolean micMute = !serviceInstance.isMicMute();
                if (accessibilityManager.isTouchExplorationEnabled()) {
                    final String text;
                    if (micMute) {
                        text = LocaleController.getString("AccDescrVoipMicOff", R.string.AccDescrVoipMicOff);
                    } else {
                        text = LocaleController.getString("AccDescrVoipMicOn", R.string.AccDescrVoipMicOn);
                    }
                    view.announceForAccessibility(text);
                }
                serviceInstance.setMicMute(micMute, false, true);
                previousState = currentState;
                updateViewState();
            }
        });
    }

    private void setVideoAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        if (isAnyVideoActive()) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                bottomButton.setData(service.isScreencast() ? R.drawable.calls_sharescreen : R.drawable.calls_video, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), false, animated);
            } else {
                bottomButton.setData(R.drawable.calls_video, Color.BLACK, Color.WHITE, LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), true, animated);
            }
            bottomButton.setCrossOffset(-AndroidUtilities.dpf2(3.5f));
            bottomButton.setOnClickListener(view -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
                } else {
                    if (Build.VERSION.SDK_INT < 21 && service.privateCall != null && !service.privateCall.video && !callingUserIsVideo && !service.sharedUIParams.cameraAlertWasShowed) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(LocaleController.getString("VoipSwitchToVideoCall", R.string.VoipSwitchToVideoCall));
                        builder.setPositiveButton(LocaleController.getString("VoipSwitch", R.string.VoipSwitch), (dialogInterface, i) -> {
                            service.sharedUIParams.cameraAlertWasShowed = true;
                            toggleCameraInput();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.create().show();
                    } else {
                        toggleCameraInput();
                    }
                }
            });
            bottomButton.setEnabled(true);
        } else {
            bottomButton.setData(R.drawable.calls_video, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), "Video", false, animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        }
    }

    private void updateSpeakerPhoneIcon() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (service.isBluetoothOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_bluetooth);
        } else if (service.isSpeakerphoneOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_speaker);
        } else {
            if (service.isHeadsetPlugged()) {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_headset);
            } else {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_phone);
            }
        }
    }

    private void setSpeakerPhoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isBluetoothOn()) {
            bottomButton.setData(R.drawable.calls_bluetooth, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), false, animated);
            bottomButton.setChecked(false, animated);
        } else if (service.isSpeakerphoneOn()) {
            bottomButton.setData(R.drawable.calls_speaker, Color.BLACK, Color.WHITE, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
            bottomButton.setChecked(true, animated);
        } else {
            bottomButton.setData(R.drawable.calls_speaker, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
            bottomButton.setChecked(false, animated);
        }
        bottomButton.setCheckableForAccessibility(true);
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });
    }

    private void setFrontalCameraAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setData(R.drawable.calls_flip, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        } else {
            bottomButton.setEnabled(true);
            if (!service.isFrontFaceCamera()) {
                bottomButton.setData(R.drawable.calls_flip, Color.BLACK, Color.WHITE, LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            } else {
                bottomButton.setData(R.drawable.calls_flip, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            }

            bottomButton.setOnClickListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }
                    serviceInstance.switchCamera();
                }
            });
        }
    }

    public void onScreenCastStart() {
        if (previewDialog == null) {
            return;
        }
        previewDialog.dismiss(true, true);
    }

    private boolean isAnyVideoActive() {
        return callingUserIsVideo || currentUserIsVideo;
    }

    private void toggleCameraInput() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final String text;
                if (!currentUserIsVideo) {
                    text = LocaleController.getString("AccDescrVoipCamOn", R.string.AccDescrVoipCamOn);
                } else {
                    text = LocaleController.getString("AccDescrVoipCamOff", R.string.AccDescrVoipCamOff);
                }
                fragmentView.announceForAccessibility(text);
            }
            if (!currentUserIsVideo) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if (previewDialog == null) {
                        service.createCaptureDevice(false);
                        if (!service.isFrontFaceCamera()) {
                            service.switchCamera();
                        }
                        windowView.setLockOnScreen(true);
                        previewDialog = new PrivateVideoPreviewDialog(fragmentView.getContext(), false, true) {
                            @Override
                            public void onDismiss(boolean screencast, boolean apply) {
                                previewDialog = null;
                                VoIPService service = VoIPService.getSharedInstance();
                                windowView.setLockOnScreen(false);
                                if (apply) {
                                    currentUserIsVideo = true;
                                    if (service != null && !screencast) {
                                        service.requestVideoCall(false);
                                        service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                previousState = currentState;
                                updateViewState();
                            }
                        };
                        if (lastInsets != null) {
                            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
                        }
                        fragmentView.addView(previewDialog);
                    }
                    return;
                } else {
                    currentUserIsVideo = true;
                    if (!service.isSpeakerphoneOn()) {
                        VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
                    }
                    service.requestVideoCall(false);
                    service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                }
            } else {
                currentUserTextureView.saveCameraLastBitmap();
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 21) {
                    service.clearCamera();
                }
            }
            previousState = currentState;
            updateViewState();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPService.getSharedInstance().acceptIncomingCall();
            } else {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(activity, () -> windowView.finish(), requestCode);
                    return;
                }
            }
        }
        if (requestCode == 102) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCameraInput();
            }
        }
    }

    private void updateSystemBarColors() {
        overlayPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f * uiVisibilityAlpha * enterTransitionProgress)));
        overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * (0.5f + 0.5f * fillNaviagtionBarValue) * enterTransitionProgress)));
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onPause();
        }
    }

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onResume();
        }
    }

    public void onPauseInternal() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }

        boolean hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity);

        if (canSwitchToPip && hasPermissionsToPip) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }

        if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
    }

    public void onResumeInternal() {
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.finish();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
            updateViewState();
        } else {
            windowView.finish();
        }

        deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
    }

    private void showErrorDialog(CharSequence message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dlg = new DarkAlertDialog.Builder(activity)
            .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
            .setMessage(message)
            .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
            .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(dialog -> windowView.finish());
    }

    @SuppressLint("InlinedApi")
    private void requestInlinePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlertsCreator.createDrawOverlayPermissionDialog(activity, (dialogInterface, i) -> {
                if (windowView != null) {
                    windowView.finish();
                }
            }).show();
        }
    }

    private void animateAvatar() {
        AndroidUtilities.cancelRunOnUIThread(backgroundSwitchRunnable);
        VoIPService service = VoIPService.getSharedInstance();
        backgroundGLView.setOverdrawColors(
            voipColorScheme.getGreen()
        );
        backgroundGLView.startOverdraw(
            new float[]{
                callingUserPhotoView.getX() + callingUserPhotoView.getMeasuredWidth() * 1.2f,
                callingUserPhotoView.getY() + callingUserPhotoView.getMeasuredHeight() + callingUserPhotoView.getMeasuredHeight() / 9f
            }
        );
        service.overdrawState = 1;
        AndroidUtilities.runOnUIThread(() -> {
            service.overdrawState = 2;
            counterForeground = 2;
            numberOfColorSchemesToRotate = 3;
            AndroidUtilities.runOnUIThread(backgroundSwitchRunnable);
        }, 3000);
    }
}
