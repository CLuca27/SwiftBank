package com.example.swiftbank.activities.login;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.views.ParticlesView;

public class BlockedUserActivity extends AppCompatActivity {

    private static final String SUPPORT_EMAIL = "support@swiftbank.ro";
    private static final String SUPPORT_PHONE = "+40800123456";

    private Button btnContactSupport;
    private Button btnBack;
    private ParticlesView particlesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnContactSupport = findViewById(R.id.btnContactSupport);
        btnBack = findViewById(R.id.btnBack);
        particlesView = findViewById(R.id.particlesView);
    }

    private void setupListeners() {
        btnContactSupport.setOnClickListener(v -> openContactOptions());
        btnBack.setOnClickListener(v -> finish());
    }

    private void openContactOptions() {
        // Opțiunea 1: Deschide email
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Cont blocat - Solicitare deblocare");

        if (emailIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(emailIntent);
        } else {
            // Fallback: Deschide telefon
            openPhoneDialer();
        }
    }

    private void openPhoneDialer() {
        Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
        phoneIntent.setData(Uri.parse("tel:" + SUPPORT_PHONE));
        startActivity(phoneIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}