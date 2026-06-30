package com.example.swiftbank.activities.register;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.adapters.AddressSuggestionAdapter;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.RegistrationData;
import com.example.swiftbank.api.dto.request.CheckRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CheckData;
import com.example.swiftbank.api.dto.response.data.success.PlaceDetailsData;
import com.example.swiftbank.api.dto.response.data.success.PlaceSuggestionData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.views.ParticlesView;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterDetailsActivity extends BackActivity {

    private static final String TAG = "RegisterDetailsActivity";
    private static final int AUTOCOMPLETE_DELAY = 300;

    // Views
    private ImageView btnBack;
    private ScrollView scrollView;
    private CardView suggestionsCard;
    private EditText etFirstName, etLastName, etCnp, etAddress;
    private TextView etStreet, etCity, etCounty, etPostalCode;
    private RecyclerView rvSuggestions;
    private LinearLayout addressFieldsContainer;
    private TextView tvError;
    private Button btnContinue;
    private View loadingOverlay;
    private ParticlesView particlesView;

    // Adapter
    private AddressSuggestionAdapter suggestionsAdapter;

    // Handler pentru debounce
    private final Handler autocompleteHandler = new Handler(Looper.getMainLooper());
    private Runnable autocompleteRunnable;

    // Data
    private RegistrationData registrationData;
    private boolean addressSelected = false;
    private boolean suppressAddressChange = false;
    private int lastScrollY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_details);

        getIntentData();
        initViews();
        setupListeners();
    }

    private void getIntentData() {
        registrationData = getIntent().getParcelableExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        scrollView = findViewById(R.id.scrollView);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etCnp = findViewById(R.id.etCnp);
        etAddress = findViewById(R.id.etAddress);
        etStreet = findViewById(R.id.etStreet);
        etCity = findViewById(R.id.etCity);
        etCounty = findViewById(R.id.etCounty);
        etPostalCode = findViewById(R.id.etPostalCode);
        rvSuggestions = findViewById(R.id.rvSuggestions);
        suggestionsCard = findViewById(R.id.suggestionsCard);
        addressFieldsContainer = findViewById(R.id.addressFieldsContainer);
        tvError = findViewById(R.id.tvError);
        btnContinue = findViewById(R.id.btnContinue);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        suggestionsAdapter = new AddressSuggestionAdapter(new AddressSuggestionAdapter.OnSuggestionClickListener() {
            @Override
            public void onSuggestionClick(PlaceSuggestionData suggestion) {
                hideSuggestions();
                hideKeyboard();

                suppressAddressChange = true;
                etAddress.setText(suggestion.getDescription());
                etAddress.setSelection(0);
                etAddress.setScrollX(0);
                etAddress.post(() -> {
                    etAddress.setSelection(0);
                    etAddress.setScrollX(0);
                });
                suppressAddressChange = false;

                fetchPlaceDetails(suggestion.getPlaceId());
            }
        });

        rvSuggestions.setLayoutManager(new LinearLayoutManager(this));
        rvSuggestions.setAdapter(suggestionsAdapter);
        rvSuggestions.setNestedScrollingEnabled(true);

        lastScrollY = scrollView.getScrollY();
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = scrollView.getScrollY();
            if (suggestionsCard.getVisibility() == View.VISIBLE && scrollY != lastScrollY) {
                hideSuggestions();
            }
            lastScrollY = scrollY;
        });

        updateContinueButton(false);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> showExitConfirmation());

        scrollView.setOnTouchListener((v, event) -> {
            if (suggestionsCard.getVisibility() == View.VISIBLE) {
                hideSuggestions();
            }
            return false;
        });

        etAddress.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDoneAction = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnterRelease = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;

            if (isDoneAction || isEnterRelease) {
                if (autocompleteRunnable != null) {
                    autocompleteHandler.removeCallbacks(autocompleteRunnable);
                }
                hideKeyboard();
                return true;
            }

            return false;
        });

        TextWatcher formWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
                updateContinueButton(isFormValid());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etFirstName.addTextChangedListener(formWatcher);
        etLastName.addTextChangedListener(formWatcher);

        etCnp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
                clearFieldError(etCnp);

                String cnp = s.toString().trim();
                if (cnp.length() == 13 && !isValidCnp(cnp)) {
                    showFieldError(etCnp, "CNP invalid");
                }

                updateContinueButton(isFormValid());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();

                if (suppressAddressChange) {
                    return;
                }

                if (addressSelected) {
                    addressSelected = false;
                    addressFieldsContainer.setVisibility(View.GONE);
                    clearAddressFields();
                }

                updateContinueButton(isFormValid());

                if (autocompleteRunnable != null) {
                    autocompleteHandler.removeCallbacks(autocompleteRunnable);
                }

                String input = s.toString().trim();
                if (input.length() >= 3) {
                    autocompleteRunnable = () -> fetchSuggestions(input);
                    autocompleteHandler.postDelayed(autocompleteRunnable, AUTOCOMPLETE_DELAY);
                } else {
                    hideSuggestions();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnContinue.setOnClickListener(v -> onContinueClicked());
    }

    private void fetchSuggestions(String input) {
        ApiClient.getPlacesService().autocomplete(input).enqueue(new Callback<ApiResponse<List<PlaceSuggestionData>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlaceSuggestionData>>> call, Response<ApiResponse<List<PlaceSuggestionData>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PlaceSuggestionData> suggestions = response.body().getData();
                    if (suggestions != null && !suggestions.isEmpty()) {
                        showSuggestions(suggestions);
                    } else {
                        hideSuggestions();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlaceSuggestionData>>> call, Throwable t) {
                Log.e(TAG, "Autocomplete error: " + t.getMessage());
            }
        });
    }

    private void showSuggestions(List<PlaceSuggestionData> suggestions) {
        suggestionsAdapter.setSuggestions(suggestions);

        etAddress.post(() -> {
            int[] location = new int[2];
            etAddress.getLocationInWindow(location);

            int yPos = location[1] + etAddress.getHeight() + (int) (8 * getResources().getDisplayMetrics().density);

            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) suggestionsCard.getLayoutParams();
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topMargin = yPos;

            int itemHeight = (int) (60 * getResources().getDisplayMetrics().density);
            int maxItems = Math.min(suggestions.size(), 3);
            params.height = (itemHeight * maxItems) + (int) (16 * getResources().getDisplayMetrics().density);

            suggestionsCard.setLayoutParams(params);
            suggestionsCard.setVisibility(View.VISIBLE);
            suggestionsCard.bringToFront();
        });
    }

    private void hideSuggestions() {
        suggestionsAdapter.clear();
        suggestionsCard.setVisibility(View.GONE);
    }
    private void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(etAddress.getWindowToken(), 0);
        }
        etAddress.clearFocus();
    }

    private void fetchPlaceDetails(String placeId) {
        showLoading(true);

        ApiClient.getPlacesService().getDetails(placeId).enqueue(new Callback<ApiResponse<PlaceDetailsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<PlaceDetailsData>> call, Response<ApiResponse<PlaceDetailsData>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    PlaceDetailsData details = response.body().getData();
                    fillAddressFields(details);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PlaceDetailsData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Place details error: " + t.getMessage());
                showError("Nu am putut obține detaliile adresei.");
            }
        });
    }

    private void fillAddressFields(PlaceDetailsData details) {
        addressSelected = true;

        etStreet.setText(details.getFullStreet() != null ? details.getFullStreet() : "");
        etCity.setText(details.getCity() != null ? details.getCity() : "");
        etCounty.setText(details.getCounty() != null ? details.getCounty().trim() : "");
        etPostalCode.setText(details.getPostalCode() != null ? details.getPostalCode() : "");

        addressFieldsContainer.setVisibility(View.VISIBLE);
        updateContinueButton(isFormValid());
    }

    private void clearAddressFields() {
        etStreet.setText("");
        etCity.setText("");
        etCounty.setText("");
        etPostalCode.setText("");
    }

    private boolean isFormValid() {
        return !getFirstName().isEmpty()
                && !getLastName().isEmpty()
                && isValidCnp(getCnp())
                && addressSelected
                && !getStreet().isEmpty()
                && !getCity().isEmpty()
                && !getCounty().isEmpty();
    }

    private boolean isValidCnp(String cnp) {
        if (cnp == null || !cnp.matches("\\d{13}")) {
            return false;
        }

        int s = Character.getNumericValue(cnp.charAt(0));
        int yy = Integer.parseInt(cnp.substring(1, 3));
        int mm = Integer.parseInt(cnp.substring(3, 5));
        int dd = Integer.parseInt(cnp.substring(5, 7));
        int jj = Integer.parseInt(cnp.substring(7, 9));
        int nnn = Integer.parseInt(cnp.substring(9, 12));
        int controlDigit = Character.getNumericValue(cnp.charAt(12));

        if (s < 1 || s > 8) {
            return false;
        }

        if (jj < 1 || jj > 52) {
            return false;
        }

        if (nnn < 1 || nnn > 999) {
            return false;
        }

        int fullYear;
        switch (s) {
            case 1:
            case 2:
                fullYear = 1900 + yy;
                break;
            case 5:
            case 6:
                fullYear = 2000 + yy;
                break;
            case 7:
            case 8:
                fullYear = (yy <= getCurrentYearLastTwoDigits()) ? 2000 + yy : 1900 + yy;
                break;
            case 3:
            case 4:
            default:
                return false;
        }

        if (fullYear < 1900 || fullYear > getCurrentYear()) {
            return false;
        }

        if (!isValidDate(fullYear, mm, dd)) {
            return false;
        }

        return hasValidCnpChecksum(cnp, controlDigit);
    }

    private boolean hasValidCnpChecksum(String cnp, int controlDigit) {
        final String controlKey = "279146358279";
        int sum = 0;

        for (int i = 0; i < 12; i++) {
            int cnpDigit = Character.getNumericValue(cnp.charAt(i));
            int keyDigit = Character.getNumericValue(controlKey.charAt(i));
            sum += cnpDigit * keyDigit;
        }

        int remainder = sum % 11;
        int expected = (remainder == 10) ? 1 : remainder;

        return expected == controlDigit;
    }

    private boolean isValidDate(int year, int month, int day) {
        if (month < 1 || month > 12) {
            return false;
        }

        Calendar calendar = new GregorianCalendar();
        calendar.setLenient(false);

        try {
            calendar.set(year, month - 1, day);
            calendar.getTime();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    private int getCurrentYearLastTwoDigits() {
        return getCurrentYear() % 100;
    }

    private String getFirstName() {
        return etFirstName.getText().toString().trim();
    }

    private String getLastName() {
        return etLastName.getText().toString().trim();
    }

    private String getCnp() {
        return etCnp.getText().toString().trim();
    }

    private String getStreet() {
        return etStreet.getText().toString().trim();
    }

    private String getCity() {
        return etCity.getText().toString().trim();
    }

    private String getCounty() {
        return etCounty.getText().toString().trim();
    }

    private String getPostalCode() {
        return etPostalCode.getText().toString().trim();
    }

    private void updateContinueButton(boolean enabled) {
        btnContinue.setEnabled(enabled);
        btnContinue.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        scrollToView(tvError);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void showFieldError(EditText editText, String errorMessage) {
        editText.setError(errorMessage);
    }

    private void clearFieldError(EditText editText) {
        editText.setError(null);
    }

    private void showCnpError(String message) {
        showFieldError(etCnp, message);
        scrollToView(etCnp);
    }

    private void scrollToView(View target) {
        target.post(() -> {
            int y = target.getTop();
            scrollView.smoothScrollTo(0, Math.max(y - dpToPx(24), 0));
            target.requestFocus();
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!show && isFormValid());
        btnContinue.setAlpha((!show && isFormValid()) ? 1f : 0.5f);
    }

    private void onContinueClicked() {
        hideError();
        clearFieldError(etCnp);

        if (getFirstName().isEmpty()) {
            showError("Completează prenumele.");
            scrollToView(etFirstName);
            return;
        }

        if (getLastName().isEmpty()) {
            showError("Completează numele.");
            scrollToView(etLastName);
            return;
        }

        if (!isValidCnp(getCnp())) {
            showCnpError("CNP invalid.");
            return;
        }

        if (!addressSelected || getStreet().isEmpty()) {
            showError("Selectează o adresă validă din listă.");
            scrollToView(etAddress);
            return;
        }

        Log.d(TAG, "Checking if CNP exists...");
        showLoading(true);

        CheckRequest request = new CheckRequest("cnp", getCnp());

        ApiClient.getAuthService().check(request).enqueue(new Callback<ApiResponse<CheckData>>() {
            @Override
            public void onResponse(Call<ApiResponse<CheckData>> call, Response<ApiResponse<CheckData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getData().isExists()) {
                        showLoading(false);
                        showError("CNP-ul este deja înregistrat.");
                        scrollToView(etCnp);
                    } else {
                        goToPassword();
                    }
                } else {
                    showLoading(false);
                    handleError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CheckData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showError("Eroare de conexiune.");
            }
        });
    }

    private void handleError(Response<ApiResponse<CheckData>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);
        if (error != null && error.getError() != null) {
            showError(error.getError().getMessage());
        } else {
            showError("A apărut o eroare.");
        }
    }

    private void goToPassword() {
        showLoading(false);

        registrationData.setFirstName(getFirstName());
        registrationData.setLastName(getLastName());
        registrationData.setCnp(getCnp());
        registrationData.setAddress(
                getStreet() + ", " + getPostalCode() + ", " + getCity() + ", " + getCounty()
        );

        Intent intent = new Intent(this, RegisterPinActivity.class);
        intent.putExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA, registrationData);
        startActivity(intent);

        Log.d(TAG, "Going to RegisterPasswordActivity");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (autocompleteRunnable != null) {
            autocompleteHandler.removeCallbacks(autocompleteRunnable);
        }

        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
