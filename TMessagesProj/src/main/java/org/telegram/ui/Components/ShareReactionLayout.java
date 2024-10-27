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
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class ShareReactionLayout extends FrameLayout {
    private Paint backgroundPaint;
    private Paint containerPaint;
    private RectF containerRect = new RectF();
    private float containerAlpha = 0f;
    private float appearProgress = 0f;
    private List<ShareOption> shareOptions = new ArrayList<>();
    private boolean dismissed;
    private float touchX, touchY;
    private View messageView;
    private Drawable shadowDrawable;
    private boolean isDragging;
    private View initialTouchedView;

    public ShareReactionLayout(Context context) {
        super(context);

        shadowDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.TRANSPARENT); // We don't need background dimming

        containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        containerPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        setWillNotDraw(false);
    }

    public void show(View messageView, ArrayList<TLRPC.User> users, float touchX, float touchY) {
        this.messageView = messageView;

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

        // Add bookmark option first
        ShareOption bookmarkOption = new ShareOption(getContext());
        bookmarkOption.setBookmark();
        addView(bookmarkOption);
        shareOptions.add(bookmarkOption);

        // Add user options
        for (TLRPC.User user : users) {
            ShareOption option = new ShareOption(getContext());
            option.setUser(user);
            addView(option);
            shareOptions.add(option);
        }

        animateAppear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                dismiss();
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
                // Optional: You can add distance check here if you want to dismiss when dragged too far
                if (isDragging) {
                    float dx = event.getX() - touchX;
                    float dy = event.getY() - touchY;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance > dp(40)) {  // Adjust threshold as needed
                        dismiss();
                        return true;
                    }
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
//        float width = dp(40 + shareOptions.size() * 44);
//        float height = dp(48);

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
            scaleAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                option.setScaleX(value);
                option.setScaleY(value);
                option.invalidate();
            });

            ValueAnimator alphaAnimator = ValueAnimator.ofFloat(0f, 1f);
            alphaAnimator.addUpdateListener(animation -> {
                option.setAlpha((float) animation.getAnimatedValue());
            });
            alphaAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT); // Keep alpha smooth

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
            set.start();
        }

        containerAnimator.start();
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

            option.layout(
                    left,
                    top,
                    left + itemSize,
                    top + itemSize
            );
        }
    }

    private class ShareOption extends View {
        private ImageReceiver imageReceiver;
        private float currentScale = 1f;
        private boolean isBookmark;
        private Paint avatarPaint;
        private Path clipPath;
        private AvatarDrawable bookmarkDrawable;

        public ShareOption(Context context) {
            super(context);
            imageReceiver = new ImageReceiver(this);
            imageReceiver.setRoundRadius(dp(36));

            // Initialize paint for avatar border if needed
            avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            avatarPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            clipPath = new Path();
//            bookmarkDrawable = context.getResources().getDrawable(R.drawable.chats_saved).mutate();
//            if (bookmarkDrawable != null) {
//                bookmarkDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), PorterDuff.Mode.MULTIPLY));
//            }
            bookmarkDrawable = new AvatarDrawable();
            bookmarkDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
        }

        public void setBookmark() {
            isBookmark = true;
            invalidate();
        }

        public void setUser(TLRPC.User user) {
            if (!isBookmark && user != null) {
                // Set default fallback avatar color
                int color = AvatarDrawable.getColorForId(user.id);

                // Create fallback drawable
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setColor(color);
                avatarDrawable.setInfo(user);

                // Set user photo if exists, otherwise use fallback
                if (user.photo != null && user.photo.photo_small != null) {
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

            if (bookmarkDrawable != null) {
                int drawableSize = Math.min(w, h);
                int left = (w - drawableSize) / 2;
                int top = (h - drawableSize) / 2;
                bookmarkDrawable.setBounds(
                        left,
                        top,
                        left + drawableSize,
                        top + drawableSize
                );
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float currentScaleX = getScaleX();
            float currentScaleY = getScaleY();

            canvas.save();
            canvas.scale(currentScaleX, currentScaleY, getWidth() / 2f, getHeight() / 2f);

            if (isBookmark) {
                canvas.clipPath(clipPath);
                canvas.drawCircle(getWidth()/2f, getHeight()/2f, getWidth()/2f, avatarPaint);
                bookmarkDrawable.draw(canvas);
            } else {
                canvas.clipPath(clipPath);
                imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
                imageReceiver.draw(canvas);
            }
            canvas.restore();

            // Force redraw if still animating
            if (currentScaleX != 1f || currentScaleY != 1f) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            animate()
                    .scaleX(pressed ? 0.85f : 1f)
                    .scaleY(pressed ? 0.85f : 1f)
                    .setDuration(150)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
        }
    }
}