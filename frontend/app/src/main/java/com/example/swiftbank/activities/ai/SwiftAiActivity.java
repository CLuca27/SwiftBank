package com.example.swiftbank.activities.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.AiChatRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AiChatData;
import com.example.swiftbank.views.ParticlesView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SwiftAiActivity extends AppCompatActivity {

    private static final int MAX_LOCAL_HISTORY_ITEMS = 12;

    private ScrollView chatScroll;
    private LinearLayout messagesContainer;
    private LinearLayout quickQuestionsContainer;
    private HorizontalScrollView quickQuestionsScroll;
    private EditText etMessage;
    private ImageView btnSendMessage;
    private ParticlesView particlesView;
    private View bottomPanel;
    private int baseChatPaddingBottom;
    private int baseBottomPanelPaddingBottom;

    private final List<AiChatRequest.ChatMessage> history = new ArrayList<>();
    private Call<ApiResponse<AiChatData>> currentCall;
    private View typingBubble;
    private final List<ObjectAnimator> typingDotAnimators = new ArrayList<>();
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swift_ai);

        configureSystemBars();
        initViews();
        setupKeyboardInsets();
        setupListeners();
        setupQuickQuestions();
        addAssistantMessage("Salut, sunt Swift AI. Te pot ajuta s\u0103 \u00een\u021belegi mai bine activitatea ta din SwiftBank \u0219i s\u0103 g\u0103se\u0219ti rapid informa\u021biile de care ai nevoie. Din motive de siguran\u021b\u0103, nu pot afi\u0219a date sensibile \u00een chat, dar \u00ee\u021bi pot oferi explica\u021bii \u0219i sugestii generale.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (particlesView != null) {
            particlesView.startAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }

    @Override
    protected void onDestroy() {
        if (currentCall != null) {
            currentCall.cancel();
        }
        super.onDestroy();
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_dark));
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }

    private void initViews() {
        chatScroll = findViewById(R.id.chatScroll);
        messagesContainer = findViewById(R.id.messagesContainer);
        quickQuestionsContainer = findViewById(R.id.quickQuestionsContainer);
        quickQuestionsScroll = findViewById(R.id.quickQuestionsScroll);
        etMessage = findViewById(R.id.etMessage);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        particlesView = findViewById(R.id.particlesView);
        bottomPanel = findViewById(R.id.bottomPanel);
        baseChatPaddingBottom = chatScroll.getPaddingBottom();
        baseBottomPanelPaddingBottom = bottomPanel.getPaddingBottom();
    }


    private void setupKeyboardInsets() {
        View rootLayout = findViewById(R.id.rootLayout);
        rootLayout.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsets.Type.ime());
            Insets navigationInsets = insets.getInsets(WindowInsets.Type.navigationBars());
            boolean keyboardVisible = imeInsets.bottom > dp(80);
            int keyboardLift = keyboardVisible ? imeInsets.bottom : 0;

            bottomPanel.setTranslationY(-keyboardLift);
            quickQuestionsScroll.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);

            int panelHeight = bottomPanel.getHeight() > 0 ? bottomPanel.getHeight() : dp(72);
            int extraChatSpace = keyboardVisible ? keyboardLift + panelHeight + dp(12) : 0;
            chatScroll.setPadding(
                    chatScroll.getPaddingLeft(),
                    chatScroll.getPaddingTop(),
                    chatScroll.getPaddingRight(),
                    baseChatPaddingBottom + extraChatSpace
            );

            int bottomPadding = baseBottomPanelPaddingBottom + (keyboardVisible ? 0 : navigationInsets.bottom);
            bottomPanel.setPadding(
                    bottomPanel.getPaddingLeft(),
                    bottomPanel.getPaddingTop(),
                    bottomPanel.getPaddingRight(),
                    bottomPadding
            );

            scrollToBottom();
            return insets;
        });
        rootLayout.requestApplyInsets();
    }
    private void setupListeners() {
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        btnSendMessage.setOnClickListener(v -> sendCurrentMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                hideKeyboard();
                etMessage.clearFocus();
                return true;
            }
            return false;
        });


        etMessage.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                sendCurrentMessage();
                hideKeyboard();
                etMessage.clearFocus();
                return true;
            }
            return false;
        });
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        updateSendState();
    }

    private void setupQuickQuestions() {
        String[] questions = new String[] {
                "Unde pot economisi luna asta?",
                "Pe ce am cheltuit cel mai mult?",
                "Compar\u0103 luna asta cu luna trecut\u0103",
                "Cum func\u021Bioneaz\u0103 OTP?",
                "Cum activez cardul virtual?"
        };

        for (String question : questions) {
            TextView chip = new TextView(this);
            chip.setText(question);
            chip.setTextColor(ContextCompat.getColor(this, R.color.white));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setBackgroundResource(R.drawable.bg_chat_chip);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            chip.setOnClickListener(v -> sendMessage(question));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, dp(8), 0);
            quickQuestionsContainer.addView(chip, params);
        }
    }

    private void sendCurrentMessage() {
        sendMessage(etMessage.getText().toString());
    }

    private void sendMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty() || isLoading) {
            return;
        }

        List<AiChatRequest.ChatMessage> previousHistory = new ArrayList<>(history);
        etMessage.setText("");
        addUserMessage(message);
        history.add(new AiChatRequest.ChatMessage("user", message));
        trimHistory();

        setLoading(true);
        showTypingBubble();

        currentCall = ApiClient.getAiService().chat(new AiChatRequest(message, previousHistory));
        currentCall.enqueue(new Callback<ApiResponse<AiChatData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AiChatData>> call, Response<ApiResponse<AiChatData>> response) {
                if (call.isCanceled()) {
                    return;
                }

                removeTypingBubble();
                setLoading(false);

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getData() != null) {
                    String answer = cleanAssistantText(response.body().getData().getAnswer());
                    addAssistantMessage(answer);
                    history.add(new AiChatRequest.ChatMessage("assistant", answer));
                    trimHistory();
                } else {
                    showAssistantError();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AiChatData>> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }

                removeTypingBubble();
                setLoading(false);
                showAssistantError();
            }
        });
    }

    private void addUserMessage(String message) {
        addBubble(message, true);
    }

    private void addAssistantMessage(String message) {
        addBubble(message, false);
    }

    private void addBubble(String message, boolean isUser) {
        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextColor(ContextCompat.getColor(this, R.color.white));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackgroundResource(isUser ? R.drawable.bg_chat_bubble_user : R.drawable.bg_chat_bubble_ai);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.setMargins(isUser ? dp(48) : 0, dp(6), isUser ? 0 : dp(48), dp(6));
        messagesContainer.addView(bubble, params);
        scrollToBottom();
    }

    private void showTypingBubble() {
        removeTypingBubble();

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackgroundResource(R.drawable.bg_chat_bubble_ai);

        TextView label = new TextView(this);
        label.setText("Swift AI analizeaz\u0103");
        label.setTextColor(ContextCompat.getColor(this, R.color.white_70));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bubble.addView(label);

        LinearLayout dotsContainer = new LinearLayout(this);
        dotsContainer.setOrientation(LinearLayout.HORIZONTAL);
        dotsContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dotsParams.setMargins(dp(8), 0, 0, 0);
        bubble.addView(dotsContainer, dotsParams);

        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setAlpha(0.35f);
            dot.setBackgroundResource(R.drawable.loading_dot);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
            dotParams.setMargins(dp(2), 0, dp(2), 0);
            dotsContainer.addView(dot, dotParams);

            ObjectAnimator animator = ObjectAnimator.ofFloat(dot, "alpha", 0.35f, 1f, 0.35f);
            animator.setDuration(900);
            animator.setStartDelay(i * 150L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.start();
            typingDotAnimators.add(animator);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.START;
        params.setMargins(0, dp(6), dp(48), dp(6));
        messagesContainer.addView(bubble, params);
        typingBubble = bubble;
        scrollToBottom();
    }

    private void removeTypingBubble() {
        stopTypingAnimation();
        if (typingBubble != null) {
            messagesContainer.removeView(typingBubble);
            typingBubble = null;
        }
    }

    private void stopTypingAnimation() {
        for (ObjectAnimator animator : typingDotAnimators) {
            animator.cancel();
        }
        typingDotAnimators.clear();
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        updateSendState();
        quickQuestionsScroll.setAlpha(loading ? 0.55f : 1f);
        quickQuestionsScroll.setEnabled(!loading);
    }

    private void updateSendState() {
        boolean hasText = etMessage != null && etMessage.getText() != null && etMessage.getText().toString().trim().length() > 0;
        boolean enabled = hasText && !isLoading;
        btnSendMessage.setEnabled(enabled);
        btnSendMessage.setAlpha(enabled ? 1f : 0.45f);
    }

    private void showAssistantError() {
        addAssistantMessage("Nu am putut primi r\u0103spunsul acum. Verific\u0103 conexiunea sau \u00EEncearc\u0103 din nou \u00EEn c\u00E2teva secunde.");
        Toast.makeText(this, "Swift AI nu este disponibil momentan", Toast.LENGTH_SHORT).show();
    }


    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        }
    }
    private String cleanAssistantText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Nu am un r\u0103spuns disponibil momentan.";
        }
        return text.replace("**", "").trim();
    }

    private void trimHistory() {
        while (history.size() > MAX_LOCAL_HISTORY_ITEMS) {
            history.remove(0);
        }
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}