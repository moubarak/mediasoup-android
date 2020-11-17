package com.example.rtcclient.network;

import org.json.JSONObject;

public interface ISignalingStrategy {

    JSONObject fetch(String path, JSONObject params);

    void fetchAsync(String path, JSONObject params);

    void register(JSONObject params, ISignalListener listener);

    interface ISignalListener {
        void onResponse(JSONObject response);
    }
}
