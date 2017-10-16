package com.testapp.billing;


/**
 * 
 * MySku enum contains all In App Purchase products definition that the sample
 * app will use. The product definition includes two properties: "SKU" and
 * "Available Marketplace".
 * 
 */
public enum MySku {

    //The only subscription product used in this sample app
    MY_PREMIUM_SUBS("com.testapp.amazontvsample.premium", "US");

    private final String sku;
    private final String availableMarkpetplace;

    /**
     * Returns the Sku string of the MySku object
     * @return
     */
    public String getSku() {
        return this.sku;
    }

    /**
     * Returns the Available Marketplace of the MySku object
     * @return
     */
    public String getAvailableMarketplace() {
        return this.availableMarkpetplace;
    }

    private MySku(final String sku, final String availableMarkpetplace) {
        this.sku = sku;
        this.availableMarkpetplace = availableMarkpetplace;
    }

    /**
     * Returns the MySku object from the specified Sku and marketplace value.
     * @param sku
     * @param marketplace
     * @return
     */
    public static MySku fromSku(final String sku, final String marketplace) {
        if (MY_PREMIUM_SUBS.getSku().equals(sku) && (null == marketplace || MY_PREMIUM_SUBS.getAvailableMarketplace()
                .equals(marketplace))) {
            return MY_PREMIUM_SUBS;
        }
        return null;
    }

}
