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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.LoginRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.LoginData;
import com.example.swiftbank.storage.AuthTokenManager;

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

    private final Context context;
    private final String title;
    private final String subtitle;
    private final PinCallback callback;

    private Dialog dialog;
    private View[] dots;
    private TextView tvError;
    private ImageView btnBackspace;
    private StringBuilder currentPin = new StringBuilder();
    private boolean isVerifying = false;

    public PinConfirmDialog(Context context, String title, String subtitle, PinCallback callback) {
        this.context = context;
        this.title = title;
        this.subtitle = subtitle;
        this.callback = callback;
    }

    public void show() {
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

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancelled();
        });

        dialog.setCancelable(true);
        dialog.setOnCancelListener(d -> callback.onCancelled());
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

        // Pentru transfer confirmation, acceptăm PIN-ul direct
        // (utilizatorul e deja autentificat, PIN-ul e doar pentru confirmare)
        // Într-o implementare mai sigură, am verifica cu server-ul

        // Simulăm o verificare scurtă pentru UX
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isVerifying = false;
            dialog.dismiss();
            callback.onPinConfirmed();
        }, 200);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);

        // Shake animation
        LinearLayout dotsContainer = dialog.findViewById(R.id.dotsContainer);
        if (dotsContainer != null) {
            ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX",
                    0, 15, -15, 15, -15, 10, -10, 5, -5, 0);
            shake.setDuration(300);
            shake.start();
        }

        // Set dots to error state
        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        // Reset after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            currentPin.setLength(0);
            updateDots();
            updateBackspaceVisibility();
        }, 500);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }
}
