package com.studio72f.freedom;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DecimalFormat;
import java.util.Calendar;

/**
 * Ratio-inspired "Root" Widgets & Tools Page.
 *
 * Implements Ratio's core widget stack in frosted glass cards:
 * 1. Day Progress Bar
 * 2. Weather Card
 * 3. Quick Notes Scratchpad
 * 4. Built-in Calculator
 * 5. Media Player Card
 */
public final class RootPageView extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private float density;

    private int fgColor = Color.WHITE;
    private int accentColor = 0xFF39B9D4;

    // Day Progress
    private ProgressBar dayProgressBar;
    private TextView dayProgressText;

    // Notes
    private EditText notesInput;

    // Calculator state
    private TextView calcDisplay;
    private String calcExpr = "";

    // Media Player
    private TextView mediaTitleText;
    private TextView mediaArtistText;
    private TextView mediaPlayBtn;
    private MediaController mediaController;

    // AI Assistant / Chatbot
    private ChatbotView chatbotView;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateDayProgress();
            handler.postDelayed(this, 60000); // Check every minute
        }
    };

    public RootPageView(Context context) {
        super(context);
        init();
    }

    public RootPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void init() {
        setOrientation(VERTICAL);
        density = getResources().getDisplayMetrics().density;
        prefs = getContext().getSharedPreferences("launcher_root_widgets", Context.MODE_PRIVATE);
        setPadding(dp(16), dp(16), dp(16), dp(16));

        // Top Header
        TextView header = new TextView(getContext());
        header.setText("Root");
        header.setTextSize(24);
        header.setTextColor(fgColor);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(8), dp(8), dp(8), dp(14));
        addView(header);

        // Scrollable widgets stack
        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout stack = new LinearLayout(getContext());
        stack.setOrientation(VERTICAL);

        // 1. Day Progress Card
        stack.addView(createDayProgressCard(), createCardParams());

        // 2. AI Assistant / Chatbot Card
        chatbotView = new ChatbotView(getContext());
        chatbotView.setColors(fgColor, accentColor);
        stack.addView(chatbotView, createCardParams());

        // 3. Weather Card
        stack.addView(createWeatherCard(), createCardParams());

        // 4. Quick Notes Scratchpad Card
        stack.addView(createNotesCard(), createCardParams());

        // 5. Instant Calculator Card
        stack.addView(createCalculatorCard(), createCardParams());

        // 6. Media Player Card
        stack.addView(createMediaPlayerCard(), createCardParams());

        scroll.addView(stack, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        updateDayProgress();
    }

    private LayoutParams createCardParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        return lp;
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg;
        this.accentColor = accent;
        if (chatbotView != null) {
            chatbotView.setColors(fg, accent);
        }
    }

    public void setChatbotActionListener(ChatbotView.ActionListener listener) {
        if (chatbotView != null) {
            chatbotView.setActionListener(listener);
        }
    }

    public void refreshChatbot() {
        if (chatbotView != null) {
            chatbotView.setColors(fgColor, accentColor);
            chatbotView.updateStatusSubtitle();
        }
    }

    public void setMediaController(MediaController controller) {
        this.mediaController = controller;
        updateMediaState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);
        updateMediaState();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(timerRunnable);
        super.onDetachedFromWindow();
    }

    // --- 1. DAY PROGRESS CARD ---
    private View createDayProgressCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrostedCard.apply(card, 16);

        TextView label = new TextView(getContext());
        label.setText("DAY PROGRESS");
        label.setTextSize(10);
        label.setTextColor(accentColor);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label);

        dayProgressText = new TextView(getContext());
        dayProgressText.setText("Calculating...");
        dayProgressText.setTextSize(18);
        dayProgressText.setTextColor(fgColor);
        dayProgressText.setTypeface(Typeface.DEFAULT_BOLD);
        dayProgressText.setPadding(0, dp(4), 0, dp(8));
        card.addView(dayProgressText);

        dayProgressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        dayProgressBar.setMax(100);
        dayProgressBar.setProgress(50);
        card.addView(dayProgressBar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(8)));

        return card;
    }

    private void updateDayProgress() {
        Calendar cal = Calendar.getInstance();
        int minutesPassed = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);
        int totalMinutesInDay = 24 * 60;
        int percentage = Math.round((minutesPassed / (float) totalMinutesInDay) * 100);
        int hoursRemaining = 24 - cal.get(Calendar.HOUR_OF_DAY);

        if (dayProgressBar != null) dayProgressBar.setProgress(percentage);
        if (dayProgressText != null) {
            dayProgressText.setText(percentage + "% of today completed (" + hoursRemaining + "h left)");
        }
    }

    // --- 2. WEATHER CARD ---
    private View createWeatherCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrostedCard.applyInteractive(card, 16);

        TextView label = new TextView(getContext());
        label.setText("WEATHER");
        label.setTextSize(10);
        label.setTextColor(accentColor);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, 0);

        TextView temp = new TextView(getContext());
        temp.setText("24°C");
        temp.setTextSize(26);
        temp.setTextColor(fgColor);
        temp.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(temp);

        TextView cond = new TextView(getContext());
        cond.setText("  Clear & Sunny • High 28° / Low 19°");
        cond.setTextSize(13);
        cond.setTextColor(0xAAFFFFFF);
        row.addView(cond);

        card.addView(row);

        card.setOnClickListener(v -> {
            // Launch weather or clock
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"));
            try {
                getContext().startActivity(intent);
            } catch (Exception ignored) {}
        });

        return card;
    }

    // --- 3. NOTES SCRATCHPAD CARD ---
    private View createNotesCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrostedCard.apply(card, 16);

        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(getContext());
        label.setText("QUICK NOTES");
        label.setTextSize(10);
        label.setTextColor(accentColor);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView copyBtn = new TextView(getContext());
        copyBtn.setText("Copy");
        copyBtn.setTextSize(12);
        copyBtn.setTextColor(0xAAFFFFFF);
        copyBtn.setPadding(dp(8), 0, dp(8), 0);
        copyBtn.setOnClickListener(v -> {
            if (notesInput != null) {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Quick Note", notesInput.getText().toString()));
                    Toast.makeText(getContext(), "Note copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });
        topRow.addView(copyBtn);

        TextView clearBtn = new TextView(getContext());
        clearBtn.setText("Clear");
        clearBtn.setTextSize(12);
        clearBtn.setTextColor(0x77FFFFFF);
        clearBtn.setPadding(dp(8), 0, 0, 0);
        clearBtn.setOnClickListener(v -> {
            if (notesInput != null) notesInput.setText("");
        });
        topRow.addView(clearBtn);

        card.addView(topRow);

        notesInput = new EditText(getContext());
        notesInput.setHint("Write a thought, to-do or link...");
        notesInput.setHintTextColor(0x66FFFFFF);
        notesInput.setTextColor(fgColor);
        notesInput.setTextSize(14);
        notesInput.setBackground(null);
        notesInput.setMinLines(3);
        notesInput.setMaxLines(8);
        notesInput.setPadding(0, dp(8), 0, dp(4));

        String savedNote = prefs.getString("scratchpad_text", "");
        notesInput.setText(savedNote);

        notesInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString("scratchpad_text", s.toString()).apply();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        card.addView(notesInput);
        return card;
    }

    // --- 4. CALCULATOR CARD ---
    private View createCalculatorCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrostedCard.apply(card, 16);

        TextView label = new TextView(getContext());
        label.setText("CALCULATOR");
        label.setTextSize(10);
        label.setTextColor(accentColor);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label);

        calcDisplay = new TextView(getContext());
        calcDisplay.setText("0");
        calcDisplay.setTextSize(22);
        calcDisplay.setTextColor(fgColor);
        calcDisplay.setTypeface(Typeface.DEFAULT_BOLD);
        calcDisplay.setGravity(Gravity.END);
        calcDisplay.setPadding(0, dp(4), 0, dp(12));
        card.addView(calcDisplay);

        // 4x4 Grid of calculator buttons
        String[][] keys = {
            {"7", "8", "9", "÷"},
            {"4", "5", "6", "×"},
            {"1", "2", "3", "-"},
            {"C", "0", "=", "+"}
        };

        for (String[] row : keys) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);

            for (String key : row) {
                TextView btn = new TextView(getContext());
                btn.setText(key);
                btn.setTextSize(16);
                btn.setGravity(Gravity.CENTER);

                boolean isOp = "÷".equals(key) || "×".equals(key) || "-".equals(key) || "+".equals(key) || "=".equals(key);
                btn.setTextColor(isOp ? accentColor : Color.WHITE);
                btn.setTypeface(isOp ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

                FrostedCard.applyInteractive(btn, 10);
                btn.setOnClickListener(v -> onCalcKey(key));

                LayoutParams lp = new LayoutParams(0, dp(38), 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                rowLayout.addView(btn, lp);
            }
            card.addView(rowLayout);
        }

        return card;
    }

    private void onCalcKey(String key) {
        if ("C".equals(key)) {
            calcExpr = "";
            calcDisplay.setText("0");
            return;
        }

        if ("=".equals(key)) {
            if (!calcExpr.isEmpty()) {
                double result = evaluateMath(calcExpr);
                DecimalFormat df = new DecimalFormat("#.######");
                calcDisplay.setText(df.format(result));
                calcExpr = df.format(result);
            }
            return;
        }

        calcExpr += key;
        calcDisplay.setText(calcExpr);
    }

    private double evaluateMath(String expr) {
        try {
            expr = expr.replace("×", "*").replace("÷", "/");
            // Simple 2-operand or multi-operand parser
            String[] tokens = expr.split("(?<=[-+*/])|(?=[-+*/])");
            if (tokens.length == 0) return 0;
            double current = Double.parseDouble(tokens[0]);
            for (int i = 1; i < tokens.length - 1; i += 2) {
                String op = tokens[i];
                double next = Double.parseDouble(tokens[i + 1]);
                switch (op) {
                    case "+": current += next; break;
                    case "-": current -= next; break;
                    case "*": current *= next; break;
                    case "/": if (next != 0) current /= next; break;
                }
            }
            return current;
        } catch (Exception e) {
            return 0;
        }
    }

    // --- 5. MEDIA PLAYER CARD ---
    private View createMediaPlayerCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        FrostedCard.apply(card, 16);

        TextView label = new TextView(getContext());
        label.setText("MEDIA PLAYER");
        label.setTextSize(10);
        label.setTextColor(accentColor);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label);

        mediaTitleText = new TextView(getContext());
        mediaTitleText.setText("No media playing");
        mediaTitleText.setTextSize(16);
        mediaTitleText.setTextColor(fgColor);
        mediaTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        mediaTitleText.setPadding(0, dp(4), 0, 0);
        card.addView(mediaTitleText);

        mediaArtistText = new TextView(getContext());
        mediaArtistText.setText("Tap to launch audio");
        mediaArtistText.setTextSize(13);
        mediaArtistText.setTextColor(0x88FFFFFF);
        mediaArtistText.setPadding(0, 0, 0, dp(10));
        card.addView(mediaArtistText);

        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        TextView prevBtn = new TextView(getContext());
        prevBtn.setText("⏮");
        prevBtn.setTextSize(20);
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setPadding(dp(12), dp(6), dp(16), dp(6));
        prevBtn.setOnClickListener(v -> {
            if (mediaController != null) mediaController.getTransportControls().skipToPrevious();
        });
        controls.addView(prevBtn);

        mediaPlayBtn = new TextView(getContext());
        mediaPlayBtn.setText("▶");
        mediaPlayBtn.setTextSize(22);
        mediaPlayBtn.setTextColor(accentColor);
        mediaPlayBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        mediaPlayBtn.setOnClickListener(v -> {
            if (mediaController != null) {
                PlaybackState state = mediaController.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
            }
        });
        controls.addView(mediaPlayBtn);

        TextView nextBtn = new TextView(getContext());
        nextBtn.setText("⏭");
        nextBtn.setTextSize(20);
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setPadding(dp(16), dp(6), dp(12), dp(6));
        nextBtn.setOnClickListener(v -> {
            if (mediaController != null) mediaController.getTransportControls().skipToNext();
        });
        controls.addView(nextBtn);

        card.addView(controls);
        return card;
    }

    public void updateMediaState() {
        if (mediaTitleText == null || mediaArtistText == null) return;
        if (mediaController != null) {
            MediaMetadata meta = mediaController.getMetadata();
            PlaybackState state = mediaController.getPlaybackState();
            if (meta != null) {
                String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                mediaTitleText.setText(title != null ? title : "Audio Playing");
                mediaArtistText.setText(artist != null ? artist : "");
            }
            if (mediaPlayBtn != null && state != null) {
                boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
                mediaPlayBtn.setText(playing ? "⏸" : "▶");
            }
        }
    }
}
