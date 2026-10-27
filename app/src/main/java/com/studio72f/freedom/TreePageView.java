package com.studio72f.freedom;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Ratio-inspired "Tree" Conversations & Notification Timeline.
 *
 * Provides a unified timeline across messaging apps (WhatsApp, Telegram, Signal, Slack, SMS)
 * with direct inline replies via RemoteInput without launching external apps.
 */
public final class TreePageView extends LinearLayout implements MediaAccessService.ConversationListener {
    private final List<MediaAccessService.ConversationItem> allItems = new ArrayList<>();
    private final List<MediaAccessService.ConversationItem> filteredItems = new ArrayList<>();
    private String currentFilter = "all"; // "all", "direct", "groups"
    private String searchQuery = "";

    private LinearLayout headerLayout;
    private LinearLayout filterChipsLayout;
    private LinearLayout contentContainer;
    private ScrollView scrollView;
    private EditText searchInput;
    private TextView emptyText;

    private int fgColor = Color.WHITE;
    private int accentColor = 0xFF39B9D4;
    private float density;

    public TreePageView(Context context) {
        super(context);
        init();
    }

    public TreePageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private void init() {
        setOrientation(VERTICAL);
        density = getResources().getDisplayMetrics().density;
        setPadding(dp(16), dp(16), dp(16), dp(16));

        // Top Header
        headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(dp(8), dp(8), dp(8), dp(12));

        TextView title = new TextView(getContext());
        title.setText("Conversations");
        title.setTextSize(24);
        title.setTextColor(fgColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        headerLayout.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        addView(headerLayout);

        // Filter Chips (All | Direct | Groups)
        filterChipsLayout = new LinearLayout(getContext());
        filterChipsLayout.setOrientation(HORIZONTAL);
        filterChipsLayout.setPadding(dp(4), 0, dp(4), dp(14));
        setupFilterChips();
        addView(filterChipsLayout);

        // ScrollView for conversation cards
        scrollView = new ScrollView(getContext());
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        contentContainer = new LinearLayout(getContext());
        contentContainer.setOrientation(VERTICAL);
        scrollView.addView(contentContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // Bottom Search Bar
        LinearLayout searchBar = new LinearLayout(getContext());
        searchBar.setOrientation(HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        FrostedCard.apply(searchBar, 24);

        searchInput = new EditText(getContext());
        searchInput.setHint("Tap to search conversations...");
        searchInput.setHintTextColor(0x88FFFFFF);
        searchInput.setTextColor(fgColor);
        searchInput.setTextSize(14);
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchBar.addView(searchInput, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams searchParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, dp(10), 0, dp(4));
        addView(searchBar, searchParams);
    }

    private void setupFilterChips() {
        filterChipsLayout.removeAllViews();
        String[] filters = {"all", "direct", "groups"};
        String[] titles = {"All", "Direct", "Groups"};

        for (int i = 0; i < filters.length; i++) {
            final String f = filters[i];
            TextView chip = new TextView(getContext());
            chip.setText(titles[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));

            boolean selected = f.equals(currentFilter);
            if (selected) {
                chip.setBackground(FrostedCard.create(0x44FFFFFF, 0xFFFFFFFF, 16, 1.2f, density));
                chip.setTextColor(Color.WHITE);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                chip.setBackground(FrostedCard.create(0x18FFFFFF, 0x44FFFFFF, 16, 1f, density));
                chip.setTextColor(0xAAFFFFFF);
                chip.setTypeface(Typeface.DEFAULT);
            }

            chip.setOnClickListener(v -> {
                currentFilter = f;
                setupFilterChips();
                applyFilter();
            });

            LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, dp(8), 0);
            filterChipsLayout.addView(chip, p);
        }
    }

    public void setColors(int fg, int accent) {
        this.fgColor = fg;
        this.accentColor = accent;
        setupFilterChips();
        applyFilter();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MediaAccessService.setConversationListener(this);
        refreshFromService();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void refreshFromService() {
        List<MediaAccessService.ConversationItem> items = MediaAccessService.getConversations();
        onConversationsChanged(items);
    }

    @Override
    public void onConversationsChanged(List<MediaAccessService.ConversationItem> items) {
        allItems.clear();
        if (items != null) allItems.addAll(items);
        applyFilter();
    }

    private void applyFilter() {
        filteredItems.clear();
        for (MediaAccessService.ConversationItem item : allItems) {
            if ("direct".equals(currentFilter) && item.isGroup) continue;
            if ("groups".equals(currentFilter) && !item.isGroup) continue;

            if (!searchQuery.isEmpty()) {
                boolean matchSender = item.sender != null && item.sender.toLowerCase().contains(searchQuery);
                boolean matchText = item.text != null && item.text.toLowerCase().contains(searchQuery);
                boolean matchApp = item.appName != null && item.appName.toLowerCase().contains(searchQuery);
                if (!matchSender && !matchText && !matchApp) continue;
            }
            filteredItems.add(item);
        }
        renderCards();
    }

    private void renderCards() {
        contentContainer.removeAllViews();

        if (!MediaAccessService.isServiceConnected()) {
            // Render Notification Permission Request Card
            LinearLayout permCard = new LinearLayout(getContext());
            permCard.setOrientation(VERTICAL);
            permCard.setPadding(dp(20), dp(20), dp(20), dp(20));
            FrostedCard.apply(permCard, 18);

            TextView pTitle = new TextView(getContext());
            pTitle.setText("Enable Conversations Timeline");
            pTitle.setTextSize(18);
            pTitle.setTextColor(fgColor);
            pTitle.setTypeface(Typeface.DEFAULT_BOLD);
            permCard.addView(pTitle);

            TextView pDesc = new TextView(getContext());
            pDesc.setText("To view your chat notifications here with direct inline replies, enable notification access for 72F Launcher.");
            pDesc.setTextSize(14);
            pDesc.setTextColor(0xAAFFFFFF);
            pDesc.setPadding(0, dp(8), 0, dp(16));
            permCard.addView(pDesc);

            Button btn = new Button(getContext());
            btn.setText("Grant Permission");
            btn.setTextColor(Color.WHITE);
            FrostedCard.applyInteractive(btn, 14);
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                getContext().startActivity(intent);
            });
            permCard.addView(btn);

            contentContainer.addView(permCard);
            return;
        }

        if (filteredItems.isEmpty()) {
            LinearLayout emptyCard = new LinearLayout(getContext());
            emptyCard.setOrientation(VERTICAL);
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(24), dp(40), dp(24), dp(40));
            FrostedCard.apply(emptyCard, 18);

            TextView emptyTitle = new TextView(getContext());
            emptyTitle.setText("All caught up");
            emptyTitle.setTextSize(18);
            emptyTitle.setTextColor(fgColor);
            emptyTitle.setTypeface(Typeface.DEFAULT_BOLD);
            emptyCard.addView(emptyTitle);

            TextView emptyDesc = new TextView(getContext());
            emptyDesc.setText("No active conversations right now.");
            emptyDesc.setTextSize(14);
            emptyDesc.setTextColor(0x88FFFFFF);
            emptyDesc.setPadding(0, dp(6), 0, 0);
            emptyCard.addView(emptyDesc);

            contentContainer.addView(emptyCard);
            return;
        }

        for (MediaAccessService.ConversationItem item : filteredItems) {
            View card = createConversationCard(item);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(12));
            contentContainer.addView(card, lp);
        }
    }

    private View createConversationCard(MediaAccessService.ConversationItem item) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        FrostedCard.apply(card, 16);

        // Header: App Name, Sender, Time Ago
        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView appLabel = new TextView(getContext());
        appLabel.setText(item.appName.toUpperCase(java.util.Locale.ROOT));
        appLabel.setTextSize(10);
        appLabel.setTextColor(accentColor);
        appLabel.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(appLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(item.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        TextView timeText = new TextView(getContext());
        timeText.setText(timeAgo);
        timeText.setTextSize(11);
        timeText.setTextColor(0x77FFFFFF);
        topRow.addView(timeText);

        card.addView(topRow);

        // Sender Name
        TextView senderText = new TextView(getContext());
        senderText.setText(item.sender);
        senderText.setTextSize(16);
        senderText.setTextColor(fgColor);
        senderText.setTypeface(Typeface.DEFAULT_BOLD);
        senderText.setPadding(0, dp(4), 0, 0);
        card.addView(senderText);

        // Group title if present
        if (item.isGroup && item.conversationTitle != null && !item.conversationTitle.isEmpty()) {
            TextView groupText = new TextView(getContext());
            groupText.setText("in " + item.conversationTitle);
            groupText.setTextSize(12);
            groupText.setTextColor(0x99FFFFFF);
            card.addView(groupText);
        }

        // Message text
        TextView msgText = new TextView(getContext());
        msgText.setText(item.text);
        msgText.setTextSize(14);
        msgText.setTextColor(0xDDFFFFFF);
        msgText.setPadding(0, dp(6), 0, dp(10));
        card.addView(msgText);

        // Direct Inline Reply Row
        if (item.canReply()) {
            LinearLayout replyRow = new LinearLayout(getContext());
            replyRow.setOrientation(HORIZONTAL);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyRow.setPadding(dp(10), dp(4), dp(6), dp(4));
            FrostedCard.apply(replyRow, 12);

            EditText replyInput = new EditText(getContext());
            replyInput.setHint("Reply to " + item.sender + "...");
            replyInput.setHintTextColor(0x77FFFFFF);
            replyInput.setTextColor(Color.WHITE);
            replyInput.setTextSize(13);
            replyInput.setBackground(null);
            replyInput.setSingleLine(true);
            replyInput.setImeOptions(EditorInfo.IME_ACTION_SEND);

            TextView sendButton = new TextView(getContext());
            sendButton.setText("Send");
            sendButton.setTextSize(13);
            sendButton.setTextColor(accentColor);
            sendButton.setTypeface(Typeface.DEFAULT_BOLD);
            sendButton.setPadding(dp(12), dp(6), dp(8), dp(6));

            sendButton.setOnClickListener(v -> {
                String reply = replyInput.getText().toString();
                if (reply.trim().isEmpty()) return;

                boolean success = MediaAccessService.sendReply(getContext(), item, reply);
                if (success) {
                    replyInput.setText("");
                    Toast.makeText(getContext(), "Reply sent to " + item.sender, Toast.LENGTH_SHORT).show();
                    // Hide soft keyboard
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(replyInput.getWindowToken(), 0);
                } else {
                    Toast.makeText(getContext(), "Failed to send reply", Toast.LENGTH_SHORT).show();
                }
            });

            replyInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendButton.performClick();
                    return true;
                }
                return false;
            });

            replyRow.addView(replyInput, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            replyRow.addView(sendButton);

            LayoutParams replyParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            replyParams.setMargins(0, 0, 0, dp(8));
            card.addView(replyRow, replyParams);
        }

        // Action Buttons Row (Open App | Dismiss)
        LinearLayout actionsRow = new LinearLayout(getContext());
        actionsRow.setOrientation(HORIZONTAL);
        actionsRow.setGravity(Gravity.END);

        TextView openBtn = new TextView(getContext());
        openBtn.setText("Open");
        openBtn.setTextSize(12);
        openBtn.setTextColor(0xAAFFFFFF);
        openBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        openBtn.setOnClickListener(v -> {
            if (item.contentIntent != null) {
                try {
                    item.contentIntent.send();
                } catch (PendingIntent.CanceledException ignored) {}
            }
        });
        actionsRow.addView(openBtn);

        TextView dismissBtn = new TextView(getContext());
        dismissBtn.setText("Dismiss");
        dismissBtn.setTextSize(12);
        dismissBtn.setTextColor(0x77FFFFFF);
        dismissBtn.setPadding(dp(10), dp(4), dp(4), dp(4));
        dismissBtn.setOnClickListener(v -> {
            MediaAccessService.dismissNotification(item.key);
        });
        actionsRow.addView(dismissBtn);

        card.addView(actionsRow);

        return card;
    }
}
