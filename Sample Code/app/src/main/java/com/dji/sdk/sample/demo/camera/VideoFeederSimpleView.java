package com.dji.sdk.sample.demo.camera;

import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.bugfender.sdk.Bugfender;
import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.PickerValueChangeListener;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.utils.Helper;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.VideoFeedSimpleView;
import com.dji.sdk.sample.internal.utils.VideoFeedView;
import com.dji.sdk.sample.internal.view.PopupNumberPicker;
import com.dji.sdk.sample.internal.view.PopupNumberPickerDouble;
import com.dji.sdk.sample.internal.view.PresentableView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import dji.common.airlink.PhysicalSource;
import dji.common.airlink.VideoFeedPriority;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.keysdk.AirLinkKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.sdk.airlink.AirLink;
import dji.sdk.airlink.OcuSyncLink;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

/**
 * Class that manage live video feed from DJI products to the mobile device.
 * Also give the example of "getPrimaryVideoFeed" and "getSecondaryVideoFeed".
 */
public class VideoFeederSimpleView extends LinearLayout
    implements View.OnClickListener, PresentableView, CompoundButton.OnCheckedChangeListener {

    private PopupNumberPicker popupNumberPicker = null;
    private PopupNumberPickerDouble popupNumberPickerDouble = null;
    private static int[] INDEX_CHOSEN = { -1, -1, -1 };
    private Handler handler = new Handler(Looper.getMainLooper());

    private Context context;
    private Switch yuvToggle;
    private TextView textviewdebug;
    private VideoFeedSimpleView primaryVideoFeed;
    private VideoFeeder.PhysicalSourceListener sourceListener;
    private AirLinkKey extEnabledKey;
    private AirLinkKey lbBandwidthKey;
    private AirLinkKey hdmiBandwidthKey;
    private AirLinkKey mainCameraBandwidthKey;
    private AirLinkKey assignSourceToPrimaryChannelKey;
    private AirLinkKey primaryVideoBandwidthKey;
    private SetCallback setBandwidthCallback;
    private SetCallback setExtEnableCallback;
    private ActionCallback allocSourceCallback;
    private AirLink airLink;
    private View primaryCoverView;
    private View fpvCoverView;
    private TextView cameraListTitle;
    private String cameraListStr;

    public VideoFeederSimpleView(Context context) {
        super(context);
        this.context = context;
        init(context);
        Bugfender.d("VideoFeederView", "cstr");
    }

    private void init(Context context) {
        Bugfender.d("VideoFeederView", "init");
        //setOrientation(HORIZONTAL);
        setClickable(true);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_video_feeder_simple, this, true);

        initAirLink();
        initAllKeys();
        initUI();
        initCallbacks();
        setUpListeners();
    }

    private void initUI() {
        Bugfender.d("VideoFeederView", "initUI");
        yuvToggle = (Switch) findViewById(R.id.yuv_toggle_button);
        primaryVideoFeed = (VideoFeedSimpleView) findViewById(R.id.primary_video_feed);
        textviewdebug = (TextView) findViewById(R.id.textViewDebug);
    }

    private void initAirLink() {
        BaseProduct baseProduct = DJISDKManager.getInstance().getProduct();
        if (null != baseProduct && null != baseProduct.getAirLink()) {
            airLink = baseProduct.getAirLink();
        }
    }

    private void debug(String text){
        textviewdebug.setText(text);
    }

    private void initAllKeys() {
        extEnabledKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.IS_EXT_VIDEO_INPUT_PORT_ENABLED);
        lbBandwidthKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_LB_VIDEO_INPUT_PORT);
        hdmiBandwidthKey =
            AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_HDMI_VIDEO_INPUT_PORT);
        mainCameraBandwidthKey = AirLinkKey.createLightbridgeLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_LEFT_CAMERA);
        assignSourceToPrimaryChannelKey = AirLinkKey.createOcuSyncLinkKey(AirLinkKey.ASSIGN_SOURCE_TO_PRIMARY_CHANNEL);
        primaryVideoBandwidthKey = AirLinkKey.createOcuSyncLinkKey(AirLinkKey.BANDWIDTH_ALLOCATION_FOR_PRIMARY_VIDEO);
    }

    private void initCallbacks() {
        setBandwidthCallback = new SetCallback() {
            @Override
            public void onSuccess() {
                ToastUtils.setResultToToast("Set key value successfully");
                if (primaryVideoFeed != null) {
                    primaryVideoFeed.changeSourceResetKeyFrame();
                }
            }

            @Override
            public void onFailure(@NonNull DJIError error) {
                ToastUtils.setResultToToast("Failed to set: " + error.getDescription());
            }
        };

        allocSourceCallback = new ActionCallback() {
            @Override
            public void onSuccess() {
                ToastUtils.setResultToToast("Perform action successfully");
            }

            @Override
            public void onFailure(@NonNull DJIError error) {
                ToastUtils.setResultToToast("Failed to action: " + error.getDescription());
            }
        };
    }


    private void setUpListeners() {
        sourceListener = new VideoFeeder.PhysicalSourceListener() {
            @Override
            public void onChange(VideoFeeder.VideoFeed videoFeed, PhysicalSource newPhysicalSource) {
                String debugString = String.format("PhysicalSourceListener -> onChange %s, %s", videoFeed.toString(), newPhysicalSource.name());
                debug(debugString);
                Bugfender.d("VideoFeederView", debugString);

                if (videoFeed == VideoFeeder.getInstance().getPrimaryVideoFeed()) {
                    String newText = "Primary Source: " + newPhysicalSource.toString();
                    ToastUtils.setResultToToast(newText);
                }
            }
        };

        setVideoFeederListeners(true);
    }

    private void tearDownListeners() {
        setVideoFeederListeners(false);
    }

    private void setVideoFeederListeners(boolean isOpen) {
        Bugfender.d("VideoFeederView", "setVideoFeederListeners");

        if (VideoFeeder.getInstance() == null) return;

        final BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null) {
            Bugfender.d("VideoFeederView", "product: " + product.getModel().getDisplayName());
            VideoFeeder.VideoDataListener primaryVideoDataListener =
                primaryVideoFeed.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);

            if (isOpen) {
                String newText =
                    "Primary Source: " + VideoFeeder.getInstance().getPrimaryVideoFeed().getVideoSource().name();
                ToastUtils.setResultToToast(newText);
                VideoFeeder.getInstance().addPhysicalSourceListener(sourceListener);
                Bugfender.d("VideoFeederView", "addPhysicalSourceListener : sourceListener");
            } else {
                VideoFeeder.getInstance().removePhysicalSourceListener(sourceListener);
                VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(primaryVideoDataListener);
            }
        }
    }
    @Override
    public void onClick(View view) {

        if (view == yuvToggle) {
            primaryVideoFeed.EnableYuvDataReceived(!primaryVideoFeed.IsYuvDataReceivedEnabled());
        }
    }


    @Override
    public int getDescription() {
        return R.string.component_listview_video_feeder;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DJISampleApplication.getEventBus().post(new MainActivity.RequestStartFullScreenEvent());
    }

    @Override
    protected void onDetachedFromWindow() {
        DJISampleApplication.getEventBus().post(new MainActivity.RequestEndFullScreenEvent());
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
