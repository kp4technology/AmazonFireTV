package com.testapp.billing;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserData;
import com.testapp.amazontvsample.R;
import com.testapp.amazontvsample.Utils;
import com.testapp.interfaces.WebServices;
import com.testapp.utils.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


/**
 * This is a sample of how an application may handle InAppPurchasing. The major
 * functions includes
 * <ul>
 * <li>Simple user and subscription history management</li>
 * <li>Grant subscription purchases</li>
 * <li>Enable/disable subscribe from GUI</li>
 * <li>Save persistent subscriptions data into SQLite database</li>
 * </ul>
 */
public class AmazonIapManager {
    private static final String TAG = "AmazonIAPManager";
    private final Context context;
    private final SubscriptionDataSource dataSource;

    private boolean subscriptionAvailable;
    private UserIapData userIapData;

    private SubscriptionAvailabilityListener subscriptionAvailabilityListener;

    public AmazonIapManager(Context context, SubscriptionAvailabilityListener subscriptionAvailabilityListener) {
        this.context = context;
        this.dataSource = new SubscriptionDataSource(context.getApplicationContext());
        this.subscriptionAvailabilityListener = subscriptionAvailabilityListener;
    }

    /**
     * Method to set the app's amazon user id and marketplace from IAP SDK
     * responses.
     *
     * @param newAmazonUserId
     * @param newAmazonMarketplace
     */
    public void setAmazonUserId(final String newAmazonUserId, final String newAmazonMarketplace) {
        // Reload everything if the Amazon user has changed.
        if (newAmazonUserId == null) {
            // A null user id typically means there is no registered Amazon
            // account.
            if (userIapData != null) {
                userIapData = null;
                refreshSubscriptionAvailability();
            }
        } else if (userIapData == null || !newAmazonUserId.equals(userIapData.getAmazonUserId())) {
            // If there was no existing Amazon user then either no customer was
            // previously registered or the application has just started.

            // If the user id does not match then another Amazon user has
            // registered.
            userIapData = new UserIapData(newAmazonUserId, newAmazonMarketplace);
            refreshSubscriptionAvailability();
        }
    }

    /**
     * Enable the magazine subscription.
     *
     * @param productData
     */
    public void enablePurchaseForSkus(final Map<String, Product> productData) {
        if (productData.containsKey(MySku.MY_PREMIUM_SUBS.getSku())) {
            subscriptionAvailable = true;
        }
    }

    /**
     * Disable the magazine subscription.
     *
     * @param unavailableSkus
     */
    public void disablePurchaseForSkus(final Set<String> unavailableSkus) {
        if (unavailableSkus.contains(MySku.MY_PREMIUM_SUBS.toString())) {
            subscriptionAvailable = false;
            // reasons for product not available can be:
            // * Item not available for this country
            // * Item pulled off from Appstore by developer
            // * Item pulled off from Appstore by Amazon

            Utils.showToast(context, "the subscription product isn't available now! ");
        }
    }

    /**
     * This method contains the business logic to fulfill the customer's
     * purchase based on the receipt received from InAppPurchase SDK's
     * {@linkx PurchasingListener#onPurchaseResponse} or
     * {@linkx PurchasingListener#onPurchaseUpdates} method.
     *
     * @paramx requestId
     * @paramx receiptId
     */
    public void handleSubscriptionPurchase(final Receipt receipt, final UserData userData) {
        try {
            if (receipt.isCanceled()) {
                // Check whether this receipt is for an expired or canceled
                // subscription
                revokeSubscription(receipt, userData.getUserId());
            } else {
                // We strongly recommend that you verify the receipt on
                // server-side.
                if (!verifyReceiptFromYourService(receipt.getReceiptId(), userData)) {
                    // if the purchase cannot be verified,
                    // show relevant error message to the customer.
                    Utils.showToast(context, "Purchase cannot be verified, please retry later.");
                    return;
                }
                grantSubscriptionPurchase(receipt, userData);
            }
            Log.i("onPurchaseResponse", "=> " + receipt.getReceiptId() + "=" + receipt.isCanceled() + ">>" + userData.getUserId());
            return;
        } catch (final Throwable e) {
            Utils.showToast(context, "Purchase cannot be completed, please retry");
        }

    }

