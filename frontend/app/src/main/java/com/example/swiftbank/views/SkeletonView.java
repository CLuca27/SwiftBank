package com.example.swiftbank.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;

/**
 * SkeletonView - Un view reutilizabil pentru skeleton loading cu efect shimmer.
 *
 * Utilizare in XML:
 * <com.example.swiftbank.views.SkeletonView
 *     android:layout_width="100dp"
 *     android:layout_height="20dp"
 *     app:skeleton_radius="8dp"
 *     app:skeleton_color="#33FFFFFF"
 *     app:skeleton_shimmer_color="#66FFFFFF"
 *     app:skeleton_duration="1000" />
 */
public class SkeletonView extends View {

    private Paint basePaint;
    private Paint shimmerPaint;
    private RectF rect;

    private float cornerRadius = 8f;
    private int baseColor;
    private int shimmerColor;
    private int animationDuration = 1000;

    private float shimmerPosition = -1f;
    private ValueAnimator animator;
    private boolean isAnimating = false;

    public SkeletonView(Context context) {
        super(context);
        init(null);
    }

    public SkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SkeletonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        int defaultBaseColor = ContextCompat.getColor(getContext(), R.color.surface_color);
        int defaultShimmerColor = ContextCompat.getColor(getContext(), R.color.divider_color);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SkeletonView);
            cornerRadius = a.getDimension(R.styleable.SkeletonView_skeleton_radius, 8f * getResources().getDisplayMetrics().density);
            baseColor = a.getColor(R.styleable.SkeletonView_skeleton_color, defaultBaseColor);
            shimmerColor = a.getColor(R.styleable.SkeletonView_skeleton_shimmer_color, defaultShimmerColor);
            animationDuration = a.getInt(R.styleable.SkeletonView_skeleton_duration, 1000);
            a.recycle();
        } else {
            cornerRadius = 8f * getResources().getDisplayMetrics().density;
            baseColor = defaultBaseColor;
            shimmerColor = defaultShimmerColor;
        }

        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(baseColor);
        basePaint.setStyle(Paint.Style.FILL);

        shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shimmerPaint.setStyle(Paint.Style.FILL);

        rect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        updateShimmerGradient();
    }

    private void updateShimmerGradient() {
        if (getWidth() <= 0) return;

        float shimmerWidth = getWidth() * 0.5f;
        float start = shimmerPosition * getWidth() - shimmerWidth;
        float end = shimmerPosition * getWidth() + shimmerWidth;

        LinearGradient gradient = new LinearGradient(
            start, 0, end, 0,
            new int[]{baseColor, shimmerColor, baseColor},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        shimmerPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Desenează baza
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, basePaint);

        // Desenează shimmer-ul peste
        if (isAnimating) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shimmerPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startShimmer();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }

    public void startShimmer() {
        if (animator != null && animator.isRunning()) return;

        isAnimating = true;
        animator = ValueAnimator.ofFloat(-0.5f, 1.5f);
        animator.setDuration(animationDuration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            shimmerPosition = (float) animation.getAnimatedValue();
            updateShimmerGradient();
            invalidate();
        });
        animator.start();
    }

    public void stopShimmer() {
        isAnimating = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        invalidate();
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }

    public void setBaseColor(int color) {
        this.baseColor = color;
        basePaint.setColor(color);
        invalidate();
    }

    public void setShimmerColor(int color) {
        this.shimmerColor = color;
        updateShimmerGradient();
        invalidate();
    }

    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
        if (animator != null && animator.isRunning()) {
            stopShimmer();
            startShimmer();
        }
    }
}
