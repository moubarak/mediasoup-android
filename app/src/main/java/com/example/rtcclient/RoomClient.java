package com.example.rtcclient;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import org.junit.Assert;
import androidx.annotation.WorkerThread;

import com.example.rtcclient.integration.mediasoup.PeerConnectionUtils;
import com.example.rtcclient.network.ISignalingStrategy;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.droid.Consumer;
import org.mediasoup.droid.Device;
import org.mediasoup.droid.Logger;
import org.mediasoup.droid.MediasoupException;
import org.mediasoup.droid.Producer;
import org.mediasoup.droid.RecvTransport;
import org.mediasoup.droid.SendTransport;
import org.mediasoup.droid.Transport;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RoomClient contains all the meeting control logic ported from client.js with the following differences
 * - The client auto-joins the room
 * - The client auto-subscribes to any peer sending data (at a time)
 */
public class RoomClient {

    private static final String TAG = "RoomClient";
    private static final String mMyPeerId = uuidv4();
    private static String mMediaPeerId;
    private boolean mJoined;
    private boolean mCamEnabled;
    private boolean mMicEnabled;
    private Handler mWorkHandler;
    private Handler mMainHandler;
    private JSONObject mLastPollSyncData;
    private Map<String, Producer> mProducers;
    private Map<String, Consumer> mConsumers;
    private Map<String, Consumer> mPeerVideoConsumerMap;
    private Map<String, Consumer> mPeerAudioConsumerMap;
    private ISignalingStrategy.ISignalListener mListener;

    /**
     * Interfaces with mediasoup-client-android
     */
    private PeerConnectionUtils mPeerConnectionUtils;

    /**
     * Holds the signaling protocol
     */
    private ISignalingStrategy mRtcClient;

    /**
     * Activity lever context for UI purposes
     */
    private Activity mContext;

