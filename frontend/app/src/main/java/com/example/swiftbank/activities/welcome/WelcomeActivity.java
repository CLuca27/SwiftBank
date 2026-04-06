package com.example.swiftbank.activities.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.login.IdentifyActivity;
import com.example.swiftbank.activities.register.RegisterPhoneActivity;
import com.example.swiftbank.views.ParticlesView;

public class WelcomeActivity extends AppCompatActivity {

    private Button btnLogin;
    private Button btnRegister;
    private ParticlesView particlesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Initialize views
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        particlesView = findViewById(R.id.particlesView);

        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, IdentifyActivity.class);
            startActivity(intent);
        });

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterPhoneActivity.class);
            startActivity(intent);
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}