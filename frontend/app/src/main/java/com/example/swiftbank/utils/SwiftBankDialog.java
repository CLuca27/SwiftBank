package com.example.swiftbank.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.swiftbank.R;

public class SwiftBankDialog {

    private final Dialog dialog;
    private final ImageView ivIcon;
    private final TextView tvTitle;
    private final TextView tvMessage;
    private final FrameLayout customViewContainer;
    private final Button btnPrimary;
    private final Button btnSecondary;

    public SwiftBankDialog(Context context) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_swiftbank, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        ivIcon = view.findViewById(R.id.ivDialogIcon);
        tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvMessage = view.findViewById(R.id.tvDialogMessage);
        customViewContainer = view.findViewById(R.id.customViewContainer);
        btnPrimary = view.findViewById(R.id.btnDialogPrimary);
        btnSecondary = view.findViewById(R.id.btnDialogSecondary);

        dialog.setCancelable(false);
    }

    public SwiftBankDialog setIcon(int iconRes) {
        ivIcon.setImageResource(iconRes);
        return this;
    }

    public SwiftBankDialog setTitle(String title) {
        tvTitle.setText(title);
        return this;
    }

    public SwiftBankDialog setMessage(String message) {
        tvMessage.setText(message);
        return this;
    }

    public SwiftBankDialog setCustomView(View view) {
        tvMessage.setVisibility(View.GONE);
        customViewContainer.setVisibility(View.VISIBLE);
        customViewContainer.removeAllViews();
        customViewContainer.addView(view);
        return this;
    }

    public SwiftBankDialog setPrimaryButton(String text, View.OnClickListener listener) {
        btnPrimary.setText(text);
        btnPrimary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(v);
            }
            dialog.dismiss();
        });
        return this;
    }

    public SwiftBankDialog setSecondaryButton(String text, View.OnClickListener listener) {
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(text);
        btnSecondary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(v);
            }
            dialog.dismiss();
        });
        return this;
    }

    public SwiftBankDialog setCancelable(boolean cancelable) {
        dialog.setCancelable(cancelable);
        return this;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    // Convenience methods for common dialogs

    public static void showNoNetworkDialog(Context context, View.OnClickListener onRetry) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_wifi_off)
                .setTitle("Fără conexiune")
                .setMessage("Verifică conexiunea la internet și încearcă din nou.")
                .setPrimaryButton("Reîncearcă", onRetry)
                .show();
    }

    public static void showServerErrorDialog(Context context, View.OnClickListener onRetry) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_server_error)
                .setTitle("A apărut o problemă")
                .setMessage("Serverul nu răspunde. Te rugăm să încerci din nou.")
                .setPrimaryButton("Reîncearcă", onRetry)
                .show();
    }

    public static void showErrorDialog(Context context, String message) {
        showErrorDialog(context, "Eroare", message);
    }

    public static void showErrorDialog(Context context, String title, String message) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_block)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("OK", null)
                .show();
    }

    public static void showSuccessDialog(Context context, String title, String message, View.OnClickListener onOk) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_check_circle)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("OK", onOk)
                .show();
    }

    public static void showInfoDialog(Context context, String title, String message) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_info)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("OK", null)
                .show();
    }

    public static void showConfirmDialog(Context context, String title, String message,
                                         String confirmText, String cancelText, Runnable onConfirm) {
        new SwiftBankDialog(context)
                .setIcon(R.drawable.ic_info)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton(confirmText, v -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setSecondaryButton(cancelText, null)
                .show();
    }
}
