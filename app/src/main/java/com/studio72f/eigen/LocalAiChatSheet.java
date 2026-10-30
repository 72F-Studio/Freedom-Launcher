package com.studio72f.eigen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ratio × Kelvin Local AI Chat Sheet for Homescreen.
 *
 * Provides instant on-device AI chat (LiteRT + AICore Gemini Nano)
 * directly from the center homescreen without having to switch tabs.
 */
public final class LocalAiChatSheet extends FrameLayout {
    public interface Listener {
        void onDismiss();
        void onOpenSettings();
    }

    private final View scrim;
    private final LinearLayout panel;
    private final View handle;
    private TextView titleView;
    private TextView engineBadge;
    private ScrollView messageScroller;
    private LinearLayout messageContainer;
    private HorizontalScrollView chipsScroller;
    private LinearLayout chipsContainer;
    private EditText inputField;
    private TextView sendBtn;
    private View thinkingIndicator;

    private Listener listener;
    private boolean isThinking = false;
    private final float density;
    private int fgColor = 0xFFF1F2F3;
    private int accentColor = 0xFFFFEA00; // Ratio signature yellow

    public LocalAiChatSheet(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        // 1. Dim Backdrop Scrim
        scrim = new View(context);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 2. Main Frosted Floating Bottom Sheet Panel
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        FrostedCard.apply(panel, FrostedCard.RADIUS_CARD_DP);
        panel.setPadding(dp(16), dp(10), dp(16), dp(16));

        // Top Drag Handle
        handle = new View(context);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(0x44F1F2F3);
        hBg.setCornerRadius(dp(2));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        hParams.gravity = Gravity.CENTER_HORIZONTAL;
        hParams.setMargins(0, dp(4), 0, dp(10));
        panel.addView(handle, hParams);

        // Header Row (✦ Local AI Assistant • Engine Status • Close)
        setupHeader(context);
        panel.addView(createHeaderRow());

        // Message Scroller
        messageScroller = new ScrollView(context);
        messageScroller.setVerticalScrollBarEnabled(false);
        messageScroller.setClipToPadding(false);

        messageContainer = new LinearLayout(context);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageContainer.setPadding(0, dp(4), 0, dp(6));
        messageScroller.addView(messageContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(260));
        scrollParams.setMargins(0, dp(6), 0, dp(8));
        panel.addView(messageScroller, scrollParams);

        // Quick Suggestion Chips
        setupChips(context);
        panel.addView(chipsScroller);

        // Sunken Input Capsule
        setupInputBar(context);

        LayoutParams pParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pParams.gravity = Gravity.BOTTOM;
        pParams.setMargins(dp(12), 0, dp(12), dp(16));
        addView(panel, pParams);

        // Initial welcome message
        addAssistantMessage("✦ Local AI Ready (LiteRT + AICore). Ask questions, launch apps, or run device actions right from Home.");
        setVisibility(GONE);
        setFocusable(true);

        setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset = insets.getSystemWindowInsetBottom();
            int leftInset = insets.getSystemWindowInsetLeft();
            int rightInset = insets.getSystemWindowInsetRight();
            setPadding(leftInset, 0, rightInset, bottomInset);
            return insets;
        });
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg != 0 ? fg : 0xFFF1F2F3;
        this.accentColor = accent != 0 ? accent : 0xFFFFEA00;
        if (titleView != null) titleView.setTextColor(fgColor);
        if (engineBadge != null) engineBadge.setTextColor(accentColor);
        if (sendBtn != null) sendBtn.setTextColor(accentColor);
    }

    private void setupHeader(Context context) {
        // Handled in createHeaderRow
    }

    private View createHeaderRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(4), dp(8));

        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(getContext());
        titleView.setText("✦ Local AI Assistant");
        titleView.setTextSize(17);
        titleView.setTextColor(fgColor);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        col.addView(titleView);

        engineBadge = new TextView(getContext());
        engineBadge.setText(AiCoreEngine.getInstance().getEngineStatusLabel(getContext(), getContext().getSharedPreferences("launcher_chatbot", Context.MODE_PRIVATE)));
        engineBadge.setTextSize(11);
        engineBadge.setTextColor(accentColor);
        engineBadge.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        col.addView(engineBadge);

        row.addView(col, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // Settings Shortcut Button
        TextView setBtn = new TextView(getContext());
        setBtn.setText("⚙");
        setBtn.setTextSize(16);
        setBtn.setTextColor(0xAAF1F2F3);
        setBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        setBtn.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onOpenSettings();
        });
        row.addView(setBtn);

        // Close Button
        TextView closeBtn = new TextView(getContext());
        closeBtn.setText("✕");
        closeBtn.setTextSize(16);
        closeBtn.setTextColor(0xAAF1F2F3);
        closeBtn.setPadding(dp(10), dp(4), dp(6), dp(4));
        closeBtn.setOnClickListener(v -> dismiss());
        row.addView(closeBtn);

        return row;
    }

    private void setupChips(Context context) {
        chipsScroller = new HorizontalScrollView(context);
        chipsScroller.setHorizontalScrollBarEnabled(false);
        chipsScroller.setClipToPadding(false);

        chipsContainer = new LinearLayout(context);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsContainer.setPadding(0, 0, 0, dp(8));

        chipsContainer.addView(createChip("🔋 Battery", "battery status"));
        chipsContainer.addView(createChip("🕒 Current Time", "what time is it?"));
        chipsContainer.addView(createChip("🌤 Today's Weather", "what is the weather today?"));
        chipsContainer.addView(createChip("📷 Open Camera", "open camera"));
        chipsContainer.addView(createChip("✦ AI Status", "what is aicore?"));

        chipsScroller.addView(chipsContainer);
    }

    private View createChip(String label, String prompt) {
        TextView chip = new TextView(getContext());
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(0xCCF1F2F3);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        FrostedCard.applySunken(chip, FrostedCard.RADIUS_PILL_DP);

        chip.setOnClickListener(v -> {
            inputField.setText(prompt);
            submitMessage();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void setupInputBar(Context context) {
        LinearLayout inputBar = new LinearLayout(context);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(14), dp(4), dp(10), dp(4));
        FrostedCard.applySunken(inputBar, FrostedCard.RADIUS_PILL_DP);

        inputField = new EditText(context);
        inputField.setHint("Ask AI or command phone...");
        inputField.setHintTextColor(0x55F1F2F3);
        inputField.setTextColor(fgColor);
        inputField.setTextSize(14);
        inputField.setBackground(null);
        inputField.setSingleLine(true);
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
               (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitMessage();
                return true;
            }
            return false;
        });

        inputBar.addView(inputField, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        sendBtn = new TextView(context);
        sendBtn.setText("➤");
        sendBtn.setTextSize(16);
        sendBtn.setTextColor(accentColor);
        sendBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setOnClickListener(v -> submitMessage());

        inputBar.addView(sendBtn);
        panel.addView(inputBar);
    }

    private void submitMessage() {
        String query = inputField.getText() != null ? inputField.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query) || isThinking) return;

        inputField.setText("");
        addUserMessage(query);
        showThinkingIndicator();

        AiCoreEngine.getInstance().generateAsync(getContext(), query, new AiCoreEngine.Callback() {
            @Override
            public void onResponse(String text, String source) {
                hideThinkingIndicator();
                addAssistantMessage(text);
            }

            @Override
            public void onError(String message) {
                hideThinkingIndicator();
                addAssistantMessage("Error: " + message);
            }
        });
    }

    private void addUserMessage(String message) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(32), dp(3), 0, dp(3));

        TextView bubble = new TextView(getContext());
        bubble.setText(message);
        bubble.setTextSize(13);
        bubble.setTextColor(fgColor);
        bubble.setPadding(dp(14), dp(8), dp(14), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_RAISED));
        bg.setCornerRadius(dp(14));
        bubble.setBackground(bg);

        row.addView(bubble);
        messageContainer.addView(row);
        scrollToBottom();
    }

    private void addAssistantMessage(String message) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, dp(3), dp(32), dp(3));

        TextView bubble = new TextView(getContext());
        bubble.setText(message);
        bubble.setTextSize(13);
        bubble.setTextColor(0xEEF1F2F3);
        bubble.setPadding(dp(14), dp(8), dp(14), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_SUNKEN));
        bg.setCornerRadius(dp(14));
        bubble.setBackground(bg);

        bubble.setOnLongClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AI Response", message));
                Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        row.addView(bubble);
        messageContainer.addView(row);
        scrollToBottom();
    }

    private void showThinkingIndicator() {
        isThinking = true;
        if (thinkingIndicator == null) {
            TextView dots = new TextView(getContext());
            dots.setText("●  ●  ●");
            dots.setTextSize(11);
            dots.setTextColor(accentColor);
            dots.setPadding(dp(14), dp(6), dp(14), dp(6));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_SUNKEN));
            bg.setCornerRadius(dp(12));
            dots.setBackground(bg);

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(3), dp(32), dp(3));
            row.addView(dots);

            thinkingIndicator = row;
        }
        if (thinkingIndicator.getParent() == null) {
            messageContainer.addView(thinkingIndicator);
            scrollToBottom();
        }
    }

    private void hideThinkingIndicator() {
        isThinking = false;
        if (thinkingIndicator != null && thinkingIndicator.getParent() != null) {
            messageContainer.removeView(thinkingIndicator);
        }
    }

    private void scrollToBottom() {
        messageScroller.post(() -> messageScroller.fullScroll(ScrollView.FOCUS_DOWN));
    }

    public void refreshEngineBadge() {
        if (engineBadge != null) {
            engineBadge.setText(AiCoreEngine.getInstance().getEngineStatusLabel(getContext(), getContext().getSharedPreferences("launcher_chatbot", Context.MODE_PRIVATE)));
        }
    }

    public void show() {
        refreshEngineBadge();
        setVisibility(VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(220).start();

        panel.setTranslationY(dp(400));
        panel.animate()
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();

        if (inputField != null) {
            inputField.requestFocus();
            inputField.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        }
    }

    public void dismiss() {
        if (inputField != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(inputField.getWindowToken(), 0);
        }

        scrim.animate().alpha(0f).setDuration(180).start();
        panel.animate()
                .translationY(dp(500))
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    setVisibility(GONE);
                    if (listener != null) listener.onDismiss();
                })
                .start();
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }
}
