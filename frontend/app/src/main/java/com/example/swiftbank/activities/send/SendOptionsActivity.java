package com.example.swiftbank.activities.send;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.transfer.BeneficiariesActivity;
import com.example.swiftbank.activities.bills.BillCategoriesActivity;

public class SendOptionsActivity extends AppCompatActivity {

    private ImageView btnBack;
    private LinearLayout optionTransfer, optionBillPayment;

    private ActivityResultLauncher<Intent> transferLauncher;
    private ActivityResultLauncher<Intent> billPaymentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_options);

        initViews();
        setupLaunchers();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        optionTransfer = findViewById(R.id.optionTransfer);
        optionBillPayment = findViewById(R.id.optionBillPayment);
    }

    private void setupLaunchers() {
        transferLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );

        billPaymentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        optionTransfer.setOnClickListener(v -> {
            Intent intent = new Intent(this, BeneficiariesActivity.class);
            transferLauncher.launch(intent);
        });

        optionBillPayment.setOnClickListener(v -> {
            Intent intent = new Intent(this, BillCategoriesActivity.class);
            billPaymentLauncher.launch(intent);
        });
    }
}
