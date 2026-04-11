package com.example.swiftbank.activities.register;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.utils.SwiftBankDialog;

public abstract class BackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmation();
            }
        });
    }

    protected void showExitConfirmation() {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_block)
                .setTitle("Abandonezi înregistrarea?")
                .setMessage("Progresul va fi pierdut.")
                .setPrimaryButton("Nu, continuă", null)
                .setSecondaryButton("Da, renunță", v -> goToWelcome())
                .setCancelable(true)
                .show();
    }

    protected void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}