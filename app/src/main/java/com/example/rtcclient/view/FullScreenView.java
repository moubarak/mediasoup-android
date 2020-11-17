package com.example.rtcclient.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.rtcclient.R;
import com.example.rtcclient.integration.mediasoup.PeerConnectionUtils;

import org.webrtc.SurfaceViewRenderer;

/**
 * FullScreenView renders the remote peer's video
 */
public class FullScreenView extends ConstraintLayout {

    /**
     * When implementing a custom view these constructors are required
     * @param context Activity level context
     */
    public FullScreenView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public FullScreenView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FullScreenView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     *
     * @param context Activity level context
     */
    private void init(Context context) {
        inflate(context, R.layout.view_remote, this);

        SurfaceViewRenderer renderer = findViewById(R.id.remote_video_renderer);
        renderer.init(PeerConnectionUtils.getEglContext(), null);
        renderer.setZOrderMediaOverlay(true);
    }
}
