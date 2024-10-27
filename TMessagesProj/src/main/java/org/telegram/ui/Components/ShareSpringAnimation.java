package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.R;

public class ShareSpringAnimation {
    private float springStiffness = 800f;
    private float springDamping = 0.5f;
    private long animationDuration = 300;

    private float startScale = 1f;
    private float endScale = 0.8f;
    private float startAlpha = 1f;
    private float endAlpha = 0f;

    public void animateShareButton(View shareButton, View targetContainer, Runnable onComplete) {
        // Get starting position coordinates
        int[] startPosition = new int[2];
        shareButton.getLocationInWindow(startPosition);

        // Calculate target position (center-top of container)
        int[] targetPosition = new int[2];
        targetContainer.getLocationInWindow(targetPosition);

        float targetX = targetPosition[0] + targetContainer.getWidth() / 2f - shareButton.getWidth() / 2f;
        float targetY = targetPosition[1] - shareButton.getHeight();

        // Create translation animations using spring physics
        SpringAnimation translationX = new SpringAnimation(shareButton, DynamicAnimation.TRANSLATION_X);
        SpringAnimation translationY = new SpringAnimation(shareButton, DynamicAnimation.TRANSLATION_Y);
        SpringAnimation scaleX = new SpringAnimation(shareButton, DynamicAnimation.SCALE_X);
        SpringAnimation scaleY = new SpringAnimation(shareButton, DynamicAnimation.SCALE_Y);

        // Configure spring forces
        SpringForce springForceMove = new SpringForce()
                .setStiffness(springStiffness)
                .setDampingRatio(springDamping);

        SpringForce springForceScale = new SpringForce(endScale)
                .setStiffness(springStiffness)
                .setDampingRatio(springDamping);

        translationX.setSpring(springForceMove);
        translationY.setSpring(springForceMove);
        scaleX.setSpring(springForceScale);
        scaleY.setSpring(springForceScale);

        // Set target values
        translationX.animateToFinalPosition(targetX - startPosition[0]);
        translationY.animateToFinalPosition(targetY - startPosition[1]);

        // Add fade out animation
        ValueAnimator alphaAnim = ValueAnimator.ofFloat(startAlpha, endAlpha);
        alphaAnim.setDuration(animationDuration);
        alphaAnim.addUpdateListener(animation -> {
            shareButton.setAlpha((float) animation.getAnimatedValue());
        });

        // Optional ripple effect
        addRippleEffect(shareButton);

        // Start all animations
        translationX.start();
        translationY.start();
        scaleX.start();
        scaleY.start();
        alphaAnim.start();

        // Reset view after animation
        new Handler().postDelayed(() -> {
            shareButton.setTranslationX(0);
            shareButton.setTranslationY(0);
            shareButton.setScaleX(1);
            shareButton.setScaleY(1);
            shareButton.setAlpha(1);
            if (onComplete != null) {
                onComplete.run();
            }
        }, animationDuration);
    }

    private void addRippleEffect(View sourceView) {
        // Create ripple effect view
        View rippleView = new View(sourceView.getContext());
        ((ViewGroup) sourceView.getParent()).addView(rippleView);

        // Set ripple view properties
        rippleView.setBackground(sourceView.getContext().getResources().getDrawable(
                R.drawable.circle_ripple_background));

        // Position ripple at source view location
        rippleView.setTranslationX(sourceView.getX());
        rippleView.setTranslationY(sourceView.getY());

        // Animate ripple
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(rippleView, View.SCALE_X, 1f, 2f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(rippleView, View.SCALE_Y, 1f, 2f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(rippleView, View.ALPHA, 0.3f, 0f);

        AnimatorSet rippleAnim = new AnimatorSet();
        rippleAnim.playTogether(scaleX, scaleY, alpha);
        rippleAnim.setDuration(animationDuration);
        rippleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ((ViewGroup) rippleView.getParent()).removeView(rippleView);
            }
        });
        rippleAnim.start();
    }
}
