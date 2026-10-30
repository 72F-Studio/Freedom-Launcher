package com.studio72f.eigen;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ratio × Kelvin Monochrome Frosted Chatbot & AI Assistant Card.
 *
 * Features:
 * - High-polish squircle frosted aesthetic (FrostedCard zero-stroke)
 * - Real-time conversational bubbles with tactile spring feedback
 * - Gemini API integration with offline smart fallback engine
 * - Quick contextual suggestions (Weather, Battery, Alarms, Launcher Help)
 * - App launch commands ("open spotify", "launch camera", etc.)
 * - Compact / Expanded view toggle with smooth height transitions
 */
public final class ChatbotView extends LinearLayout {
    public interface ActionListener {
        void onOpenSettings();
        void onSetWallpaper();
    }

    private static final String PREF_HISTORY = "chatbot_history";
    private static final String PREF_API_KEY = "gemini_api_key";
    private static final String PREF_MODEL = "gemini_model";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private float density;

    private int fgColor = 0xFFF1F2F3;
    private int accentColor = 0xFFFFEA00; // Ratio signature yellow
    private boolean isExpanded = true;
    private boolean isThinking = false;

    private LinearLayout headerLayout;
    private TextView titleView;
    private TextView statusSubtitle;
    private TextView clearBtn;
    private TextView expandBtn;

    private ScrollView messageScroller;
    private LinearLayout messageContainer;
    private HorizontalScrollView chipsScroller;
    private LinearLayout chipsContainer;
    private LinearLayout inputContainer;
    private EditText inputField;
    private TextView sendBtn;
    private View thinkingIndicator;

    private ActionListener actionListener;

    public ChatbotView(Context context) {
        super(context);
        init();
    }

