package com.example.swiftbank.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SkeletonLayout - Container care gestionează skeleton loading.
 *
 * Utilizare:
 * 1. Pune SkeletonLayout ca wrapper
 * 2. Primul copil = skeleton view(s)
 * 3. Al doilea copil = conținutul real
 *
 * Sau folosește programatic cu setSkeletonView() și setContentView()
 *
 * Exemplu XML:
 * <com.example.swiftbank.views.SkeletonLayout
 *     android:id="@+id/skeletonLayout"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content">
 *
 *     <!-- Skeleton (primul copil) -->
 *     <LinearLayout android:id="@+id/skeleton">...</LinearLayout>
 *
 *     <!-- Content (al doilea copil) -->
 *     <LinearLayout android:id="@+id/content">...</LinearLayout>
 *
 * </com.example.swiftbank.views.SkeletonLayout>
 *
 * În cod: skeletonLayout.showContent() sau skeletonLayout.showSkeleton()
 */
public class SkeletonLayout extends FrameLayout {

    private View skeletonView;
    private View contentView;
    private boolean isShowingSkeleton = true;
    private int animationDuration = 200;

    public SkeletonLayout(@NonNull Context context) {
        super(context);
    }

    public SkeletonLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SkeletonLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Primul copil = skeleton, al doilea = content
        if (getChildCount() >= 2) {
            skeletonView = getChildAt(0);
            contentView = getChildAt(1);

            // Inițial arată skeleton-ul
            skeletonView.setVisibility(VISIBLE);
            contentView.setVisibility(GONE);
            contentView.setAlpha(0f);
        }
    }

    public void setSkeletonView(View skeleton) {
        this.skeletonView = skeleton;
    }

    public void setContentView(View content) {
        this.contentView = content;
    }

    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
    }

    /**
     * Arată skeleton-ul cu animație
     */
    public void showSkeleton() {
        if (isShowingSkeleton) return;
        isShowingSkeleton = true;

        if (skeletonView == null || contentView == null) return;

        // Fade out content, fade in skeleton
        contentView.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .withEndAction(() -> {
                contentView.setVisibility(GONE);
                skeletonView.setVisibility(VISIBLE);
                skeletonView.setAlpha(0f);
                skeletonView.animate()
                    .alpha(1f)
                    .setDuration(animationDuration)
                    .start();
                startSkeletonAnimations(skeletonView);
            })
            .start();
    }

    /**
     * Arată conținutul real cu animație
     */
    public void showContent() {
        if (!isShowingSkeleton) return;
        isShowingSkeleton = false;

        if (skeletonView == null || contentView == null) return;

        // Oprește animațiile skeleton
        stopSkeletonAnimations(skeletonView);

        // Fade out skeleton, fade in content
        skeletonView.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .withEndAction(() -> {
                skeletonView.setVisibility(GONE);
                contentView.setVisibility(VISIBLE);
                contentView.setAlpha(0f);
                contentView.animate()
                    .alpha(1f)
                    .setDuration(animationDuration)
                    .start();
            })
            .start();
    }

    /**
     * Arată conținutul instant (fără animație)
     */
    public void showContentInstant() {
        isShowingSkeleton = false;
        if (skeletonView != null) {
            stopSkeletonAnimations(skeletonView);
            skeletonView.setVisibility(GONE);
        }
        if (contentView != null) {
            contentView.setVisibility(VISIBLE);
            contentView.setAlpha(1f);
        }
    }

    /**
     * Verifică dacă skeleton-ul e vizibil
     */
    public boolean isShowingSkeleton() {
        return isShowingSkeleton;
    }

    private void startSkeletonAnimations(View view) {
        if (view instanceof SkeletonView) {
            ((SkeletonView) view).startShimmer();
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                startSkeletonAnimations(group.getChildAt(i));
            }
        }
    }

    private void stopSkeletonAnimations(View view) {
        if (view instanceof SkeletonView) {
            ((SkeletonView) view).stopShimmer();
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                stopSkeletonAnimations(group.getChildAt(i));
            }
        }
    }
}
