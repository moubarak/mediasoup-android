package com.example.rtcclient.network.mediasoup.socket;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.example.rtcclient.network.ISignalingStrategy;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.droid.Logger;
import org.protoojs.droid.ProtooException;

import io.reactivex.Observable;

/**
 * Sockets over Protoo directly imported from mediasoup-demo-android. Renamed and partially modified
 * to implement ISignalingStrategy. Not tested yet
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class SocketClient extends org.protoojs.droid.Peer implements ISignalingStrategy {

    private static final String TAG = "SocketClient";

    interface RequestGenerator {
        void request(JSONObject req);
    }

    public SocketClient(@NonNull WebSocketTransport transport, @NonNull Listener listener) {
        super(transport, listener);
    }

    public Observable<String> request(String method) {
        return request(method, new JSONObject());
    }

    public Observable<String> request(String method, @NonNull RequestGenerator generator) {
        JSONObject req = new JSONObject();
        generator.request(req);
        return request(method, req);
    }

    private Observable<String> request(String method, @NonNull JSONObject data) {
        Logger.d(TAG, "request(), method: " + method);
        return Observable.create(
                emitter ->
                        request(
                                method,
                                data,
                                new ClientRequestHandler() {
                                    @Override
                                    public void resolve(String data) {
                                        if (!emitter.isDisposed()) {
                                            emitter.onNext(data);
                                        }
                                    }

                                    @Override
                                    public void reject(long error, String errorReason) {
                                        if (!emitter.isDisposed()) {
                                            emitter.onError(new ProtooException(error, errorReason));
                                        }
                                    }
                                }));
    }

    @WorkerThread
    @Override
    public JSONObject fetch(String method, @NonNull JSONObject data) {
        Logger.d(TAG, "syncRequest(), method: " + method);

        JSONObject response = null;
        String result = request(method, data).blockingFirst(); // This will block

        try {
            response = new JSONObject(result);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return response;
    }

    //TODO (mohamed): Implement async
    @Override
    public void fetchAsync(String path, JSONObject params) {

    }

    // TODO (mohamed): Consolidate listeners
    @Override
    public void register(JSONObject params, ISignalListener listener) {

    }

    //TODO (mohamed): Implement
    @Override
    public void unRegister() {

    }
}
