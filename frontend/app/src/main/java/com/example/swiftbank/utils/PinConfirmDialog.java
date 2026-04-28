package com.example.swiftbank.utils;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CardDetailsRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dialog pentru confirmarea transferurilor cu PIN.
 */
public class PinConfirmDialog {

    private static final int PIN_LENGTH = 6;

    public interface PinCallback {
        void onPinConfirmed();
        void onCancelled();
    }

    public interface PinInputCallback {
        void onPinEntered(String pin);
        void onCancelled();
    }

    private final Context context;
    private final String title;
    private final String subtitle;
    private final PinCallback callback;
    private final PinInputCallback inputCallback;

    private Dialog dialog;
    private View[] dots;
    private TextView tvError;
    private ImageView btnBackspace;
    private GridLayout numpad;
    private View btnCancel;
    private StringBuilder currentPin = new StringBuilder();
    private boolean isVerifying = false;
    private final Handler animationHandler = new Handler(Looper.getMainLooper());

    public PinConfirmDialog(Context context, String title, String subtitle, PinCallback callback) {
        this.context = context;
        this.title = title;
        this.subtitle = subtitle;
        this.callback = callback;
        this.inputCallback = null;
    }

    public PinConfirmDialog(Context context, String title, String subtitle, PinInputCallback inputCallback) {
        this.context = context;
        this.title = title;
        this.subtitle = subtitle;
        this.callback = null;
        this.inputCallback = inputCallback;
    }

    public void show() {
        // Reset state
        currentPin.setLength(0);
        isVerifying = false;

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_confirm, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Init views
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvError = view.findViewById(R.id.tvError);
        btnBackspace = view.findViewById(R.id.btnBackspace);

        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);

        // Init dots
        dots = new View[PIN_LENGTH];
        dots[0] = view.findViewById(R.id.dot1);
        dots[1] = view.findViewById(R.id.dot2);
        dots[2] = view.findViewById(R.id.dot3);
        dots[3] = view.findViewById(R.id.dot4);
        dots[4] = view.findViewById(R.id.dot5);
        dots[5] = view.findViewById(R.id.dot6);

        // Setup numpad
        int[] buttonIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                          R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        for (int i = 0; i < 10; i++) {
            final int digit = i;
            view.findViewById(buttonIds[i]).setOnClickListener(v -> onDigitPressed(digit));
        }

        btnBackspace.setOnClickListener(v -> onBackspacePressed());

        numpad = view.findViewById(R.id.numpad);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            stopLoadingAnimation();
            dialog.dismiss();
            if (callback != null) callback.onCancelled();
            if (inputCallback != null) inputCallback.onCancelled();
        });

        dialog.setCancelable(true);
        dialog.setOnCancelListener(d -> {
            if (callback != null) callback.onCancelled();
            if (inputCallback != null) inputCallback.onCancelled();
        });
        dialog.show();
    }

    private void onDigitPressed(int digit) {
        if (isVerifying || currentPin.length() >= PIN_LENGTH) return;

        hideError();
        currentPin.append(digit);
        updateDots();
        updateBackspaceVisibility();

        if (currentPin.length() == PIN_LENGTH) {
            new Handler(Looper.getMainLooper()).postDelayed(this::verifyPin, 150);
        }
    }

    private void onBackspacePressed() {
        if (isVerifying || currentPin.length() == 0) return;

        currentPin.deleteCharAt(currentPin.length() - 1);
        updateDots();
        updateBackspaceVisibility();
        hideError();
    }

    private void updateDots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            if (i < currentPin.length()) {
                dots[i].setBackgroundResource(R.drawable.pin_dot_filled);
            } else {
                dots[i].setBackgroundResource(R.drawable.pin_dot_empty);
            }
        }
    }

    private void updateBackspaceVisibility() {
        if (currentPin.length() > 0) {
            btnBackspace.setEnabled(true);
            btnBackspace.animate().alpha(1f).setDuration(150).start();
        } else {
            btnBackspace.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> btnBackspace.setEnabled(false)).start();
        }
    }

    private void verifyPin() {
        isVerifying = true;
        String pin = currentPin.toString();

        if (inputCallback != null) {
            // Pentru PinInputCallback, pornim animația și așteptăm răspuns extern
            startLoadingAnimation();
            setNumpadEnabled(false);
            inputCallback.onPinEntered(pin);
        } else if (callback != null) {
            // Pentru PinCallback, verificăm PIN-ul cu API-ul
            startLoadingAnimation();
            setNumpadEnabled(false);

            ApiClient.getUserService().verifyPin(new CardDetailsRequest(pin))
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                        if (dialog == null || !dialog.isShowing()) return;

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            // PIN corect
                            stopLoadingAnimation();
                            isVerifying = false;
                            dialog.dismiss();
                            callback.onPinConfirmed();
                        } else if (response.code() >= 500) {
                            // Eroare server
                            dismiss();
                            SwiftBankDialog.showServerErrorDialog(context, v -> show());
                        } else {
                            // PIN incorect
                            showError("PIN incorect");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        if (dialog == null || !dialog.isShowing()) return;
                        dismiss();
                        SwiftBankDialog.showServerErrorDialog(context, v -> show());
                    }
                });
        }
    }

    private void startLoadingAnimation() {
        if (dots == null) return;

        // Animație de "dans" pentru puncte - fiecare punct se mărește și micșorează în secvență
        Runnable animateDots = new Runnable() {
            int currentDot = 0;

            @Override
            public void run() {
                if (!isVerifying || dialog == null || !dialog.isShowing()) return;

                for (int i = 0; i < PIN_LENGTH; i++) {
                    float scale = (i == currentDot) ? 1.3f : 1.0f;
                    dots[i].animate()
                            .scaleX(scale)
                            .scaleY(scale)
                            .setDuration(150)
                            .start();
                }

                currentDot = (currentDot + 1) % PIN_LENGTH;
                animationHandler.postDelayed(this, 150);
            }
        };

        animationHandler.post(animateDots);
    }

    private void stopLoadingAnimation() {
        animationHandler.removeCallbacksAndMessages(null);

        if (dots != null) {
            for (View dot : dots) {
                dot.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
        }
    }

    private void setNumpadEnabled(boolean enabled) {
        if (numpad != null) {
            numpad.setAlpha(enabled ? 1f : 0.5f);
            for (int i = 0; i < numpad.getChildCount(); i++) {
                numpad.getChildAt(i).setEnabled(enabled);
            }
        }
        if (btnBackspace != null) {
            btnBackspace.setEnabled(enabled && currentPin.length() > 0);
        }
    }

    public void showError(String message) {
        if (dialog == null || !dialog.isShowing()) return;

        stopLoadingAnimation();
        setNumpadEnabled(true);

        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);

        LinearLayout dotsContainer = dialog.findViewById(R.id.dotsContainer);
        if (dotsContainer != null) {
            ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX",
                    0, 15, -15, 15, -15, 10, -10, 5, -5, 0);
            shake.setDuration(300);
            shake.start();
        }

        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            currentPin.setLength(0);
            updateDots();
            updateBackspaceVisibility();
            isVerifying = false;
        }, 500);
    }

    public void dismiss() {
        stopLoadingAnimation();
        isVerifying = false;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }
}
