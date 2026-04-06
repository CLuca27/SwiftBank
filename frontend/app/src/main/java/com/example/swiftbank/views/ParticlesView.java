package com.example.swiftbank.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticlesView extends View {

    private List<Particle> particles;
    private Paint paint;
    private Random random;
    private boolean isRunning = true;

    private static final int PARTICLE_COUNT = 15;

    public ParticlesView(Context context) {
        super(context);
        init();
    }

    public ParticlesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParticlesView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        particles = new ArrayList<>();
        random = new Random();

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(0x66FFFFFF); // white 40% opacity
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initParticles();
    }

    private void initParticles() {
        particles.clear();
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) return;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.x = random.nextFloat() * width;
            p.y = random.nextFloat() * height;
            p.radius = 1 + random.nextFloat() * 1.5f;
            p.speed = 0.5f + random.nextFloat();
            p.alpha = 0.2f + random.nextFloat() * 0.3f;
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Particle p : particles) {
            paint.setAlpha((int) (p.alpha * 255));
            float radius = p.radius * getResources().getDisplayMetrics().density;
            canvas.drawCircle(p.x, p.y, radius, paint);
        }

        if (isRunning) {
            updateParticles();
            postInvalidateOnAnimation();
        }
    }

    private void updateParticles() {
        int height = getHeight();
        int width = getWidth();

        for (Particle p : particles) {
            p.y -= p.speed;

            if (p.y < -20) {
                p.y = height + 20;
                p.x = random.nextFloat() * width;
                p.radius = 1 + random.nextFloat() * 1.5f;
                p.speed = 0.5f + random.nextFloat();
                p.alpha = 0.2f + random.nextFloat() * 0.3f;
            }
        }
    }

    public void stopAnimation() {
        isRunning = false;
    }

    public void startAnimation() {
        isRunning = true;
        postInvalidateOnAnimation();
    }

    private static class Particle {
        float x, y;
        float radius;
        float speed;
        float alpha;
    }
}
