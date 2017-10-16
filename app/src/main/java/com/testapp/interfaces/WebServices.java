package com.testapp.interfaces;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Koustubh on 16-Oct-17.
 */

public interface WebServices {

    @GET("/version/1.0/verifyReceiptId/")
    Call<ResponseBody> verifyReceipt(@Query("developer") String developer,
                                     @Query("user") String user,
                                     @Query("receiptId") String receiptId);

}
