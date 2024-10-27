package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.ImageLocation;
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
    protected void onDraw(Canvas canvas) {
        float width = dp(40 + shareOptions.size() * 44);
        float height = dp(48);

        // Center horizontally on touch point
        containerRect.left = touchX - width / 2;
        containerRect.right = touchX + width / 2;

        // Position relative to touch point with fixed offset
        float offset = dp(40); // Fixed offset above touch point
        containerRect.bottom = touchY - offset;
        containerRect.top = containerRect.bottom - height;

        // Screen bounds checking
        if (containerRect.left < dp(16)) {
            containerRect.left = dp(16);
            containerRect.right = containerRect.left + width;
        } else if (containerRect.right > getWidth() - dp(16)) {
            containerRect.right = getWidth() - dp(16);
            containerRect.left = containerRect.right - width;
        }

        // Top screen bounds check
        if (containerRect.top < dp(16)) {
            // If there's not enough space above, show below the touch point
            containerRect.top = touchY + offset;
            containerRect.bottom = containerRect.top + height;
        }

        // Scale animation from touch point
        float scale = 0.5f + 0.5f * appearProgress;
        float centerX = touchX;
        float centerY = touchY;

        canvas.save();
        canvas.scale(scale, scale, centerX, centerY);

        // Draw shadow and container
        if (shadowDrawable != null) {
            shadowDrawable.setBounds(
                    (int)containerRect.left - dp(3),
                    (int)containerRect.top - dp(3),
                    (int)containerRect.right + dp(3),
                    (int)containerRect.bottom + dp(3)
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

        // Stagger animate options
        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            option.setAlpha(0f);
            option.setScaleX(0.5f);
            option.setScaleY(0.5f);

            option.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setStartDelay(50 + i * 40L) // Slightly longer delay between items
                    .setInterpolator(CubicBezierInterpolator.DEFAULT);
        }

        containerAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int itemSize = dp(40);
        int spacing = dp(4);

        float startX = containerRect.left + dp(8);
        float centerY = containerRect.top + containerRect.height() / 2;

        for (int i = 0; i < shareOptions.size(); i++) {
            ShareOption option = shareOptions.get(i);
            int left = (int) (startX + i * (itemSize + spacing));
            int top = (int) (centerY - itemSize / 2);

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
        private float scale = 1f;
        private boolean isBookmark;

        public ShareOption(Context context) {
            super(context);
            imageReceiver = new ImageReceiver(this);
        }

        public void setBookmark() {
            isBookmark = true;
        }

        public void setUser(TLRPC.User user) {
            if (!isBookmark) {
                imageReceiver.setImage(
                        ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL),
                        "40_40",
                        new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)),
                        0,
                        null,
                        null,
                        0
                );
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isBookmark) {
                // Draw bookmark icon
                Theme.dialogs_archiveAvatarDrawable.draw(canvas);
            } else {
                canvas.save();
                canvas.scale(scale, scale, getWidth()/2f, getHeight()/2f);
                imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
                imageReceiver.draw(canvas);
                canvas.restore();
            }
        }
    }

    // Rest of the touch handling, dismiss animation etc...
}