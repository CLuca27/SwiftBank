package com.example.swiftbank.activities.settings;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.UpdateProfileRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.ProfileData;
import com.example.swiftbank.utils.SwiftBankDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ImageView btnBack;
    private CardView cardInitials, cardPhoto, btnChangePhoto;
    private TextView tvInitials, tvFullName, tvMemberSince;
    private TextView tvFirstName, tvLastName, tvEmail, tvPhone, tvBirthDate, tvAddress;
    private LinearLayout fieldFirstName, fieldLastName;
    private LinearLayout skeletonContainer, contentContainer;
    private ImageView ivProfilePhoto;

    private ProfileData currentProfile;
    private Uri cameraPhotoUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Uri imageUri = null;

                    if (result.getData() != null && result.getData().getData() != null) {
                        imageUri = result.getData().getData();
                    } else if (cameraPhotoUri != null) {
                        imageUri = cameraPhotoUri;
                    }

                    if (imageUri != null) {
                        handleImageSelected(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        setupListeners();
        loadProfile();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        cardInitials = findViewById(R.id.cardInitials);
        cardPhoto = findViewById(R.id.cardPhoto);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        tvInitials = findViewById(R.id.tvInitials);
        tvFullName = findViewById(R.id.tvFullName);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        tvFirstName = findViewById(R.id.tvFirstName);
        tvLastName = findViewById(R.id.tvLastName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvBirthDate = findViewById(R.id.tvBirthDate);
        tvAddress = findViewById(R.id.tvAddress);
        fieldFirstName = findViewById(R.id.fieldFirstName);
        fieldLastName = findViewById(R.id.fieldLastName);
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        skeletonContainer = findViewById(R.id.skeletonContainer);
        contentContainer = findViewById(R.id.contentContainer);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnChangePhoto.setOnClickListener(v -> openImagePicker());

        fieldFirstName.setOnClickListener(v -> showEditDialog("Prenume", tvFirstName.getText().toString(), "first_name"));
        fieldLastName.setOnClickListener(v -> showEditDialog("Nume", tvLastName.getText().toString(), "last_name"));
    }

    private void openImagePicker() {
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        try {
            File photoFile = new File(getCacheDir(), "profile_photo_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        } catch (Exception e) {
            cameraPhotoUri = null;
        }

        Intent chooserIntent = Intent.createChooser(galleryIntent, "Selectează poza de profil");

        List<Intent> extraIntents = new ArrayList<>();
        if (cameraPhotoUri != null) {
            extraIntents.add(cameraIntent);
        }

        if (!extraIntents.isEmpty()) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Intent[0]));
        }

        imagePickerLauncher.launch(chooserIntent);
    }

    private void loadProfile() {
        ApiClient.getUserService().getProfile().enqueue(new Callback<ApiResponse<ProfileData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ProfileData>> call, Response<ApiResponse<ProfileData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    currentProfile = response.body().getData();
                    displayProfile(currentProfile);
                } else {
                    Toast.makeText(ProfileActivity.this, "Eroare la încărcarea profilului", Toast.LENGTH_SHORT).show();
                    showContent();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ProfileData>> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                showContent();
            }
        });
    }

    private void showContent() {
        skeletonContainer.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    private void displayProfile(ProfileData profile) {
        String firstName = profile.getFirstName() != null ? profile.getFirstName() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName() : "";

        tvFirstName.setText(firstName);
        tvLastName.setText(lastName);
        tvFullName.setText(firstName + " " + lastName);

        String initials = "";
        if (!firstName.isEmpty()) initials += firstName.charAt(0);
        if (!lastName.isEmpty()) initials += lastName.charAt(0);
        tvInitials.setText(initials.toUpperCase());

        tvEmail.setText(profile.getEmail() != null ? profile.getEmail() : "-");
        tvPhone.setText(formatPhone(profile.getPhone()));
        tvBirthDate.setText(formatDate(profile.getBirthDate()));
        tvAddress.setText(profile.getAddress() != null && !profile.getAddress().isEmpty()
                ? profile.getAddress() : "-");

        tvMemberSince.setText("Membru din " + formatMemberDate(profile.getCreatedAt()));

        String photoBase64 = profile.getProfilePhoto();
        if (photoBase64 != null && !photoBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(photoBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    ivProfilePhoto.setImageBitmap(bitmap);
                    cardPhoto.setVisibility(View.VISIBLE);
                    cardInitials.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                // Keep showing initials
            }
        }

        showContent();
    }

    private String formatPhone(String phone) {
        if (phone == null || phone.isEmpty()) return "-";
        if (phone.startsWith("+40") && phone.length() == 12) {
            return phone.substring(0, 3) + " " + phone.substring(3, 6) + " " + phone.substring(6, 9) + " " + phone.substring(9);
        }
        return phone;
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "-";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("ro"));
            Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatMemberDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            inputFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM yyyy", new Locale("ro"));
            Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private void handleImageSelected(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) inputStream.close();

            if (bitmap == null) {
                Toast.makeText(this, "Imaginea nu a putut fi procesată", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap scaledBitmap = scaleBitmap(bitmap, 400);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            cardPhoto.setVisibility(View.VISIBLE);
            cardInitials.setVisibility(View.GONE);
            ivProfilePhoto.setImageBitmap(scaledBitmap);

            uploadProfilePhoto(base64Image);

        } catch (Exception e) {
            Toast.makeText(this, "Imaginea nu a putut fi încărcată", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap scaleBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        if (scale >= 1) return original;
        return Bitmap.createScaledBitmap(original,
                (int) (width * scale), (int) (height * scale), true);
    }

    private void uploadProfilePhoto(String base64Image) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setProfilePhoto(base64Image);

        ApiClient.getUserService().updateProfile(request).enqueue(new Callback<ApiResponse<ProfileData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ProfileData>> call, Response<ApiResponse<ProfileData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this, "Poza actualizată", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ProfileActivity.this, "Eroare la salvarea pozei", Toast.LENGTH_SHORT).show();
                    cardPhoto.setVisibility(View.GONE);
                    cardInitials.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ProfileData>> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                cardPhoto.setVisibility(View.GONE);
                cardInitials.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showEditDialog(String fieldName, String currentValue, String fieldKey) {
        EditText input = new EditText(this);
        input.setText(currentValue);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSelection(currentValue.length());
        input.setPadding(48, 32, 48, 32);
        input.setTextColor(getResources().getColor(R.color.white, null));
        input.setHintTextColor(getResources().getColor(R.color.white_50, null));
        input.setBackgroundResource(R.drawable.bg_edit_text_dialog);

        new SwiftBankDialog(this)
                .setTitle("Editează " + fieldName.toLowerCase())
                .setCustomView(input)
                .setPrimaryButton("Salvează", v -> {
                    String newValue = input.getText().toString().trim();
                    if (!newValue.isEmpty() && !newValue.equals(currentValue)) {
                        updateProfileField(fieldKey, newValue);
                    }
                })
                .setSecondaryButton("Anulează", null)
                .show();

        input.requestFocus();
    }

    private void updateProfileField(String fieldKey, String value) {
        UpdateProfileRequest request = new UpdateProfileRequest();

        switch (fieldKey) {
            case "first_name":
                request.setFirstName(value);
                break;
            case "last_name":
                request.setLastName(value);
                break;
        }

        ApiClient.getUserService().updateProfile(request).enqueue(new Callback<ApiResponse<ProfileData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ProfileData>> call, Response<ApiResponse<ProfileData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    currentProfile = response.body().getData();
                    displayProfile(currentProfile);
                    Toast.makeText(ProfileActivity.this, "Profil actualizat", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ProfileActivity.this, "Eroare la salvare", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ProfileData>> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