    /**
     * TODO (mohamed): Move mediasoup-related vars to a separate class
     */
    private SendTransport.Listener sendTransportListener = new SendTransport.Listener() {

        private final String listenerTAG = TAG + "_SendTrans";

        // tell the server what it needs to know from us in order to set
        // up a server-side producer object, and get back a
        // producer.id. call callback() on success or errback() on
        // failure.
        @Override
        public String onProduce(Transport transport, String kind, String rtpParameters, String appData) {
//                    if (mClosed) {
//                        return "";
//                    }
            Logger.d(listenerTAG, "onProduce() ");

            String producerId = "";
            JSONObject params = new JSONObject();
            String endpoint = "send-track";

            try {
                params.putOpt("transportId", transport.getId());
                params.putOpt("kind", kind);
                params.putOpt("rtpParameters", new JSONObject(rtpParameters));
                params.putOpt("paused", false);
                params.putOpt("appData", new JSONObject().put("mediaTag", kind.equals("video") ? "cam-video" : "cam-audio"));

                JSONObject objresult = syncSig(endpoint, params);
                producerId = objresult.optString("id");

            } catch (JSONException e) {
                e.printStackTrace();
            }

            return producerId;
        }

        @Override
        public void onConnect(Transport transport, String dtlsParameters) {
//                    if (mClosed) {
//                        return;
//
            Logger.d(listenerTAG + "_send", "onConnect()");

            JSONObject params = new JSONObject();
            String endpoint = "connect-transport";

            try {
                params.putOpt("transportId", transport.getId());
                params.putOpt("dtlsParameters", new JSONObject(dtlsParameters));

                syncSig(endpoint, params);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // for this simple demo, any time a transport transitions to closed,
        // failed, or disconnected, leave the room and reset
        @Override
        public void onConnectionStateChange(Transport transport, String connectionState) {
            Logger.d(listenerTAG, "onConnectionStateChange: " + connectionState);
        }
    };
    private RecvTransport.Listener recvTransportListener = new RecvTransport.Listener() {

        private final String listenerTAG = TAG + "_RecvTrans";

        @Override
        public void onConnect(Transport transport, String dtlsParameters) {
//                    if (mClosed) {
//                        return;
//                    }
            Logger.d(listenerTAG, "onConnect()");

            JSONObject params = new JSONObject();
            String endpoint = "connect-transport";

            try {
                params.putOpt("transportId", transport.getId());
                params.putOpt("dtlsParameters", new JSONObject(dtlsParameters));

                syncSig(endpoint, params);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConnectionStateChange(Transport transport, String connectionState) {
            Logger.d(listenerTAG, "onRecvConnectionStateChange: " + connectionState);
//                    if (connectionState.equalsIgnoreCase("connected")) {
//                        resumeConsumer(mConsumers.entrySet().iterator().next().getValue().mConsumer);
//                    }
        }
    };
    private Device mMediasoupDevice;
    private SendTransport mSendTransport;
    private RecvTransport mRecvTransport;
    private AudioTrack mLocalAudioTrack;
    private Producer mMicProducer;
    private Producer mCamProducer;
    private VideoTrack mLocalVideoTrack;
    private VideoTrack mRemoteVideoTrack;

    //region Interface

    /**
     * Initialize
     * @param rtcClient signaling protocol
     * @param ctx Activity level context
     */
    public RoomClient(ISignalingStrategy rtcClient, Activity ctx) {
        try {
            mContext = ctx;
            mRtcClient = rtcClient;

            reset();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by the permission handler in the Activity
     */
    public void joinRoom() {
        mWorkHandler.post(() -> joinRoomImpl());
    }

    /**
     * Leave and destroy
     */
    public void leaveRoom() {

        asyncSig("leave", new JSONObject());

        mWorkHandler.post(() -> mRtcClient.unRegister());

        if (!mJoined) {
            return;
        }

        Logger.d(TAG, "leaveRoom()");

        mJoined = false;

        mCamProducer.close();
        mMicProducer.close();

        mCamProducer = null;
        mMicProducer = null;

        if (mRecvTransport != null) {
            mRecvTransport.close();
        }

        if (mSendTransport != null) {
            mSendTransport.close();
        }

//        sendTransportListener = null;
//        recvTransportListener = null;

        mSendTransport.dispose();
        mRecvTransport.dispose();

        mSendTransport = null;

        mMicEnabled = false;
        mCamEnabled = false;

        mLocalAudioTrack.setEnabled(false);
        mLocalVideoTrack.setEnabled(false);

        mLocalVideoTrack.dispose();
        mLocalAudioTrack.dispose();

        mLocalVideoTrack = null;
        mLocalAudioTrack = null;

        mConsumers = null;
        mProducers = null;
        mPeerAudioConsumerMap = null;
        mPeerVideoConsumerMap = null;
        mLastPollSyncData = null;
        mListener = null;

        mMediasoupDevice.dispose();
        mWorkHandler.post(()->mPeerConnectionUtils.dispose());

        mWorkHandler.getLooper().quitSafely();
        mWorkHandler = null;
        mMainHandler = null;
    }

    /**
     * Resume a paused video
     */
    public void startCamera() {
        Logger.d(TAG, "startCamera()");
        if (mPeerConnectionUtils != null) {
            if (mWorkHandler != null) {
                mWorkHandler.post(() -> {
                    mPeerConnectionUtils.startCamCapture();
                });
            }
        }
    }

    /**
     * Pause an existing video
     */
    public void stopCamera() {
        Logger.d(TAG, "stopCamera()");
        if (mPeerConnectionUtils != null) {
            if (mWorkHandler != null) {
                mWorkHandler.post(() -> mPeerConnectionUtils.stopCamCapture());
            }
        }
    }

    public void switchCamera(CameraVideoCapturer.CameraSwitchHandler handler) {
        Logger.d(TAG, "switchCamera()");
        mWorkHandler.post(() -> mPeerConnectionUtils.switchCam(handler));
    }

    public void muteMic() {
        Logger.d(TAG, "muteMic()");
        mWorkHandler.post(this::muteMicImpl);
    }

    public void unMuteMic() {
        Logger.d(TAG, "unmuteMic()");
        mWorkHandler.post(this::unMuteMicImpl);
    }

    /**
     * TODO (mohamed): seamless switching between signaling implementations (i.e protoo, etc.)
     */
    public void changeSignalingStrategy(ISignalingStrategy rtcClient) {
        /**
         * It should look something like this
         */
        // this.mRtcClient = rtcClient;
    }

    //endregion Interface

    //region Control

    @WorkerThread
    private void joinRoomImpl() {
        if (mJoined) {
            return;
        }

        Log.d(TAG, "joinRoomImpl()");

        JSONObject params = new JSONObject();
        String endpoint = "join-as-new-peer";

        try {
            JSONObject response = syncSig(endpoint, params);
            if (response instanceof JSONObject) {

                String routerRtpCapabilities = response.optString("routerRtpCapabilities");
                mMediasoupDevice.load(routerRtpCapabilities);

                boolean canSendMic = mMicEnabled;
                boolean canSendCam = mCamEnabled;

//                if (!mMicEnabled) {
                    canSendMic = mMediasoupDevice.canProduce("audio");
//                }
//                if (!mCamEnabled) {
                    canSendCam = mMediasoupDevice.canProduce("video");
//                }

                /**
                 * Create video/audio
                 */
//                if (canSendMic && !mMicEnabled) {
//                    mMicEnabled = true;
                    mWorkHandler.post(() -> enableMic());
//                }
//                if (canSendCam && !mCamEnabled) {
//                    mCamEnabled = true;
                    mWorkHandler.post(() -> enableCam());
//                }

                /**
                 * Automatically (no user interaction) create send and recv transport
                  */
                createSendTransport();
                createRecvTransport();
            } else {
                /**
                 * Server error? Try again
                 * TODO (mohamed): Implement exponential backoff
                 */
                mJoined = false;
                mMainHandler.post(() -> joinRoom());
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MediasoupException e) {
            e.printStackTrace();
        }

        mJoined = true;

        JSONObject data = new JSONObject();

        try {
            data.putOpt("peerId", mMyPeerId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mRtcClient.register(data, mListener);
    }

    /**
     * Update logic. Compare the peer list with the cached list from last poll and determine
     * if we want to subscribe, remove peers, or close consumers
     * TODO: Rename this to something more protocol-agnostic
     */
    @WorkerThread
    private void pollAndUpdate(JSONObject response) {

        if (response == null) {
            return;
        }

        JSONObject peers = response.optJSONObject("peers");

        if (peers == null) {
            return;
        }

        // Logger.d(TAG, "lastPollSyncData:");
        /**
         * Go through the updated list of peers
         */
        for (Iterator<String> it = peers.keys(); it.hasNext();) {
            String peerId = it.next();

            Logger.d(TAG, "Peer : " + peerId + " is me : " + peerId.equals(mMyPeerId));
            if (mLastPollSyncData != null && !(mLastPollSyncData.opt(peerId) instanceof JSONObject)) {
                Logger.d(TAG, "New peer " + peerId + " has joined");
            }

            /**
             * Ignore Me
             */
            if (peerId.equals(mMyPeerId)) {
                continue;
            }

            /**
             * Check if this peer is sending video/audio
             */
            boolean isSendingVideo = peers.optJSONObject(peerId).optJSONObject("media").opt("cam-video") instanceof JSONObject;
            boolean isSendingAudio = peers.optJSONObject(peerId).optJSONObject("media").opt("cam-audio") instanceof JSONObject;

            /**
             * mMediaPeerId is the peer id we're currently subscribed/subscribing to
             * Auto subscribe to the first peer on the list that is streaming. Ignore other peers
              */
            if (mMediaPeerId == null) {
                if (isSendingVideo || isSendingAudio) {
                    Assert.assertEquals(mPeerVideoConsumerMap.isEmpty(), mPeerAudioConsumerMap.isEmpty());
                    mMediaPeerId = peerId;
                }
            } else if (!peerId.equals(mMediaPeerId)) {
                continue;
            }

            /**
             * mPeerVideoConsumerMap maintains our subscribed video consumer.
             * Subscribe to video/audio if the peer is sending but we're not subscribed.
             * On the other hand, unsubscribe if the peer stopped sending video/audio
             */
            if (isSendingVideo && !mPeerVideoConsumerMap.containsKey(peerId)) {
                mWorkHandler.post(() -> subscribeToVideo(peerId));
            }
            else if (!isSendingVideo && mPeerVideoConsumerMap.containsKey(peerId)) {
                Logger.d(TAG, "peer " + peerId + " has stopped transmitting video");
                closeConsumer(mPeerVideoConsumerMap.get(peerId), peerId);
            }

            /**
             * mPeerAudioConsumerMap maintains our subscribed audio consumer.
             */
            if (isSendingAudio && !mPeerAudioConsumerMap.containsKey(peerId)) {
                mWorkHandler.post(() -> subscribeToAudio(peerId));
            }
            else if (!isSendingAudio && mPeerAudioConsumerMap.containsKey(peerId)) {
                Logger.d(TAG, "peer " + peerId + " has stopped transmitting audio");
                closeConsumer(mPeerAudioConsumerMap.get(peerId), peerId);
            }
        }

        /**
         * Check we're never in a wrong state
         * TODO (mohamed): Add more checks when only one kind is subscribed
         */
        if (!mPeerVideoConsumerMap.isEmpty() && !mPeerAudioConsumerMap.isEmpty()) {
            Assert.assertEquals(mPeerVideoConsumerMap.keySet(), mPeerAudioConsumerMap.keySet());
            Assert.assertEquals(mPeerVideoConsumerMap.keySet().size(), 1);
            Assert.assertEquals(mPeerAudioConsumerMap.keySet().size(), 1);
            Assert.assertEquals((String)mPeerVideoConsumerMap.keySet().iterator().next(), mMediaPeerId);
            Assert.assertEquals((String)mPeerAudioConsumerMap.keySet().iterator().next(), mMediaPeerId);
        }

        /**
         * Go through the cached list and detect if the peer we're subscribed to has
         * left the room
         */
        if (mLastPollSyncData != null) {
            for (Iterator<String> it = mLastPollSyncData.keys(); it.hasNext(); ) {
                String cachedPeerId = it.next();

                /**
                 * Ignore Me
                 */
                if (cachedPeerId.equals(mMyPeerId)) {
                    continue;
                }

                /**
                 * If the cached peer is not in the updated list unsubscribe
                 */
                if (!(peers.opt(cachedPeerId) instanceof JSONObject)) {
                    Logger.d(TAG, "peer " + cachedPeerId + " has left the room");
                    Consumer videoConsumer = mPeerVideoConsumerMap.get(cachedPeerId);
                    Consumer audioConsumer = mPeerAudioConsumerMap.get(cachedPeerId);
                    if (videoConsumer != null) {
                        closeConsumer(videoConsumer, cachedPeerId);
                    }
                    if (audioConsumer != null) {
                        closeConsumer(audioConsumer, cachedPeerId);
                    }
                    /**
                     * Reset the remote peer id so we can look for another next poll
                     */
                    mMediaPeerId = null;
                }
            }
        }

        mLastPollSyncData = peers;
    }

    @WorkerThread
    private void subscribeToVideo(String mediaPeerId) {

        JSONObject params = new JSONObject();
        String endpoint = "recv-track";

        try {

            params.putOpt("rtpCapabilities", new JSONObject(mMediasoupDevice.getRtpCapabilities()));
            params.putOpt("mediaTag", "cam-video");
            params.putOpt("mediaPeerId", mediaPeerId);

            JSONObject data = syncSig(endpoint, params);

            String peerId = mediaPeerId;
            String producerId = data.optString("producerId");
            String id = data.optString("id");
            String kind = data.optString("kind");
            String rtpParameters = data.optString("rtpParameters");

            /**
             * mRecvTransport doesn't like it when we pass appData
              */
            Consumer consumer = mRecvTransport.consume(
                    c -> {
                        mConsumers.remove(c.getId());
                        Logger.w(TAG, "onTransportClose for consume");
                    },
                    id,
                    producerId,
                    kind,
                    rtpParameters);

            mConsumers.put(consumer.getId(), consumer);
            mPeerVideoConsumerMap.put(peerId, consumer);

            /**
             * We just subscribed. Update the rendering UI if needed
             */
            if (kind.equals("video")) {
                mMainHandler.post(() -> {
                        SurfaceViewRenderer renderer = mContext.findViewById(R.id.remote_video_renderer);
                        mRemoteVideoTrack = (VideoTrack) consumer.getTrack();
                        mRemoteVideoTrack.addSink(renderer);
                });
            }

            resumeConsumer(consumer);

        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "\"newConsumer\" request failed:", e);
        }
    }

    @WorkerThread
    private void subscribeToAudio(String mediaPeerId) {

        JSONObject params = new JSONObject();
        String endpoint = "recv-track";

        try {

            params.putOpt("rtpCapabilities", new JSONObject(mMediasoupDevice.getRtpCapabilities()));
            params.putOpt("mediaTag", "cam-audio");
            params.putOpt("mediaPeerId", mediaPeerId);

            JSONObject data = syncSig(endpoint, params);

            String peerId = mediaPeerId;
            String producerId = data.optString("producerId");
            String id = data.optString("id");
            String kind = data.optString("kind");
            String rtpParameters = data.optString("rtpParameters");
            String type = data.optString("type");
            String appData = data.optString("appData");
            boolean producerPaused = data.optBoolean("producerPaused");

            Consumer consumer = mRecvTransport.consume(
                    c -> {
                        mConsumers.remove(c.getId());
                        Logger.w(TAG, "onTransportClose for consume");
                    },
                    id,
                    producerId,
                    kind,
                    rtpParameters);

            mConsumers.put(consumer.getId(), consumer);
            Logger.d(TAG, consumer.getAppData());
            mPeerAudioConsumerMap.put(peerId, consumer);

            resumeConsumer(consumer);

        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "\"newConsumer\" request failed:", e);
        }
    }

    @WorkerThread
    private void enableCam() {
        Logger.d(TAG, "enableCam()");
        try {
            if (mCamProducer != null) {
                return;
            }
            if (!mMediasoupDevice.isLoaded()) {
                Logger.w(TAG, "enableCam() | not loaded");
                return;
            }
            if (!mMediasoupDevice.canProduce("video")) {
                Logger.w(TAG, "enableCam() | cannot produce video");
                return;
            }
            if (mSendTransport == null) {
                Logger.w(TAG, "enableCam() | mSendTransport doesn't ready");
                return;
            }

            if (mLocalVideoTrack == null) {
                mLocalVideoTrack = mPeerConnectionUtils.createVideoTrack(Application.context.getApplicationContext(), "cam");
                mLocalVideoTrack.setEnabled(true);
            }

            /**
             * Render local video
             */
            mMainHandler.post(() -> {
                SurfaceViewRenderer renderer = mContext.findViewById(R.id.video_renderer);
                mLocalVideoTrack.addSink(renderer);
            });

            mCamProducer = mSendTransport.produce(
                    producer -> {
                        Logger.e(TAG, "onTransportClose(), camProducer");
                        if (mCamProducer != null) {
                            mProducers.remove(mCamProducer.getId());
                            mCamProducer = null;
                        }
                    },
                    mLocalVideoTrack,
                    null,
                    null);

            mProducers.put(mCamProducer.getId(), mCamProducer);
        } catch (MediasoupException e) {
            e.printStackTrace();
            Logger.e(TAG, "enableWebcam() | failed:", e);
            if (mLocalVideoTrack != null) {
                mLocalVideoTrack.setEnabled(false);
            }
        }
    }

    @WorkerThread
    private void enableMic() {
        Logger.d(TAG, "enableMic()");
        try {
            if (mMicProducer != null) {
                return;
            }
            if (!mMediasoupDevice.isLoaded()) {
                Logger.w(TAG, "enableMic() | not loaded");
                return;
            }
            if (!mMediasoupDevice.canProduce("audio")) {
                Logger.w(TAG, "enableMic() | cannot produce audio");
                return;
            }
            if (mSendTransport == null) {
                Logger.w(TAG, "enableMic() | mSendTransport doesn't ready");
                return;
            }
            if (mLocalAudioTrack == null) {
                mLocalAudioTrack = mPeerConnectionUtils.createAudioTrack(Application.context.getApplicationContext(), "mic");
                mLocalAudioTrack.setEnabled(true);
            }
            mMicProducer = mSendTransport.produce(
                    producer -> {
                        Logger.e(TAG, "onTransportClose(), micProducer");
                        if (mMicProducer != null) {
                            mProducers.remove(mMicProducer.getId());
                            mMicProducer = null;
                        }
                    },
                    mLocalAudioTrack,
                    null,
                    null);
            mProducers.put(mMicProducer.getId(), mMicProducer);
        } catch (MediasoupException e) {
            e.printStackTrace();
            Logger.e(TAG, "enableMic() | failed:", e);

            if (mLocalAudioTrack != null) {
                mLocalAudioTrack.setEnabled(false);
            }
        }
    }

    @WorkerThread
    private void closeConsumer(Consumer consumer, String peerId) {
        Logger.d(TAG, "closeConsumer() " + consumer.getId());

        JSONObject params = new JSONObject();
        String endpoint = "close-consumer";

        try {
            params.putOpt("consumerId", consumer.getId());
            syncSig(endpoint, params);
            consumer.close();

            try {
                if (consumer.getKind().equals("video")) {
                    mPeerVideoConsumerMap.remove(peerId);
                    SurfaceViewRenderer renderer = mContext.findViewById(R.id.remote_video_renderer);
                    mRemoteVideoTrack.removeSink(renderer);
                    renderer.clearImage();
                }
                else
                    mPeerAudioConsumerMap.remove(peerId);

                mConsumers.remove(consumer.getId());
            } catch (UnsupportedOperationException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "closeConsumer() | failed:", e);
        }
    }

    @WorkerThread
    private void resumeConsumer(Consumer consumer) {
        Logger.d(TAG, "resumeConsumer() " + consumer.getId());

        JSONObject params = new JSONObject();
        String endpoint = "resume-consumer";

        try {
            params.putOpt("consumerId", consumer.getId());
            syncSig(endpoint, params);
            consumer.resume();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "resumeConsumer() | failed:", e);
        }
    }

    @WorkerThread
    private void muteMicImpl() {
        Logger.d(TAG, "muteMicImpl()");

        JSONObject params = new JSONObject();
        String endpoint = "pause-producer";

        try {
            params.putOpt("producerId", mMicProducer.getId());
            syncSig(endpoint, params);
            mMicProducer.pause();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "muteMicImpl() | failed:", e);
        }
    }

    @WorkerThread
    private void unMuteMicImpl() {
        Logger.d(TAG, "unmuteMicImpl()");

        JSONObject params = new JSONObject();
        String endpoint = "resume-producer";

        try {
            params.putOpt("producerId", mMicProducer.getId());
            syncSig(endpoint, params);
            mMicProducer.resume();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "unmuteMicImpl() | failed:", e);
        }
    }

    //endregion Control

    //region Signaling

    /**
     * Synchronous signaling oblivious of actual protocol
     * @param endpoint path
     * @param data parameters
     * @return blocking
     */
    private JSONObject syncSig(String endpoint, JSONObject data) {

        try {
            /**
             * All signaling should send peer id
             */
            data.putOpt("peerId", mMyPeerId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        /**
         * This will block
         */
        return mRtcClient.fetch(endpoint, data);
    }

    /**
     * Non-blocking request
     * @param endpoint path
     * @param data parameters
     */
    private void asyncSig(String endpoint, JSONObject data) {
        try {
            /**
             * All signaling should send peer id
             */
            data.putOpt("peerId", mMyPeerId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mRtcClient.fetchAsync(endpoint, data);
    }

    //endregion Signaling

    //region Utilities

    public void reset() {

        mMediasoupDevice = new Device();
        mProducers = new HashMap<>();
        mConsumers = new HashMap<>();
        mPeerVideoConsumerMap = new HashMap<>();
        mPeerAudioConsumerMap = new HashMap<>();

        /**
         * Initialize the worker handler
         * */
        HandlerThread handlerThread = new HandlerThread("worker");
        handlerThread.start();
        mWorkHandler = new Handler(handlerThread.getLooper());

        /**
         * Initialize the main thread handler
         */
        mMainHandler = new Handler(Looper.getMainLooper());

        mWorkHandler.post(() -> {
            mPeerConnectionUtils = new PeerConnectionUtils();
            /**
             * TODO: don't hardcode default values
             */
            PeerConnectionUtils.setPreferCameraFace("front");
        });

        mListener = new ISignalingStrategy.ISignalListener() {
            @Override
            public void onResponse(JSONObject response) {
                if (mWorkHandler != null) {
                    mWorkHandler.post(() -> pollAndUpdate(response));
                }
            }
        };
    }

    /**
     * Utility function to create a transport and hook up signaling logic
     * appropriate to the transport's direction
     */
    @WorkerThread
    private void createSendTransport() throws JSONException, MediasoupException {
        Logger.d(TAG, "createSendTransport()");

        JSONObject params = new JSONObject();
        String endpoint = "create-transport";

        try {
            params.putOpt("direction", "send");

            JSONObject response = syncSig(endpoint, params);
            JSONObject info = null;
            info = response.getJSONObject("transportOptions");
            Logger.d(TAG, "device#createSendTransport() " + info);
            String id = info.optString("id");
            String iceParameters = info.optString("iceParameters");
            String iceCandidates = info.optString("iceCandidates");
            String dtlsParameters = info.optString("dtlsParameters");

            mSendTransport = mMediasoupDevice.createSendTransport(sendTransportListener, id, iceParameters, iceCandidates, dtlsParameters, null, null);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void createRecvTransport() throws JSONException, MediasoupException {
        Logger.d(TAG, "createRecvTransport()");

        JSONObject params = new JSONObject();
        String endpoint = "create-transport";

        try {
            params.putOpt("direction", "recv");

            JSONObject response = syncSig(endpoint, params);

            JSONObject info = null;
            info = response.getJSONObject("transportOptions");
            Logger.d(TAG, "device#createRecvTransport() " + info);
            String id = info.optString("id");
            String appData = info.optString("appData");
            String iceParameters = info.optString("iceParameters");
            String iceCandidates = info.optString("iceCandidates");
            String dtlsParameters = info.optString("dtlsParameters");

            mRecvTransport = mMediasoupDevice.createRecvTransport(recvTransportListener, id, iceParameters, iceCandidates, dtlsParameters);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ported from client.js
     * @return
     */
    private static String uuidv4() {
        Pattern pattern = Pattern.compile("[018]");
        Matcher matcher = pattern.matcher("111-111-1111");
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[1];
            random.nextBytes(bytes);
            matcher.appendReplacement(buffer, Integer.toString(bytes[0] & 15, 16));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    //endregion Utilities
}
