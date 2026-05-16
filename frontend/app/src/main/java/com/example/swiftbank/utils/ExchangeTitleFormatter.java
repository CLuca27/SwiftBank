package com.example.swiftbank.utils;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public final class ExchangeTitleFormatter {
    private ExchangeTitleFormatter() {
    }

    public static CharSequence format(String title, float density) {
        if (title == null) {
            return "";
        }

        int arrowIndex = title.indexOf('\u2192');
        if (arrowIndex < 0) {
            return title;
        }

        SpannableString formattedTitle = new SpannableString(title);
        int offsetPx = -Math.max(1, Math.round(1.5f * density));
        formattedTitle.setSpan(
                new BaselineOffsetSpan(offsetPx),
                arrowIndex,
                arrowIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return formattedTitle;
    }

    private static final class BaselineOffsetSpan extends MetricAffectingSpan {
        private final int offsetPx;

        private BaselineOffsetSpan(int offsetPx) {
            this.offsetPx = offsetPx;
        }

        @Override
        public void updateMeasureState(TextPaint textPaint) {
            textPaint.baselineShift += offsetPx;
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.baselineShift += offsetPx;
        }
    }
}