    public ChatbotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg != 0 ? fg : 0xFFF1F2F3;
        this.accentColor = accent != 0 ? accent : 0xFFFFEA00;
        updateColors();
    }

    public void updateStatusSubtitle() {
        if (statusSubtitle != null) {
            statusSubtitle.setText(AiCoreEngine.getInstance().getEngineStatusLabel(getContext(), prefs));
            boolean isLocal = AiCoreEngine.getInstance().isAiCoreAvailable(getContext()) || AiCoreEngine.getInstance().isLiteRtAvailable();
            statusSubtitle.setTextColor(isLocal ? accentColor : 0xAAF1F2F3);
        }
    }

    private void updateColors() {
        if (titleView != null) titleView.setTextColor(fgColor);
        updateStatusSubtitle();
        if (sendBtn != null) {
            sendBtn.setTextColor(accentColor);
        }
    }

    private void init() {
        setOrientation(VERTICAL);
        density = getResources().getDisplayMetrics().density;
        prefs = getContext().getSharedPreferences("launcher_chatbot", Context.MODE_PRIVATE);

        // Apply Frosted Card Container (Squircle, zero-stroke)
        FrostedCard.apply(this, FrostedCard.RADIUS_CARD_DP);
        setPadding(dp(16), dp(14), dp(16), dp(14));

        // 1. Header Row
        setupHeader();
        addView(headerLayout);

        // 2. Chat Message History Scroller
        messageScroller = new ScrollView(getContext());
        messageScroller.setVerticalScrollBarEnabled(false);
        messageScroller.setClipToPadding(false);

        messageContainer = new LinearLayout(getContext());
        messageContainer.setOrientation(VERTICAL);
        messageContainer.setPadding(0, dp(6), 0, dp(8));
        messageScroller.addView(messageContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LayoutParams scrollerParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(220));
        scrollerParams.setMargins(0, dp(4), 0, dp(8));
        addView(messageScroller, scrollerParams);

        // 3. Quick Suggestion Chips (Horizontal Scroll)
        setupChips();
        addView(chipsScroller);

        // 4. Input Bar (Sunken Frosted Capsule)
        setupInputBar();
        addView(inputContainer);

        // Load persisted conversation history
        loadHistory();

        // If history is empty, show welcoming prompt
        if (messageContainer.getChildCount() == 0) {
            addAssistantMessage("✦ Hello! I'm your Ratio Assistant. Ask me anything, launch apps, or tap ⚙ in Settings to link your Gemini API key.");
        }
    }

    private void setupHeader() {
        headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(dp(2), 0, dp(2), dp(8));

        // Title and Status
        LinearLayout titleCol = new LinearLayout(getContext());
        titleCol.setOrientation(VERTICAL);

        titleView = new TextView(getContext());
        titleView.setText("✦ Assistant");
        titleView.setTextSize(16);
        titleView.setTextColor(fgColor);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(titleView);

        statusSubtitle = new TextView(getContext());
        statusSubtitle.setTextSize(11);
        statusSubtitle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        updateStatusSubtitle();
        titleCol.addView(statusSubtitle);

        headerLayout.addView(titleCol, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // Action Buttons: Clear History & Expand/Collapse Toggle
        clearBtn = createPillButton("Clear", v -> {
            clearHistory();
            addAssistantMessage("Conversation cleared. How can I help you?");
        });
        headerLayout.addView(clearBtn);

        expandBtn = createPillButton(isExpanded ? "▲" : "▼", v -> toggleExpand());
        headerLayout.addView(expandBtn);
    }

    private TextView createPillButton(String text, OnClickListener listener) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextSize(11);
        btn.setTextColor(0xAAF1F2F3);
        btn.setPadding(dp(10), dp(5), dp(10), dp(5));
        btn.setGravity(Gravity.CENTER);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrostedCard.applySunken(btn, FrostedCard.RADIUS_PILL_DP);

        attachSpringTouch(btn);
        btn.setOnClickListener(listener);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void toggleExpand() {
        isExpanded = !isExpanded;
        expandBtn.setText(isExpanded ? "▲" : "▼");
        messageScroller.setVisibility(isExpanded ? VISIBLE : GONE);
        chipsScroller.setVisibility(isExpanded ? VISIBLE : GONE);
        inputContainer.setVisibility(isExpanded ? VISIBLE : GONE);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void setupChips() {
        chipsScroller = new HorizontalScrollView(getContext());
        chipsScroller.setHorizontalScrollBarEnabled(false);
        chipsScroller.setClipToPadding(false);

        chipsContainer = new LinearLayout(getContext());
        chipsContainer.setOrientation(HORIZONTAL);
        chipsContainer.setPadding(0, 0, 0, dp(10));

        chipsContainer.addView(createChip("🌤 Weather", "What's the weather today?"));
        chipsContainer.addView(createChip("🔋 Battery", "Check battery status"));
        chipsContainer.addView(createChip("📅 Next Alarm", "What is my next alarm?"));
        chipsContainer.addView(createChip("✨ Ratio Tips", "How do I customize Ratio?"));
        chipsContainer.addView(createChip("⚙ Settings", "open settings"));
        chipsContainer.addView(createChip("🖼 Wallpaper", "change wallpaper"));

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

        attachSpringTouch(chip);
        chip.setOnClickListener(v -> {
            inputField.setText(prompt);
            submitMessage();
        });

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void setupInputBar() {
        inputContainer = new LinearLayout(getContext());
        inputContainer.setOrientation(HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        inputContainer.setPadding(dp(14), dp(4), dp(10), dp(4));
        FrostedCard.applySunken(inputContainer, FrostedCard.RADIUS_PILL_DP);

        inputField = new EditText(getContext());
        inputField.setHint("Ask or command...");
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

        inputContainer.addView(inputField, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        sendBtn = new TextView(getContext());
        sendBtn.setText("➤");
        sendBtn.setTextSize(16);
        sendBtn.setTextColor(accentColor);
        sendBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        sendBtn.setGravity(Gravity.CENTER);
        attachSpringTouch(sendBtn);
        sendBtn.setOnClickListener(v -> submitMessage());

        inputContainer.addView(sendBtn);
    }

    private void submitMessage() {
        String query = inputField.getText() != null ? inputField.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query) || isThinking) return;

        inputField.setText("");
        addUserMessage(query);
        showThinkingIndicator();

        String lower = query.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            String appName = lower.replaceFirst("^(open|launch)\\s+", "").trim();
            if (appName.equals("settings")) {
                hideThinkingIndicator();
                addAssistantMessage("Opening Launcher Settings...");
                saveHistory();
                if (actionListener != null) actionListener.onOpenSettings();
                return;
            }
            if (appName.equals("wallpaper")) {
                hideThinkingIndicator();
                addAssistantMessage("Opening Wallpaper Settings...");
                saveHistory();
                if (actionListener != null) actionListener.onSetWallpaper();
                return;
            }
        }

        AiCoreEngine.getInstance().generateAsync(getContext(), query, new AiCoreEngine.Callback() {
            @Override
            public void onResponse(String text, String source) {
                hideThinkingIndicator();
                addAssistantMessage(text);
                saveHistory();
            }

            @Override
            public void onError(String message) {
                hideThinkingIndicator();
                addAssistantMessage("Error: " + message);
                saveHistory();
            }
        });
    }

    private void addUserMessage(String message) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(32), dp(4), 0, dp(4));

        TextView bubble = new TextView(getContext());
        bubble.setText(message);
        bubble.setTextSize(13);
        bubble.setTextColor(fgColor);
        bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
        bubble.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // User bubble: Dark raised squircle with subtle accent tint
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_RAISED));
        bg.setCornerRadius(dp(14));
        bg.setStroke(0, 0);
        bubble.setBackground(bg);

        row.addView(bubble);
        messageContainer.addView(row);
        scrollToBottom();
    }

    private void addAssistantMessage(String message) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, dp(4), dp(32), dp(4));

        TextView bubble = new TextView(getContext());
        bubble.setText(message);
        bubble.setTextSize(13);
        bubble.setTextColor(0xEEF1F2F3);
        bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
        bubble.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Assistant bubble: Sunken matte squircle
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_SUNKEN));
        bg.setCornerRadius(dp(14));
        bg.setStroke(0, 0);
        bubble.setBackground(bg);

        // Long press on assistant bubble copies text
        bubble.setOnLongClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Assistant Response", message));
                Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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
            dots.setAlpha(0.7f);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(FrostedCard.resolveFillColor(FrostedCard.COLOR_SUNKEN));
            bg.setCornerRadius(dp(12));
            dots.setBackground(bg);

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setPadding(0, dp(4), dp(32), dp(4));
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

    /**
     * Dual-Engine Query Processing:
     * 1. Attempts remote Gemini API if key is present
     * 2. Falls back to smart local assistant capabilities
     */
    private String processQuery(String query) {
        String lower = query.toLowerCase(Locale.ROOT).trim();

        // 1. Local App Launching commands
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            String appName = lower.replaceFirst("^(open|launch)\\s+", "").trim();
            if (appName.equals("settings")) {
                handler.post(() -> {
                    if (actionListener != null) actionListener.onOpenSettings();
                });
                return "Opening Launcher Settings...";
            }
            if (appName.equals("wallpaper")) {
                handler.post(() -> {
                    if (actionListener != null) actionListener.onSetWallpaper();
                });
                return "Opening Wallpaper Settings...";
            }
            String launchResult = launchAppByName(appName);
            if (launchResult != null) return launchResult;
        }

        // 2. Local direct commands
        if (lower.contains("settings")) {
            handler.post(() -> {
                if (actionListener != null) actionListener.onOpenSettings();
            });
            return "Opening Settings sheet for you...";
        }
        if (lower.contains("wallpaper")) {
            handler.post(() -> {
                if (actionListener != null) actionListener.onSetWallpaper();
            });
            return "Opening Wallpaper configuration...";
        }

        // 3. Local Battery Query
        if (lower.contains("battery")) {
            BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                boolean isCharging = bm.isCharging();
                return String.format(Locale.getDefault(), "Battery is currently at %d%%%s.", level, isCharging ? " (Charging ⚡)" : "");
            }
        }

        // 4. Local Time / Date Query
        if (lower.contains("time") || lower.contains("date") || lower.contains("today")) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault());
            return "Current time: " + sdf.format(new Date());
        }

        // 5. Local Ratio & Launcher Tips
        if (lower.contains("ratio") || lower.contains("launcher") || lower.contains("tip") || lower.contains("help")) {
            return "✦ Ratio Tips:\n" +
                   "• Swipe Left: Root Productivity Cards (Calculator, Weather, Notes)\n" +
                   "• Center: Frosted Tiles Grid with category drawers\n" +
                   "• Swipe Right: Tree Unified Messaging\n" +
                   "• Tap ⚙ in bottom bar to open Settings\n" +
                   "• Enter a Gemini API Key in Settings for deep conversational reasoning!";
        }

        // 6. Gemini Cloud AI Inference
        String apiKey = prefs.getString(PREF_API_KEY, "");
        if (!apiKey.isEmpty()) {
            try {
                return callGeminiApi(apiKey, query);
            } catch (Exception e) {
                return "Gemini API error (" + e.getMessage() + "). Check your API key or network connection.";
            }
        }

        // 7. General smart offline answer
        return "I'm running in offline mode. You can ask me about battery status, time, launching apps ('open Spotify'), or open settings.\n\n" +
               "To unlock full cloud AI chat, enter your Google Gemini API key in Launcher Settings → AI Assistant.";
    }

    private String launchAppByName(String targetName) {
        PackageManager pm = getContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : list) {
            String label = info.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if (label.contains(targetName) || targetName.contains(label)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(launchIntent);
                    return "Launching " + info.loadLabel(pm) + "...";
                }
            }
        }
        return "Could not find an app matching '" + targetName + "'.";
    }

    private String callGeminiApi(String apiKey, String prompt) throws Exception {
        String model = prefs.getString(PREF_MODEL, DEFAULT_MODEL);
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);

        JSONObject requestJson = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject contentObj = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject partObj = new JSONObject();
        partObj.put("text", "You are the built-in AI assistant for Eigen Launcher. Keep responses concise, elegant, informative, and formatted with clean markdown.\nUser query: " + prompt);
        parts.put(partObj);
        contentObj.put("parts", parts);
        contents.put(contentObj);
        requestJson.put("contents", contents);

        byte[] postData = requestJson.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + sb.toString());
        }

        JSONObject resp = new JSONObject(sb.toString());
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray resParts = content.optJSONArray("parts");
                if (resParts != null && resParts.length() > 0) {
                    return resParts.getJSONObject(0).optString("text", "No response text.");
                }
            }
        }
        return "No text candidates returned by Gemini.";
    }

    private void attachSpringTouch(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180)
                            .setInterpolator(new OvershootInterpolator(1.3f)).start();
                    break;
            }
            return false;
        });
    }

    private void saveHistory() {
        JSONArray arr = new JSONArray();
        int count = messageContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = messageContainer.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout l = (LinearLayout) row;
                if (l.getChildCount() > 0 && l.getChildAt(0) instanceof TextView) {
                    TextView tv = (TextView) l.getChildAt(0);
                    boolean isUser = l.getGravity() == Gravity.END;
                    try {
                        JSONObject item = new JSONObject();
                        item.put("user", isUser);
                        item.put("text", tv.getText().toString());
                        arr.put(item);
                    } catch (Exception ignored) {}
                }
            }
        }
        prefs.edit().putString(PREF_HISTORY, arr.toString()).apply();
    }

    private void loadHistory() {
        String json = prefs.getString(PREF_HISTORY, "");
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                boolean isUser = obj.optBoolean("user", false);
                String text = obj.optString("text", "");
                if (isUser) addUserMessage(text);
                else addAssistantMessage(text);
            }
        } catch (Exception ignored) {}
    }

    private void clearHistory() {
        messageContainer.removeAllViews();
        prefs.edit().remove(PREF_HISTORY).apply();
    }
}
