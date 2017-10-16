package com.testapp.billing;

/**
 * Created by Koustubh on 06-Jun-17.
 */

public interface SubscriptionAvailabilityListener {
    void setSubscriptionAvailable(boolean productAvailable, boolean userCanSubscribe);
}
