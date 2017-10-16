package com.testapp.amazontvsample;

import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.RequestId;
import com.testapp.billing.AmazonIapManager;
import com.testapp.billing.AmazonPurchasingListener;
import com.testapp.billing.MySku;
import com.testapp.billing.SubscriptionAvailabilityListener;

import java.util.HashSet;
import java.util.Set;

public class BillingActivity extends Activity implements SubscriptionAvailabilityListener {

    private String TAG = "BillingActivity";
    private Context context;

    private Button btnPurchase;

    //Amazon IAP 2.0
    private AmazonIapManager amazonIapManager;
    private AmazonPurchasingListener purchasingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);
        context = this;
        setupIAPOnCreate();
        btnPurchase = (Button) findViewById(R.id.btnPurchase);
        btnPurchase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onSubscribe(view);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        amazonIapManager.activate();
        Log.d(TAG, "onResume: call getUserData");
        PurchasingService.getUserData();
        Log.d(TAG, "onResume: getPurchaseUpdates");
        PurchasingService.getPurchaseUpdates(true);

        Log.d(TAG, "onResume: call getProductData for skus: " + MySku.values());
        final Set<String> productSkus = new HashSet<String>();
        for (final MySku mySku : MySku.values()) {
            productSkus.add(mySku.getSku());
        }
        PurchasingService.getProductData(productSkus);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (amazonIapManager != null)
            amazonIapManager.deactivate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (amazonIapManager != null)
            amazonIapManager.deactivate();
    }

    public void onSubscribe(final View view) {
        final RequestId requestId = PurchasingService.purchase(MySku.MY_PREMIUM_SUBS.getSku());
        Log.d(TAG, "onSubscribeClick: requestId (" + requestId + ")");
    }

    private void setupIAPOnCreate() {
        amazonIapManager = new AmazonIapManager(context, this);
        purchasingListener = new AmazonPurchasingListener(amazonIapManager);
        Log.d(TAG, "onCreate: registering PurchasingListener");
        PurchasingService.registerListener(getApplicationContext(), purchasingListener);
    }


    @Override
    public void setSubscriptionAvailable(boolean productAvailable, boolean userCanSubscribe) {
        Log.i("onPurchaseUpdates", productAvailable + " = " + userCanSubscribe);
        if (productAvailable) {
            if (userCanSubscribe) {
                btnPurchase.setEnabled(true);
            } else {
                btnPurchase.setEnabled(false);
                Utils.showDialog(context,"Looks like you already purchased subscription, Thank you!");
            }
        } else {
            btnPurchase.setEnabled(false);
            Utils.showDialog(context,"Sorry, please try after sometiome. Subscription is not available right now.");
        }
    }
}
