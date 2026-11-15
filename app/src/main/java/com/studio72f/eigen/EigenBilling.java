package com.studio72f.eigen;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Play Billing for Eigen: Eigen Plus, a one-time unlock, and optional tips.
 * Plus is acknowledged and never consumed, so it stays owned. Tips unlock nothing; they are consumed so they
 * do not auto-refund and can be made repeatedly.
 * Ownership is checked against Play's own purchase cache on the phone; there is no account or license server.
 */
public final class EigenBilling implements PurchasesUpdatedListener {
    public static final String DONATION_COFFEE = "eigen_donation_coffee";
    public static final String DONATION_SNACK = "eigen_donation_snack";
    public static final String DONATION_MEAL = "eigen_donation_meal";
    public static final String DONATION_SUPPORTER = "eigen_donation_supporter";

    public static final String PLUS = "eigen_plus";
    /** Prefs that remember Plus: bought (re-checked against Play on every connection) or kept from before Plus existed. */
    public static final String PREF_OWNED = "plus_owned", PREF_GRANDFATHERED = "plus_grandfathered";

    public static final List<String> DONATION_PRODUCT_IDS = List.of(
        DONATION_COFFEE,
        DONATION_SNACK,
        DONATION_MEAL,
        DONATION_SUPPORTER
    );

    public static final class DonationTier {
        public final String id;
        public final String title;
        public final String description;
        public final String emoji;

        DonationTier(String id, String title, String description, String emoji) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.emoji = emoji;
        }
    }

    public static final List<DonationTier> TIERS = List.of(
        // No prices here: each tier shows Play's own price, in the person's currency, once Play answers.
        new DonationTier(DONATION_COFFEE, "A coffee", "Helps keep Eigen private and independent.", "☕"),
        new DonationTier(DONATION_SNACK, "A snack", "Supports ongoing maintenance and updates.", "🥐"),
        new DonationTier(DONATION_MEAL, "A meal", "Funds development of new features and tools.", "🍱"),
        new DonationTier(DONATION_SUPPORTER, "A big thank-you", "Backs Eigen as a self-funded project.", "🌟")
    );

    public interface Listener {
        void onProductDetailsUpdated();
        void onPurchaseCompleted(String productId);
        /** Plus was bought, restored or refunded. */
        default void onPlusChanged(boolean plus) { }
        /**
         * A purchase or tip couldn't start or ended in an error. [stage] is "connect", "details" or "flow";
         * [response] is Play's response code (0 when there is none). Shown in Eigen's problem sheet.
         */
        default void onPurchaseProblem(String stage, String productId, int response) { }
    }

    /** Whether Plus is unlocked: bought, or kept by an install from before Plus existed. */
    public static boolean hasPlus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_OWNED, false) || prefs.getBoolean(PREF_GRANDFATHERED, false);
    }

    // Holds only the application context, which lives as long as the process.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static EigenBilling instance;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BillingClient billingClient;
    private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean isConnecting = false;

    public static synchronized EigenBilling getInstance(Context context) {
        if (instance == null) {
            instance = new EigenBilling(context.getApplicationContext());
        }
        return instance;
    }

    private EigenBilling(Context context) {
        this.context = context;
        initBillingClient();
    }

    private void initBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        startConnection();
    }

    public void startConnection() {
        if (billingClient == null) return;
        if (billingClient.isReady()) {
            queryProductDetails();
            checkPurchases();
            return;
        }
        if (isConnecting) return;
        isConnecting = true;

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                isConnecting = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    checkPurchases();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isConnecting = false;
            }
        });
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void queryProductDetails() {
        if (billingClient == null || !billingClient.isReady()) return;

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        List<String> ids = new ArrayList<>(DONATION_PRODUCT_IDS);
        ids.add(PLUS);
        for (String id : ids) {
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            );
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && result != null) {
                List<ProductDetails> detailsList = result.getProductDetailsList();
                if (detailsList != null) {
                    for (ProductDetails details : detailsList) {
                        productDetailsMap.put(details.getProductId(), details);
                    }
                    mainHandler.post(() -> {
                        for (Listener l : listeners) l.onProductDetailsUpdated();
                    });
                }
            }
        });
    }

    /** Restores Plus from Play's purchase cache (or drops it after a refund) and consumes any tips left unconsumed. */
    private void checkPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) return;
            boolean plus = false;
            for (Purchase p : purchases) {
                if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                for (String prod : p.getProducts()) {
                    if (PLUS.equals(prod)) { plus = true; acknowledge(p); }
                    else if (DONATION_PRODUCT_IDS.contains(prod)) consumePurchase(p, prod);
                }
            }
            setPlus(plus);
        });
    }

    /** Asks Play again, for "Restore purchase". */
    public void restorePurchases() {
        if (billingClient == null || !billingClient.isReady()) { startConnection(); return; }
        checkPurchases();
    }

    private void acknowledge(Purchase purchase) {
        if (purchase.isAcknowledged()) return;
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build();
        billingClient.acknowledgePurchase(params, result -> { });
    }

    private void setPlus(boolean owned) {
        SharedPreferences prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_OWNED, false) == owned) return;
        prefs.edit().putBoolean(PREF_OWNED, owned).apply();
        boolean plus = hasPlus(context);
        mainHandler.post(() -> {
            if (owned) Toast.makeText(context, "Eigen Plus is on. Thank you!", Toast.LENGTH_LONG).show();
            for (Listener l : listeners) l.onPlusChanged(plus);
        });
    }

    /** Play's price for [productId] in the local currency, or [fallback] (null: show no price) until Play answers. */
    public String getFormattedPrice(String productId, String fallback) {
        ProductDetails details = productDetailsMap.get(productId);
        if (details != null && details.getOneTimePurchaseOfferDetails() != null) {
            return details.getOneTimePurchaseOfferDetails().getFormattedPrice();
        }
        return fallback;
    }

    public void launchPlus(Activity activity) {
        launchDonation(activity, PLUS);
    }

    public void launchDonation(Activity activity, String productId) {
        if (billingClient == null || !billingClient.isReady()) {
            startConnection();
            for (Listener l : listeners) l.onPurchaseProblem("connect", productId, 0);
            return;
        }

        ProductDetails details = productDetailsMap.get(productId);
        if (details == null) {
            queryProductDetails();
            for (Listener l : listeners) l.onPurchaseProblem("details", productId, 0);
            return;
        }

        List<BillingFlowParams.ProductDetailsParams> detailsList = Collections.singletonList(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        );

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(detailsList)
            .build();

        billingClient.launchBillingFlow(activity, flowParams);
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            checkPurchases();
            return;
        }
        int code = billingResult.getResponseCode();
        if (code != BillingClient.BillingResponseCode.OK && code != BillingClient.BillingResponseCode.USER_CANCELED) {
            mainHandler.post(() -> { for (Listener l : listeners) l.onPurchaseProblem("flow", "", code); });
            return;
        }
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    for (String prod : purchase.getProducts()) {
                        if (PLUS.equals(prod)) {
                            acknowledge(purchase);
                            setPlus(true);
                        } else if (DONATION_PRODUCT_IDS.contains(prod)) {
                            consumePurchase(purchase, prod);
                        }
                    }
                }
            }
        }
    }

    private void consumePurchase(Purchase purchase, String productId) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build();

        billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "Thank you for supporting Eigen!", Toast.LENGTH_LONG).show();
                    for (Listener l : listeners) l.onPurchaseCompleted(productId);
                });
            }
        });
    }
}
