package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.OpenGLBitmapProcessor;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ShareReactionLayout extends FrameLayout {
    private final Paint containerPaint;
    private final RectF containerRect = new RectF();
    private float appearProgress = 0f;
    private final List<ShareOption> shareOptions = new ArrayList<>();
    private boolean dismissed;
    private float touchX, touchY;
    private ShareOption touchedOption;
    private MessageObject messageObject;
    private Function<TLRPC.User, Void> onShowBulletinCallback;
    private float hideProgress = 0f;
    private boolean isHideAnimationRunning;
    private boolean isFullyShown;
    private final int bounceOffset;
    private final OpenGLBitmapProcessor blurProcessor;
    private final OpenGLBitmapProcessor morphProcessor;
    private Bitmap morphBitmap;
    private Bitmap blurBitmap;
    private float morphBgX, morphBgY;
    private OnDismissListener onDismissListener;
    private final int longPressTimeout;

    public interface OnShareSelectedListener {
        void onShareSelected(TLRPC.User user, View sourceView, View targetView);
    }

    private OnShareSelectedListener shareSelectedListener;

    public void setShareSelectedListener(OnShareSelectedListener listener) {
        this.shareSelectedListener = listener;
    }

    public ShareReactionLayout(Context context) {
        super(context);
        longPressTimeout = ViewConfiguration.getLongPressTimeout();
        bounceOffset = dp(16);

        blurProcessor = new OpenGLBitmapProcessor(context);
        morphProcessor = new OpenGLBitmapProcessor(context);

        containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        containerPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void show(View messageView, MessageObject message, ArrayList<TLRPC.User> users, float x, float y, Function<TLRPC.User, Void> showBulletin) {
        messageObject = message;
        onShowBulletinCallback = showBulletin;

        int[] location = new int[2];
        messageView.getLocationInWindow(location);
        int statusBarHeight = AndroidUtilities.statusBarHeight;

        touchX = location[0] + x;
        touchY = location[1] + y - statusBarHeight;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.y = statusBarHeight;

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.addView(this, params);

        post(() -> {
            for (TLRPC.User user : users) {
                ShareOption option = new ShareOption(getContext());
                option.setUser(user);
                addView(option);
                shareOptions.add(option);
            }
            animateAppear();
        });
    }

    private void animateAppear() {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(125);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
            requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFullyShown = true;
            }
        });
        animator.start();

        int centerIndex = shareOptions.size() / 2;
        long baseDelay = 50L;

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            option.animate().cancel();
            option.setVisibility(VISIBLE);
            option.setAlpha(0f);

            float initialScale;
            int distanceFromCenter = Math.abs(i - centerIndex);

            if (i == centerIndex) {
                initialScale = 0.7f;
            } else if (distanceFromCenter == 1) {
                initialScale = 0.6f;
            } else {
                initialScale = 0.5f;
            }

            option.setScaleX(initialScale);
            option.setScaleY(initialScale);
            option.invalidate();

            long delay = baseDelay * (distanceFromCenter + 1);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(option, View.SCALE_X, initialScale, 1f),
                    ObjectAnimator.ofFloat(option, View.SCALE_Y, initialScale, 1f),
                    ObjectAnimator.ofFloat(option, View.ALPHA, 0f, 1f)
            );
            set.setStartDelay(delay);
            set.setDuration(100);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            set.start();
        }
    }

    private void dismiss() {
        if (dismissed) return;
        dismissed = true;

        recycleBitmaps();

        if (blurProcessor != null) {
            blurProcessor.onDetach();
        }
        if (morphProcessor != null) {
            morphProcessor.onDetach();
        }

        AnimatorSet dismissSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();

        ValueAnimator containerAnimator = ValueAnimator.ofFloat(1, 0);
        containerAnimator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animators.add(containerAnimator);

        for (ShareOption option : shareOptions) {
            animators.add(ObjectAnimator.ofFloat(option, View.SCALE_X, option.getScaleX(), 0f));
            animators.add(ObjectAnimator.ofFloat(option, View.SCALE_Y, option.getScaleY(), 0f));
            animators.add(ObjectAnimator.ofFloat(option, View.ALPHA, option.getAlpha(), 0f));
        }

        dismissSet.playTogether(animators);
        dismissSet.setDuration(180);
        dismissSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        dismissSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (getParent() != null) {
                    WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(ShareReactionLayout.this);
                }
                if (onDismissListener != null) {
                    onDismissListener.onDismiss();
                }
            }
        });
        dismissSet.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int itemSize = dp(36);
        int spacing = dp(4);

        float startX = containerRect.left + dp(8);
        float centerY = containerRect.top + (containerRect.height() - itemSize) / 2;

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            int left = (int) (startX + i * (itemSize + spacing));
            int top = (int) centerY;
            option.layout(left, top, left + itemSize, top + itemSize);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (dismissed) return;

        if (!isFullyShown) {
            Canvas morphCanvas = new Canvas(morphBitmap);
            morphCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            Canvas blurCanvas = new Canvas(blurBitmap);
            blurCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }

        int itemCount = shareOptions.size();
        int itemSize = dp(36);
        int itemSpacing = dp(4);
        float sidePadding = dp(8);

        float width = (itemCount * itemSize) + ((itemCount - 1) * itemSpacing) + (sidePadding * 2);
        float height = dp(48);

        containerRect.left = touchX - width / 2;
        containerRect.right = containerRect.left + width;
        containerRect.bottom = touchY - dp(40);
        containerRect.top = containerRect.bottom - height;

        if (containerRect.left < dp(16)) {
            containerRect.left = dp(16);
            containerRect.right = containerRect.left + width;
        } else if (containerRect.right > getWidth() - dp(16)) {
            containerRect.right = getWidth() - dp(16);
            containerRect.left = containerRect.right - width;
        }

        if (!isFullyShown) {
            canvas.save();
            float scale = 0.5f + 0.5f * appearProgress;
            canvas.scale(scale, scale, touchX, touchY);

            // This is the critical part that needs to be fixed
            morphProcessor.drawMorph(
                    morphBitmap,
                    containerRect.left - morphBgX,  // Removed extra padding
                    containerRect.top - morphBgY,   // Removed extra padding
                    containerRect.width(),          // Use full width
                    containerRect.height(),         // Use full height
                    dp(24),                        // Corner radius
                    touchX - morphBgX,             // Exact touch point
                    touchY - morphBgY,             // Exact touch point
                    dp(20),                        // Initial circle size
                    appearProgress,
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)
            );

            blurProcessor.processBitmap(morphBitmap, blurBitmap, dp(8) * (1f - appearProgress));

            canvas.drawBitmap(morphBitmap, morphBgX, morphBgY, null);
            canvas.drawBitmap(blurBitmap, 0, 0, null);

            canvas.restore();
        }

        containerPaint.setAlpha((int)(255 * appearProgress));
        canvas.drawRoundRect(containerRect, dp(24), dp(24), containerPaint);

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            option.setAlpha(touchedOption == null || touchedOption == option ? 1f : 0.5f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dismissed) return false;

        float x = event.getRawX();
        float y = event.getRawY() - AndroidUtilities.statusBarHeight;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - event.getDownTime() > longPressTimeout) {
                    checkForTouchedOption(x, y);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (touchedOption != null) {
                    TLRPC.User selectedUser = touchedOption.getUser();
                    if (selectedUser != null && messageObject != null) {
                        ArrayList<MessageObject> messages = new ArrayList<>();
                        messages.add(messageObject);

                        SendMessagesHelper.getInstance(UserConfig.selectedAccount)
                                .sendMessage(messages, selectedUser.id, false, false, true, 0);

                        if (onShowBulletinCallback != null) {
                            onShowBulletinCallback.apply(selectedUser);
                        }

                        if (shareSelectedListener != null) {
                            shareSelectedListener.onShareSelected(selectedUser, this, touchedOption);
                        }
                    }
                }
                dismiss();
                return true;
        }
        return false;
    }

    private void checkForTouchedOption(float x, float y) {
        ShareOption prevTouched = touchedOption;
        touchedOption = null;

        for (ShareOption option : shareOptions) {
            int[] location = new int[2];
            option.getLocationOnScreen(location);

            float left = location[0];
            float top = location[1] - AndroidUtilities.statusBarHeight;
            float right = left + option.getWidth();
            float bottom = top + option.getHeight();

            if (x >= left && x <= right && y >= top && y <= bottom) {
                touchedOption = option;
                if (prevTouched != touchedOption) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                break;
            }
        }

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w <= 0 || h <= 0) return;

        recycleBitmaps();

        int surfaceWidth = w + bounceOffset * 2;
        int surfaceHeight = h + bounceOffset;

        morphBitmap = Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);
        blurBitmap = Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);

        // Initialize with exact dimensions
        morphProcessor.initSurface(surfaceWidth, surfaceHeight);
        blurProcessor.initSurface(surfaceWidth, surfaceHeight);

        morphBgX = bounceOffset;
        morphBgY = bounceOffset;
    }

    private void recycleBitmaps() {
        if (morphBitmap != null) {
            morphBitmap.recycle();
            morphBitmap = null;
        }
        if (blurBitmap != null) {
            blurBitmap.recycle();
            blurBitmap = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blurProcessor.onAttach();
        morphProcessor.onAttach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurProcessor.onDetach();
        morphProcessor.onDetach();
        recycleBitmaps();
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.onDismissListener = listener;
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    private class ShareOption extends View {
        private ImageReceiver imageReceiver;
        private Paint avatarPaint;
        private Path clipPath;
        private AvatarDrawable bookmarkDrawable;
        private ValueAnimator touchScaleAnimator;
        private boolean isTouched;
        private AnimatorSet appearAnimator;
        private static final float TOUCH_SCALE = 1.11f;
        private ValueAnimator scaleAnimator;
        private float touchScale = 1f;
        private TLRPC.User user;

        public ShareOption(Context context) {
            super(context);
            imageReceiver = new ImageReceiver(this);
            imageReceiver.setRoundRadius(dp(36));

            // Initialize paint for avatar border if needed
            avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            avatarPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            clipPath = new Path();
            bookmarkDrawable = new AvatarDrawable();
            bookmarkDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
        }

        public void setTouched(boolean touched) {
            if (isTouched == touched) {
                return;
            }

            isTouched = touched;
            FileLog.d("ShareOption setTouched: " + touched + " scale=" + touchScale);

            if (appearAnimator != null && appearAnimator.isRunning()) {
                FileLog.d("ShareOption appear still running");
                return;
            }

            if (touchScaleAnimator != null) {
                touchScaleAnimator.cancel();
            }

            touchScaleAnimator = ValueAnimator.ofFloat(touchScale, touched ? TOUCH_SCALE : 1f);
            touchScaleAnimator.setDuration(150);
            touchScaleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            touchScaleAnimator.addUpdateListener(animation -> {
                touchScale = (float) animation.getAnimatedValue();
                FileLog.d("ShareOption scale now: " + touchScale);
                invalidate();
            });
            touchScaleAnimator.start();
        }

        public TLRPC.User getUser() {
            return user;
        }

        public void setUser(TLRPC.User user) {
            if (user != null) {
                this.user = user;
                // Set default fallback avatar color
                int color = AvatarDrawable.getColorForId(user.id);

                // Create fallback drawable
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setColor(color);
                avatarDrawable.setInfo(user);

                // Set user photo if exists, otherwise use fallback
                if (UserObject.isUserSelf(user)) {
                    imageReceiver.setImageBitmap(bookmarkDrawable);
                }
                else if (user.photo != null && user.photo.photo_small != null) {
                    imageReceiver.setForUserOrChat(
                            user,
                            avatarDrawable,
                            true // Allow animations
                    );
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
                imageReceiver.setRoundRadius(getMeasuredWidth() / 2);
                invalidate();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // Update circular clip path when size changes
            clipPath.reset();
            clipPath.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();

            if (touchScale != 1f) {
                float px = getWidth() / 2f;
                float py = getHeight() / 2f;
                canvas.scale(touchScale, touchScale, px, py);
            }

            canvas.clipPath(clipPath);
            imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
            imageReceiver.draw(canvas);

            canvas.restore();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (touchScaleAnimator != null) {
                touchScaleAnimator.cancel();
                touchScaleAnimator = null;
            }
            if (appearAnimator != null) {
                appearAnimator.cancel();
                appearAnimator = null;
            }
            if (scaleAnimator != null) {
                scaleAnimator.cancel();
                scaleAnimator = null;
            }
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

    }
}