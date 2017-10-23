package com.testapp.utils;

/**
 * Created by Koustubh on 16-Oct-17.
 */

public class AppConstants {
    private static boolean DEBUG = true;
    private static boolean isAndroid = false;
    private static String API_DOMAIN = "";
    private static String RVS_DOMAIN= "http://192.168.0.102:8080/RVSSandbox/";// Live RVS Domain = "https://appstore-sdk.amazon.com/"

    public static String getApiDomain() {
        return API_DOMAIN;
    }

    public static String getRvsDomain() {
        return RVS_DOMAIN;
    }

    public static boolean isAndroid() {
        return isAndroid;
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

}
