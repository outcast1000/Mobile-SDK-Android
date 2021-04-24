package com.dji.sdk.sample.internal.utils;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;

import com.bugfender.sdk.Bugfender;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.thirdparty.rx.Observable;
import dji.thirdparty.rx.Subscription;
import dji.thirdparty.rx.android.schedulers.AndroidSchedulers;
import dji.thirdparty.rx.functions.Action1;

/**
 * VideoView will show the live video for the given video feed.
 */
public class VideoFeedSimpleView extends TextureView implements SurfaceTextureListener {
    //region Properties
    private final static String TAG = "DULFpvWidget";
    private DJICodecManager codecManager = null;
    private VideoFeeder.VideoDataListener videoDataListener = null;
    private int videoWidth;
    private int videoHeight;
    private boolean isPrimaryVideoFeed;
    private View coverView;
    private final long WAIT_TIME = 500; // Half of a second
    private AtomicLong lastReceivedFrameTime = new AtomicLong(0);
    private Observable timer =
        Observable.timer(100, TimeUnit.MICROSECONDS).observeOn(AndroidSchedulers.mainThread()).repeat();
    private Subscription subscription;
    private boolean mYUVEnabled = false;

    //endregion

    //region Life-Cycle
    public VideoFeedSimpleView(Context context) {
        this(context, null, 0);
    }

    public VideoFeedSimpleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoFeedSimpleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void EnableYuvDataReceived(boolean bEnable){
        if (codecManager != null) {
            codecManager.enabledYuvData(bEnable);
            mYUVEnabled = bEnable;
        }
    }

    public boolean IsYuvDataReceivedEnabled(){
        return mYUVEnabled;
    }


    private void init() {
        // Avoid the rending exception in the Android Studio Preview view.
        if (isInEditMode()) {
            return;
        }

        setSurfaceTextureListener(this);
        videoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {

                Bugfender.d("VideoFeedView", "videoDataListener - onReceive: " + size);
                ToastUtils.setResultToToast("videoDataListener - onReceive: " + size);

                lastReceivedFrameTime.set(System.currentTimeMillis());

                if (codecManager != null) {
                    Bugfender.d("VideoFeedView", "sendDataToDecoder: " + size);
                    codecManager.sendDataToDecoder(videoBuffer,
                                                   size,
                                                   isPrimaryVideoFeed
                                                   ? UsbAccessoryService.VideoStreamSource.Camera.getIndex()
                                                   : UsbAccessoryService.VideoStreamSource.Fpv.getIndex());
                }
            }
        };
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Bugfender.d("VideoFeedView", String.format(Locale.getDefault(), "onSurfaceTextureAvailable: %d - %d", width, height));
        ToastUtils.setResultToToast(String.format(Locale.getDefault(), "onSurfaceTextureAvailable: %d - %d", width, height));
        if (codecManager == null) {
            codecManager = new DJICodecManager(this.getContext(),
                                               surface,
                                               width,
                                               height,
                                               isPrimaryVideoFeed
                                               ? UsbAccessoryService.VideoStreamSource.Camera
                                               : UsbAccessoryService.VideoStreamSource.Fpv);
            codecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
                @Override
                public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer byteBuffer, int i, int i1, int i2) {
                    String desc = String.format("onYuvDataReceived: %s (%d,%d,%d)", mediaFormat.toString(), i, i1, i2);
                    Bugfender.d("VideoFeedView", desc);
                    ToastUtils.setResultToToast(desc);

                }
            });
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        //Ignore
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Bugfender.d("VideoFeedView", "onSurfaceTextureDestroyed");

        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager.destroyCodec();
            codecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //Bugfender.d("VideoFeedView", "onSurfaceTextureUpdated");

        if (videoHeight != codecManager.getVideoHeight() || videoWidth != codecManager.getVideoWidth()) {
            videoWidth = codecManager.getVideoWidth();
            videoHeight = codecManager.getVideoHeight();
            adjustAspectRatio(videoWidth, videoHeight);
        }
    }
    //endregion

    //region Logic
    public VideoFeeder.VideoDataListener registerLiveVideo(VideoFeeder.VideoFeed videoFeed, boolean isPrimary) {
        isPrimaryVideoFeed = isPrimary;
        Bugfender.d("VideoFeedView", "registerLiveVideo");

        if (videoDataListener != null && videoFeed != null && !videoFeed.getListeners().contains(videoDataListener)) {
            Bugfender.d("VideoFeedView", "addVideoDataListener");
            videoFeed.addVideoDataListener(videoDataListener);
            return videoDataListener;
        }
        return null;
    }

    public void changeSourceResetKeyFrame() {
        Bugfender.d("VideoFeedView", "changeSourceResetKeyFrame");

        if (codecManager != null) {
            codecManager.resetKeyFrame();
        }
    }
    //endregion

    //region Helper method

    /**
     * This method should not to be called until the size of `TextureView` is fixed.
     */
    private void adjustAspectRatio(int videoWidth, int videoHeight) {

        int viewWidth = this.getWidth();
        int viewHeight = this.getHeight();
        double aspectRatio = (double) videoHeight / videoWidth;

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;

        Matrix txform = new Matrix();
        this.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        txform.postTranslate(xoff, yoff);
        this.setTransform(txform);
    }
    //endregion


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }
}
