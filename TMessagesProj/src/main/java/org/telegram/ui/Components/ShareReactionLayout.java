package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
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
    private Paint backgroundPaint;
    private Paint containerPaint;
    private RectF containerRect = new RectF();
    private float appearProgress = 0f;
    private List<ShareOption> shareOptions = new ArrayList<>();
    private boolean dismissed;
    private float touchX, touchY;
    private Drawable shadowDrawable;
    private ShareOption touchedOption;
    private MessageObject messageObject;
    private Function<TLRPC.User, Void> onShowBulletinCallback;

    public ShareReactionLayout(Context context) {
        super(context);

        shadowDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.TRANSPARENT); // We don't need background dimming

        containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        containerPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        setWillNotDraw(false);
    }

    public void show(View messageView, MessageObject message, ArrayList<TLRPC.User> users, float touchX, float touchY, Function<TLRPC.User, Void> showBulletin) {
        messageObject = message;
        onShowBulletinCallback = showBulletin;
        // Get absolute coordinates
        int[] location = new int[2];
        messageView.getLocationInWindow(location);

        int statusBarHeight = AndroidUtilities.statusBarHeight;

        // Calculate actual screen position
        this.touchX = location[0] + touchX;
        // Adjust for status bar
        this.touchY = location[1] + touchY - statusBarHeight;

        // Add the status bar height back for proper window positioning
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

        // Add user options
        for (TLRPC.User user : users) {
            ShareOption option = new ShareOption(getContext());
            option.setUser(user);
            addView(option);
            shareOptions.add(option);
        }

        animateAppear();
        checkForTouchedOption(touchX, touchY);
    }

    private void checkForTouchedOption(float x, float y) {
        FileLog.d("ShareLayout checking touch at " + x + "," + y);

        // Reset previous touch state
        if (touchedOption != null) {
            touchedOption.setTouched(false);
            touchedOption = null;
        }

        // Find which option is under the touch point
        for (ShareOption option : shareOptions) {
            int[] location = new int[2];
            option.getLocationOnScreen(location);

            float left = location[0];
            float top = location[1];
            float right = left + option.getWidth();
            float bottom = top + option.getHeight();

            boolean touched = x >= left && x <= right && y >= top && y <= bottom;

            if (touched) {
                touchedOption = option;
                touchedOption.setTouched(true);
                FileLog.d("ShareLayout found touched option at " + left + "," + top);
                break;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Always intercept touch events
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        FileLog.d("ShareLayout touch: action=" + event.getAction() +
                " rawXY=(" + rawX + "," + rawY + ")");

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                checkForTouchedOption(rawX, rawY);
                return true;

            case MotionEvent.ACTION_UP:
                if (touchedOption != null) {
                    TLRPC.User selectedUser = touchedOption.getUser();
                    if (selectedUser != null && messageObject != null) {
                        ArrayList<MessageObject> messages = new ArrayList<>();
                        if (messageObject.getGroupId() != 0) {
//                            MessageObject.GroupedMessages groupedMessages = MessagesController.getInstance(UserConfig.selectedAccount).getGroupedMessages().get(messageObject.getGroupId());
//                            if (groupedMessages != null) {
//                                messages.addAll(groupedMessages.messages);
//                            }
                        } else {
                            messages.add(messageObject);
                        }

                        // Send message to selected user
                        long dialogId = selectedUser.id;
                        SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(messages, dialogId, false, false, true, 0);
                        if (onShowBulletinCallback != null) {
                            onShowBulletinCallback.apply(selectedUser);
                        }
                    }
                    touchedOption.setTouched(false);
                    touchedOption = null;
                }
                dismiss();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (touchedOption != null) {
                    touchedOption.setTouched(false);
                    touchedOption = null;
                }
                return true;
        }
        return true;
    }

    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;

        AnimatorSet dismissSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();

        // Animate container
        ValueAnimator containerAnimator = ValueAnimator.ofFloat(1, 0);
        containerAnimator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animators.add(containerAnimator);

        // Animate share options
        int centerIndex = shareOptions.size() / 2;
        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            int distanceFromCenter = Math.abs(i - centerIndex);

            long delay;
            if (distanceFromCenter == 2) {
                delay = 0;
            } else if (distanceFromCenter == 1) {
                delay = 30;
            } else {
                delay = 60;
            }

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(option, View.SCALE_X, option.getScaleX(), 0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(option, View.SCALE_Y, option.getScaleY(), 0f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(option, View.ALPHA, option.getAlpha(), 0f);

            scaleX.setStartDelay(delay);
            scaleY.setStartDelay(delay);
            alpha.setStartDelay(delay);

            animators.add(scaleX);
            animators.add(scaleY);
            animators.add(alpha);
        }

        dismissSet.playTogether(animators);
        dismissSet.setDuration(100);
        dismissSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        dismissSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (getParent() != null) {
                    ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(ShareReactionLayout.this);
                }
            }
        });
        dismissSet.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int itemCount = shareOptions.size();
        int itemSize = dp(36); // Avatar size
        int itemSpacing = dp(4); // Space between items
        float sidePadding = dp(8); // Padding on left and right of container

        // Calculate exact width needed
        float width = (itemCount * itemSize) + // Total width of all avatars
                ((itemCount - 1) * itemSpacing) + // Total spacing between avatars
                (sidePadding * 2); // Left and right padding
        float height = dp(48);

        // Center on touch point
        containerRect.left = touchX - width / 2;
        containerRect.right = containerRect.left + width;

        // Position above touch point
        containerRect.bottom = touchY - dp(8);
        containerRect.top = containerRect.bottom - height;

        // Center on touch point
        containerRect.left = touchX - width / 2;
        containerRect.right = touchX + width / 2;

        // Position above touch point with fixed offset
        float offset = dp(40);
        containerRect.bottom = touchY - offset;
        containerRect.top = containerRect.bottom - height;

        // Add clip rect to prevent drawing outside bounds
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());

        // Screen bounds check - Adjust to prevent overflow
        if (containerRect.left < dp(16)) {
            containerRect.left = dp(16);
            containerRect.right = containerRect.left + width;
        } else if (containerRect.right > getWidth() - dp(16)) {
            containerRect.right = getWidth() - dp(16);
            containerRect.left = containerRect.right - width;
        }

        float scale = 0.5f + 0.5f * appearProgress;
        canvas.scale(scale, scale, touchX, touchY);

        // Draw shadow with tighter bounds
        if (shadowDrawable != null) {
            shadowDrawable.setBounds(
                    (int)containerRect.left - dp(2), // Reduced shadow padding
                    (int)containerRect.top - dp(2),
                    (int)containerRect.right + dp(2),
                    (int)containerRect.bottom + dp(2)
            );
            shadowDrawable.setAlpha((int)(255 * appearProgress));
            shadowDrawable.draw(canvas);
        }

        containerPaint.setAlpha((int)(255 * appearProgress));
        canvas.drawRoundRect(containerRect, dp(24), dp(24), containerPaint);

        canvas.restore();
    }
    private void animateAppear() {
        ValueAnimator containerAnimator = ValueAnimator.ofFloat(0, 1);
        containerAnimator.setDuration(250);
        containerAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        containerAnimator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
            requestLayout();
        });

        int centerIndex = shareOptions.size() / 2;
        long baseDelay = 50L;

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            option.animate().cancel();

            // Force initial state
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

            // Immediately apply initial scale
            option.setScaleX(initialScale);
            option.setScaleY(initialScale);
            option.invalidate();

            long delay;
            if (i == centerIndex) {
                delay = baseDelay;
            } else if (distanceFromCenter == 1) {
                delay = baseDelay * 2;
            } else {
                delay = baseDelay * 3;
            }

            final float finalInitialScale = initialScale;

            // Create animator set with listeners
            AnimatorSet set = new AnimatorSet();
            ValueAnimator scaleAnimator = ValueAnimator.ofFloat(initialScale, 1f);
            scaleAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                option.setScaleX(value);
                option.setScaleY(value);
                option.invalidate();
            });
            scaleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

            ValueAnimator alphaAnimator = ValueAnimator.ofFloat(0f, 1f);
            alphaAnimator.addUpdateListener(animation -> {
                option.setAlpha((float) animation.getAnimatedValue());
            });
            alphaAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);

            set.playTogether(scaleAnimator, alphaAnimator);
            set.setStartDelay(delay);
            set.setDuration(100);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    option.setScaleX(finalInitialScale);
                    option.setScaleY(finalInitialScale);
                    option.invalidate();
                }
            });

            // Store appear animator in the option
            option.appearAnimator = set;
            set.start();
        }

        containerAnimator.start();
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        // Add logging to see view positions
        int itemSize = dp(36);
        int spacing = dp(4);

        float startX = containerRect.left + dp(8);
        float centerY = containerRect.top + (containerRect.height() - itemSize) / 2;

        FileLog.d("ShareLayout onLayout: container at " + containerRect.left + "," + containerRect.top +
                " size " + containerRect.width() + "x" + containerRect.height());

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            int left = (int) (startX + i * (itemSize + spacing));
            int top = (int) centerY;

            option.layout(
                    left,
                    top,
                    left + itemSize,
                    top + itemSize
            );

            FileLog.d("ShareLayout laid out option " + i + " at " + left + "," + top);
        }
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