package com.testapp.billing;

import android.util.Log;

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserDataResponse;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of {@link PurchasingListener} that listens to Amazon
 * InAppPurchase SDK's events, and call {@linkx SampleIAPManager} to handle the
 * purchase business logic.
 */
public class AmazonPurchasingListener implements PurchasingListener {

    private static final String TAG = "IAPSubscriptionApp";

    private final AmazonIapManager amazonIapManager;

    public AmazonPurchasingListener(final AmazonIapManager amazonIapManager) {
        this.amazonIapManager = amazonIapManager;
    }

    /**
     * This is the callback for {@link PurchasingService#getUserData}. For
     * successful case, get the current user from {@link UserDataResponse} and
     * call {@linkx SampleIAPManager#setAmazonUserId} method to load the Amazon
     * user and related purchase information
     *
     * @param response
     */
    @Override
    public void onUserDataResponse(final UserDataResponse response) {
        Log.d(TAG, "onGetUserDataResponse: requestId (" + response.getRequestId()
                + ") userIdRequestStatus: "
                + response.getRequestStatus()
                + ")");

        final UserDataResponse.RequestStatus status = response.getRequestStatus();
        switch (status) {
            case SUCCESSFUL:
                Log.d(TAG, "onUserDataResponse: get user id (" + response.getUserData().getUserId()
                        + ", marketplace ("
                        + response.getUserData().getMarketplace()
                        + ") ");
                amazonIapManager.setAmazonUserId(response.getUserData().getUserId(), response.getUserData().getMarketplace());
                break;

            case FAILED:
            case NOT_SUPPORTED:
                Log.d(TAG, "onUserDataResponse failed, status code is " + status);
                amazonIapManager.setAmazonUserId(null, null);
                break;
        }
    }

    /**
     * This is the callback for {@link PurchasingService#getProductData}. After
     * SDK sends the product details and availability to this method, it will
     * call {@linkx SampleIAPManager#enablePurchaseForSkus}
     * {@linkx SampleIAPManager#disablePurchaseForSkus} or
     * {@linkx SampleIAPManager#disableAllPurchases} method to set the purchase
     * status accordingly.
     */
    @Override
    public void onProductDataResponse(final ProductDataResponse response) {
        final ProductDataResponse.RequestStatus status = response.getRequestStatus();
        Log.d(TAG, "onProductDataResponse: RequestStatus (" + status + ")");

        switch (status) {
            case SUCCESSFUL:
                Log.d(TAG, "onProductDataResponse: successful.  The item data map in this response includes the valid SKUs");
                final Set<String> unavailableSkus = response.getUnavailableSkus();
                Log.d(TAG, "onProductDataResponse: " + unavailableSkus.size() + " unavailable skus");
                amazonIapManager.enablePurchaseForSkus(response.getProductData());
                amazonIapManager.disablePurchaseForSkus(response.getUnavailableSkus());
                amazonIapManager.refreshSubscriptionAvailability();

                break;
            case FAILED:
            case NOT_SUPPORTED:
                Log.d(TAG, "onProductDataResponse: failed, should retry request");
                amazonIapManager.disableAllPurchases();
                break;
        }
    }

    /**
     * This is the callback for {@link PurchasingService#getPurchaseUpdates}.
     * <p>
     * You will receive receipts for all possible Subscription history from this
     * callback
     */
    @Override
    public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
        Log.d(TAG, "onPurchaseUpdatesResponse: requestId (" + response.getRequestId()
                + ") purchaseUpdatesResponseStatus ("
                + response.getRequestStatus()
                + ") userId ("
                + response.getUserData().getUserId()
                + ") response ("
                + response.toString()
                + " / " + response.getReceipts() + " / " + response.hasMore());
        final PurchaseUpdatesResponse.RequestStatus status = response.getRequestStatus();
        switch (status) {
            case SUCCESSFUL:
                amazonIapManager.setAmazonUserId(response.getUserData().getUserId(), response.getUserData().getMarketplace());
                for (final Receipt receipt : response.getReceipts()) {
                    amazonIapManager.handleReceipt(response.getRequestId().toString(), receipt, response.getUserData());
                    amazonIapManager.updatePurchaseDetails(response.getUserData().getUserId(), receipt.getReceiptId());
                }
                if (response.hasMore()) {
                    PurchasingService.getPurchaseUpdates(false);
                }
                Log.i(TAG, "=> " + response.getReceipts().size());
                amazonIapManager.reloadSubscriptionStatus();
                break;
            case FAILED:
            case NOT_SUPPORTED:
                Log.d(TAG, "onProductDataResponse: failed, should retry request");
                amazonIapManager.disableAllPurchases();
                break;
        }

    }

    /**
     * This is the callback for {@link PurchasingService#purchase}. For each
     * time the application sends a purchase request
     * {@link PurchasingService#purchase}, Amazon Appstore will call this
     * callback when the purchase request is completed. If the RequestStatus is
     * Successful or AlreadyPurchased then application needs to call
     * {@linkx SampleIAPManager#handleReceipt} to handle the purchase
     * fulfillment. If the RequestStatus is INVALID_SKU, NOT_SUPPORTED, or
     * FAILED, notify corresponding method of {@linkx SampleIAPManager} .
     */
    @Override
    public void onPurchaseResponse(final PurchaseResponse response) {
        final String requestId = response.getRequestId().toString();
        final String userId = response.getUserData().getUserId();
        final PurchaseResponse.RequestStatus status = response.getRequestStatus();
        Log.d(TAG, "onPurchaseResponse: requestId (" + requestId
                + ") userId ("
                + userId
                + ") purchaseRequestStatus ("
                + status
                + ")"
                + response.getReceipt().getReceiptId()
                + ")");

        switch (status) {
            case SUCCESSFUL:
                final Receipt receipt = response.getReceipt();
                Log.d(TAG, "onPurchaseResponse: receipt json:" + receipt.toJSON());
                amazonIapManager.handleReceipt(response.getRequestId().toString(), receipt, response.getUserData());
                amazonIapManager.updatePurchaseDetails(response.getUserData().getUserId(), receipt.getReceiptId());
                amazonIapManager.reloadSubscriptionStatus();
                break;
            case ALREADY_PURCHASED:
                Log.i(TAG,
                        "onPurchaseResponse: already purchased, you should verify the subscription purchase on your side and make sure the purchase was granted to customer");
                Receipt receipt1 = response.getReceipt();
                Log.i(TAG, "=> " + receipt1.getReceiptId() + ", " + receipt1.isCanceled() + ", " + userId);
                break;
            case INVALID_SKU:
                Log.d(TAG,
                        "onPurchaseResponse: invalid SKU!  onProductDataResponse should have disabled buy button already.");
                final Set<String> unavailableSkus = new HashSet<String>();
                unavailableSkus.add(response.getReceipt().getSku());
                amazonIapManager.disablePurchaseForSkus(unavailableSkus);
                break;
            case FAILED:
            case NOT_SUPPORTED:
                Log.d(TAG, "onPurchaseResponse: failed so remove purchase request from local storage");
                amazonIapManager.purchaseFailed(response.getReceipt().getSku());
                break;
        }

    }

}