    private void grantSubscriptionPurchase(final Receipt receipt, final UserData userData) {

        final MySku mySku = MySku.fromSku(receipt.getSku(), userIapData.getAmazonMarketplace());
        // Verify that the SKU is still applicable.
        if (mySku != MySku.MY_PREMIUM_SUBS) {
            Log.w(TAG, "The SKU [" + receipt.getSku() + "] in the receipt is not valid anymore ");
            // if the sku is not applicable anymore, call
            // PurchasingService.notifyFulfillment with status "UNAVAILABLE"
            PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.UNAVAILABLE);
            return;
        }
        try {
            // Set the purchase status to fulfilled for your application
            saveSubscriptionRecord(receipt, userData.getUserId());
            PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.FULFILLED);

        } catch (final Throwable e) {
            // If for any reason the app is not able to fulfill the purchase,
            // add your own error handling code here.
            Log.e(TAG, "Failed to grant entitlement purchase, with error " + e.getMessage());
        }

    }

    /**
     * Method to handle receipt
     *
     * @param requestId
     * @param receipt
     * @param userData
     */
    public void handleReceipt(final String requestId, final Receipt receipt, final UserData userData) {
        switch (receipt.getProductType()) {
            case CONSUMABLE:
                // check consumable sample for how to handle consumable purchases
                break;
            case ENTITLED:
                // check entitlement sample for how to handle consumable purchases
                break;
            case SUBSCRIPTION:
                handleSubscriptionPurchase(receipt, userData);
                break;
        }
    }

    /**
     * Show purchase failed message
     *
     * @param sku
     */
    public void purchaseFailed(final String sku) {
        Utils.showToast(context, "Purchase failed!");
    }

    public UserIapData getUserIapData() {
        return this.userIapData;
    }

    public boolean isMagazineSubsAvailable() {
        return subscriptionAvailable;
    }

    public void setMagazineSubsAvailable(final boolean magazineSubsAvailable) {
        this.subscriptionAvailable = magazineSubsAvailable;
    }

    /**
     * Disable all magezine subscriptions on UI
     */
    public void disableAllPurchases() {
        this.setMagazineSubsAvailable(false);
        refreshSubscriptionAvailability();
    }

    /**
     * Reload the magazine subscription availability
     */
    public void refreshSubscriptionAvailability() {
        boolean available = subscriptionAvailable && userIapData != null;
        subscriptionAvailabilityListener.setSubscriptionAvailable(available,
                userIapData != null && !userIapData.isSubsActiveCurrently());
    }

    /**
     * Gracefully close the database when the main activity's onStop and
     * onDestroy
     */
    public void deactivate() {
        dataSource.close();

    }

    /**
     * Connect to the database when main activity's onStart and onResume
     */
    public void activate() {
        dataSource.open();

    }

    /**
     * Reload the subscription history from database
     */
    public void reloadSubscriptionStatus() {
        final List<SubscriptionRecord> subsRecords = dataSource.getSubscriptionRecords(userIapData.getAmazonUserId());
        userIapData.setSubscriptionRecords(subsRecords);
        userIapData.reloadSubscriptionStatus();
        refreshSubscriptionAvailability();
    }

    /**
     * This sample app includes a simple SQLite implementation for save
     * subscription purchase detail locally.
     * <p>
     * We strongly recommend that you save the purchase information on a server.
     *
     * @param receipt
     * @param userId
     */
    private void saveSubscriptionRecord(final Receipt receipt, final String userId) {
        // TODO replace with your own implementation

        dataSource
                .insertOrUpdateSubscriptionRecord(receipt.getReceiptId(),
                        userId,
                        receipt.getPurchaseDate().getTime(),
                        receipt.getCancelDate() == null ? SubscriptionRecord.TO_DATE_NOT_SET
                                : receipt.getCancelDate().getTime(),
                        receipt.getSku());

//        updatePayment(context, true, userId, receipt.getReceiptId(), "amazon");

    }

    /**
     * We strongly recommend verifying the receipt on your own server side
     * first. The server side verification ideally should include checking with
     * Amazon RVS (Receipt Verification Service) to verify the receipt details.
     *
     * @param receiptId
     * @return
     * @see <a href=
     * "https://developer.amazon.com/appsandservices/apis/earn/in-app-purchasing/docs/rvs"
     * >Appstore's Receipt Verification Service</a>
     */
    private boolean verifyReceiptFromYourService(final String receiptId, final UserData userData) {
        // TODO Add your own server side accessing and verification code
        verifyReceipt(context, true, userData.getUserId(), receiptId);
        return false;
    }

    /**
     * Private method to revoke a subscription purchase from the customer
     * <p>
     * Please implement your application-specific logic to handle the revocation
     * of a subscription purchase.
     *
     * @param receipt
     * @param userId
     */

    private void revokeSubscription(final Receipt receipt, final String userId) {
        final String receiptId = receipt.getReceiptId();
        dataSource.cancelSubscription(receiptId, receipt.getCancelDate().getTime());
    }


    public void updatePurchaseDetails(String userId, String receiptId) {
        Log.i(TAG, "=> " + userId + ", " + receiptId);
//        updatePayment(context, true, userId, receiptId, "amazon");
    }

