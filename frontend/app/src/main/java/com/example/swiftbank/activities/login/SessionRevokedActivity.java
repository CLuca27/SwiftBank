package com.example.swiftbank.activities.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.views.ParticlesView;

public class SessionRevokedActivity extends AppCompatActivity {

    private Button btnLogin;
    private ParticlesView particlesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_revoked);

        btnLogin = findViewById(R.id.btnLogin);
        particlesView = findViewById(R.id.particlesView);

        btnLogin.setOnClickListener(v -> openLogin());
    }

    private void openLogin() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
