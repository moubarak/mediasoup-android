package com.example.rtcclient;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rtcclient.network.ISignalingStrategy;
import com.example.rtcclient.network.http.HttpClient;
import com.example.rtcclient.prefs.API;
import com.example.rtcclient.prefs.SettingsActivity;
import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import org.mediasoup.droid.Logger;
import org.webrtc.CameraVideoCapturer;

/**
 * MainActivity is the application's main (and for now only) screen
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Logger tag
     */
    private static final String TAG = "MainActivity";

    /**
     * mRoomClient encapsulates everything related to RTC on the client
     */
    private RoomClient mRoomClient;

    /**
     * permissionHandler handles newly granted or existing permissions before
     * automatically joining the room
     */
    private final PermissionHandler permissionHandler = new PermissionHandler() {
        @Override
        public void onGranted() {
            Logger.d(TAG, "permissions granted");

            /**
             * Auto join the room once all permissions are granted. Which
             * are checked against on each app launch
             */
            mRoomClient.joinRoom();
        }
    };

    //region LifeCycle events

    /**
     * This is the entry point each time the app is launched
     * @param savedInstanceState should be used to save state between
     *                           destructive UI transitions such as
     *                           orientation changes but we currently assume
     *                           fixed portrait mode
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Stop the screen from auto-dimming
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ISignalingStrategy httpClient = HttpClient.getSharedInstance();
        mRoomClient = new RoomClient(httpClient, this);

        try {
            checkPermissions();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resume the camera feed when the app is resumed.
     */
    @Override
    protected void onStart() {
        super.onStart();
        mRoomClient.startCamera();
    }

    /**
     * Stop the camera geed when the app is backgrounded. Keep audio going
     */
    @Override
    protected void onStop() {
        super.onStop();
        mRoomClient.stopCamera();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.server_ip:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, 1);
                mRoomClient.leaveRoom();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 1) {
            Logger.d(TAG, "request config done");
            runOnUiThread(() -> {
                mRoomClient.reset();
                checkPermissions();
            });
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     *
     */
    @Override
    protected void onDestroy() {
        mRoomClient.leaveRoom();
        super.onDestroy();
    }

    //endregion LifeCycle events

    /**
     * Request/check permissions needed to start (Camera, Audio, Internet)
     */
    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        };
        String rationale = "Please provide permissions for the app to work";
        Permissions.Options options = new Permissions.Options().setRationaleDialogTitle("Info").setSettingsDialogTitle("Warning");
        Permissions.check(this, permissions, rationale, options, permissionHandler);
    }

    //region Actions

    /**
     * Mute/Unmute mic
     * @param view is associated the button (ImageButton)
     */
    public void toggleMic(View view) {
        Logger.d(TAG, "toggleMic()");

        runOnUiThread(() -> view.setSelected(!view.isSelected()));

        if (view.isSelected()) {
            mRoomClient.muteMic();
        } else {
            mRoomClient.unMuteMic();
        }
    }

    /**
     * Switch between front/back facing cameras
     * @param view is associated the button (ImageButton)
     */
    public void switchCamera(View view) {
        Logger.d(TAG, "switchCamera()");

        view.setEnabled(false);
        mRoomClient.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean b) {
                runOnUiThread(() -> view.setEnabled(true));
            }

            @Override
            public void onCameraSwitchError(String s) {
                runOnUiThread(() -> view.setEnabled(false));
            }
        });
    }

    //endregion Actions
}