//    private void updatePayment(final Context context, final boolean showProgress, String amuserid, String amreceiptid, String vendor) {
//
//        String os = "";
//        if (AppConstants.isAndroid())
//            os = "androidtv";
//        else
//            os = "fire";
//
//        SharedPreferences settings = setUserDefaultsAndPreferences(null, context);
//        String device = settings.getString("device_id", "#FFFFFF");
//
//        if (!isConnectingToInternet(context)) {
//            showDialog(context, "Please check your internet connection.", "Okay");
//            return;
//        }
//
//        final ProgressDialog pDialog = new ProgressDialog(context);
//
//        if (showProgress) {
//            pDialog.setMessage("Updating...");
//            pDialog.setIndeterminate(true);
//            pDialog.setCancelable(false);
//            pDialog.show();
//        }
//
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(AppConstants.getApiDomain())
//                .addConverterFactory(GsonConverterFactory.create())
//                .build();
//
//        WebServices webServices = retrofit.create(WebServices.class);
//
//        Log.i("updatePayment", "\namuserid = " + amuserid + "\n" + "amreceiptid = " + amreceiptid + "\n"
//                + "vendor = " + vendor + "\n membership = One_Month" + "\n"
//                + "amsharedid = " + context.getString(R.string.iap_shared_secret_key)
//                + "\n" + "os = " + os + ", device = " + device);
//
//        Call call = webServices.updatePayment(amuserid, amreceiptid, vendor, "One_Month", context.getString(R.string.iap_shared_secret_key), os, device);
//
//
//        call.enqueue(new Callback() {
//            @Override
//            public void onResponse(Call call, Response response) {
//
//                ResponseBody body = (ResponseBody) response.body();
//
//                if (response.body() == null) {
//                    if (showProgress)
//                        pDialog.dismiss();
//                    return;
//                }
//
//                try {
//                    BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
//                    StringBuilder out = new StringBuilder();
//                    String newLine = System.getProperty("line.separator");
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        out.append(line);
//                        out.append(newLine);
//                    }
//
//                    Log.i("JSON raw", "=> " + out.toString());
//
//                    JSONObject jsonObject = new JSONObject(out.toString()).getJSONObject("items");
//
//                    if (jsonObject.getBoolean("status")) {
//                        User.updateUser(true, new User.userUpdated() {
//                            @Override
//                            public void complete() {
//                                Log.v(TAG, "Update user completed after canceling");
//                                showMessage(context.getString(R.string.preferences_membership_refreshed));
//                            }
//                        });
//                    } else {
//                        showDialog(context, "Something went wrong, please try refreshing membership.", "Okay");
//                    }
//
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                if (showProgress)
//                    pDialog.dismiss();
//            }
//
//            @Override
//            public void onFailure(Call call, Throwable t) {
//                if (showProgress)
//                    pDialog.dismiss();
//            }
//        });
//    }

    private void verifyReceipt(final Context context, final boolean showProgress, String user, String receiptId) {

//        SharedPreferences settings = setUserDefaultsAndPreferences(null, context);
//        String device = settings.getString("device_id", "#FFFFFF");

        if (!isConnectingToInternet(context)) {
            showDialog(context, "Please check your internet connection.", "Okay");
            return;
        }

        final ProgressDialog pDialog = new ProgressDialog(context);

        if (showProgress) {
            pDialog.setMessage("Updating...");
            pDialog.setIndeterminate(true);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(AppConstants.getRvsDomain())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WebServices webServices = retrofit.create(WebServices.class);

        Call call = webServices.verifyReceipt(context.getString(R.string.iap_shared_secret_key), user, receiptId);


        call.enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response_main) {

                ResponseBody body = (ResponseBody) response_main.body();
                try {

                    switch (response_main.code()) {
                        case 400:
                            System.out.println("Amazon RVS Error: Invalid receiptID");
                            // Process Response Data locally
                            // Respond to app
                            break;

                        case 496:
                            System.out.println("Amazon RVS Error: Invalid developerSecret");
                            // Process Response Data locally
                            // Respond to app
                            break;

                        case 497:
                            System.out.println("Amazon RVS Error: Invalid userId");
                            // Process Response Data locally
                            // Respond to app
                            break;

                        case 500:
                            System.out.println("Amazon RVS Error: Internal Server Error");
                            // Process Response Data locally
                            // Respond to app
                            break;

                        case 200:

                            //Retrieve Amazon RVS Response
                            BufferedReader in = new BufferedReader(new InputStreamReader(body.byteStream()));

                            String inputLine;
                            StringBuffer response = new StringBuffer();

                            while ((inputLine = in.readLine()) != null) {
                                response.append(inputLine);
                            }
                            in.close();

                            //Log Amazon RVS Response
                            System.out.println("Amazon RVS Response: " + response.toString());

//                        //Create JSONObject for RVS Response
//                        JSONObject responseJson = new JSONObject(response.toString());

                            //Parse RVS Response
                            JSONObject responseJson = new JSONObject(response.toString());
                            String receiptId = responseJson.getString("receiptId");
                            String productType = responseJson.getString("productType");
                            String productId = responseJson.getString("productId");
                            long purchaseDate = responseJson.optLong("purchaseDate");
                            long cancelDate = responseJson.optLong("cancelDate");
                            boolean testTransaction = responseJson.optBoolean("testTransaction");

                        showDialog(context,"Payment Successful","Okay");

                            // Process Response Data locally
                            // Respond to app

                            break;

                        default:
                            System.out.println("Amazon RVS Error: Undefined Response Code From Amazon RVS");
                            // Process Response Data locally
                            // Respond to app
                            break;
                    }

                } catch (MalformedURLException e) {

                    // As a best practice, replace the following logic with logic for logging.
                    System.out.println("Amazon RVS MalformedURLException");
                    e.printStackTrace();
                    // Process Response Data locally
                    // Respond to app
                } catch (IOException e) {

                    // As a best practice, replace the following logic with logic for logging.
                    System.out.println("Amazon RVS IOException");
                    e.printStackTrace();
                    // Process Response Data locally
                    // Respond to app
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (showProgress)
                    pDialog.dismiss();

//                User.updateUser(true, new User.userUpdated() {
//                    @Override
//                    public void complete() {
//                        Log.v(TAG, "Update user completed after canceling");
//                        showMessage(context.getString(R.string.preferences_membership_refreshed));
//                    }
//                });
            }

            @Override
            public void onFailure(Call call, Throwable t) {
                if (showProgress)
                    pDialog.dismiss();
            }
        });
    }

    public static boolean isConnectingToInternet(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null)
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
        }
        return false;
    }

    public static void showDialog(Context context, String message, String ok_label) {
        AlertDialog.Builder buidler = new AlertDialog.Builder(context);
        buidler.setMessage(message);
        buidler.setPositiveButton(ok_label,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method
                    }
                });
        AlertDialog dialog = buidler.create();
        dialog.show();
    }

    private void showMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
