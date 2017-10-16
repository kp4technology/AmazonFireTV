package com.testapp.billing;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Sample SQLiteHelper for the purchase record table
 * 
 */
public class AmazonSQLiteHelper extends SQLiteOpenHelper {
    //table name
    public static final String TABLE_SUBSCRIPTIONS = "subscriptions";
    //receipt id
    public static final String COLUMN_RECEIPT_ID = "receipt_id";
    //amazon user id
    public static final String COLUMN_USER_ID = "user_id";
    //subscription valid from date
    public static final String COLUMN_DATE_FROM = "date_from";
    //subscription valid to date
    public static final String COLUMN_DATE_TO = "date_to";
    //subscription sku
    public static final String COLUMN_SKU = "sku";

    private static final String DATABASE_NAME = "subscriptions.db";
    private static final int DATABASE_VERSION = 1;

    // Database creation sql statement
    private static final String DATABASE_CREATE = "create table " + TABLE_SUBSCRIPTIONS
                                                  + "("
                                                  + COLUMN_RECEIPT_ID
                                                  + " text primary key not null, "
                                                  + COLUMN_USER_ID
                                                  + " text not null, "
                                                  + COLUMN_DATE_FROM
                                                  + " integer not null, "
                                                  + COLUMN_DATE_TO
                                                  + " integer, "
                                                  + COLUMN_SKU
                                                  + " text not null"
                                                  + ");";

    public AmazonSQLiteHelper(final Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(final SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Log
            .w(AmazonSQLiteHelper.class.getName(), "Upgrading database from version " + oldVersion
                                                   + " to "
                                                   + newVersion);
        // do nothing in the sample
    }

}