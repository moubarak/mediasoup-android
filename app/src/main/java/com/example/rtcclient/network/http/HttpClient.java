package com.example.rtcclient.network.http;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.example.rtcclient.Application;
import com.example.rtcclient.network.ISignalingStrategy;
import com.example.rtcclient.prefs.API;

import org.json.JSONObject;
import org.mediasoup.droid.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The signaling protocol is left to us to implement. HttpClient is a basic HTTP polling
 * client based on Volley against the webRTC server
 */
public class HttpClient implements ISignalingStrategy {

    private static final String TAG = "HttpClient";
    /**
     * Singleton to make sure one-and-only one instance is going around which simplifies things initially
     */
    private static HttpClient sharedInstance;
    /**
     * Moved the polling logic inside the http client to make changing Signaling protocols easier
     */
    private final ScheduledThreadPoolExecutor mExecutor_ = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> mPollingInterval;
    private RequestQueue mRequestQueue;
    /**
     * ISignalListener handles the result of a poll request to mimic other signaling protocols. Also to
     * make changing signaling protocols easier if needed
     */
    private final ISignalListener listener;

    /**
     * Constructor for the Singleton
     */
    private HttpClient() {
        this.listener = null;
        this.mRequestQueue = getRequestQueue();
    }

    /**
     * TODO (mohamed): Replace Singleton with Factory
     *
     * @return The one instance
     */
    public static synchronized HttpClient getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new HttpClient();
        }
        return sharedInstance;
    }

    /**
     * Getter for the default headers. Handy when you want to modify them somewhere else
     * @return HashMap
     */
    private static Map<String, String> defaultHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * @return Volley's default pool
     */
    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(Application.context.getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        Log.e(TAG, req.toString());
        getRequestQueue().add(req);
    }

    /**
     * This is the polling method. It mimics other protocols that register for events
     * @param data parameters
     * @param listener handles the response logic
     */
    @Override
    public void register(JSONObject data, ISignalListener listener) {
        mPollingInterval = mExecutor_.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                JSONObject response = fetch("sync", data);
                listener.onResponse(response);
            }
        }, 0L, 1, TimeUnit.SECONDS);
    }

    /**
     * Send synchronous messages to the server via POST requests
     * @param path the method on the server
     * @param params parameters always include "my" peer id
     * @return blocks until response
     */
    @Override
    public JSONObject fetch(String path, JSONObject params) {

        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API.api + path, params, future, future) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return defaultHeaders();
            }
        };

        getSharedInstance().addToRequestQueue(request);

        JSONObject response = null;

        try {
            /**
             * This will block
             */
            response = future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();

            /**
             * TODO (mohamed): Not the nicest way to handle errors
             */
            if (VolleyError.class.isAssignableFrom(e.getCause().getClass())) {
                VolleyError volleyError = (VolleyError) e.getCause();
                Logger.e(TAG, "Volley Error = " + volleyError.toString());
                if (volleyError.networkResponse != null) {
                    Logger.e(TAG, "volleyError.networkResponse = " + volleyError.networkResponse.toString());
                    Logger.e(TAG, "volleyError.networkResponse.statusCode = " + volleyError.networkResponse.statusCode);
                    Logger.e(TAG, "volleyError.networkResponse.data = " + new String(volleyError.networkResponse.data));
                }
            }
        }

        return response;
    }

    /**
     * When the app is being killed we don't want to block until we get a response. Use this
     * synchronous POST to leave the room in certain cases
     * @param path the method in the server i.e. 'leave'
     * @param params parameters always include "my" peer id
     */
    @Override
    public void fetchAsync(String path, JSONObject params) {

        /**
         *  Override Success
         */
        Response.Listener<JSONObject> success = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject jsonObject) {

            }
        };

        /**
         * Overriding the default failure handler
         */
        Response.ErrorListener failure = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Logger.e(TAG, "Volley Error = " + volleyError.toString());
                if (volleyError.networkResponse != null) {
                    Logger.e(TAG, "volleyError.networkResponse = " + volleyError.networkResponse.toString());
                    Logger.e(TAG, "volleyError.networkResponse.statusCode = " + volleyError.networkResponse.statusCode);
                    Logger.e(TAG, "volleyError.networkResponse.data = " + new String(volleyError.networkResponse.data));
                }
            }
        };

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API.api + path, params, success, failure) {
            @Override
            public Map<String, String> getHeaders() {
                return defaultHeaders();
            }
        };

        getSharedInstance().addToRequestQueue(request);
    }
}
