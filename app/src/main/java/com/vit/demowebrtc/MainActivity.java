package com.vit.demowebrtc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.Toast;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private final static String CAMERA = Manifest.permission.CAMERA;
    private final static String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;

    @BindView(R.id.button_start)
    Button mButtonStart;

    @BindView(R.id.button_stop)
    Button mButtonStop;

    @BindView(R.id.local_view)
    SurfaceViewRenderer mViewLocal;

    private EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private MediaConstraints mediaConstraints;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private AudioSource audioSource;
    private AudioTrack audioTrack;

    private boolean isStarting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if(ContextCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[] {CAMERA, RECORD_AUDIO }, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100 && grantResults.length > 0) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "권한 거부", Toast.LENGTH_SHORT).show();
        }
    }

    @OnClick(R.id.button_start)
    void onClickStart() {
        initSurfaceView();
        initPeerConnectionFactory();
        initVideoTrack();
        initAudioTrack();

        mButtonStart.setEnabled(false);
        mButtonStop.setEnabled(true);
        isStarting = true;
    }

    @OnClick(R.id.button_stop)
    void onClickStop() {
        if(isStarting) {
            mButtonStart.setEnabled(true);
            mButtonStop.setEnabled(false);
            try { videoCapturer.stopCapture(); }
            catch (InterruptedException e) { e.printStackTrace(); }

            videoCapturer.dispose();
            videoSource.dispose();
            videoTrack.dispose();

            audioSource.dispose();
            audioTrack.dispose();
            mViewLocal.clearImage();
            mViewLocal.release();

            rootEglBase.release();
            peerConnectionFactory.dispose();
            isStarting = false;
        }
    }

    private void initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
    }

    private void initVideoTrack() {
        if(isCamera2Supported())
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        else
            videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        SurfaceTextureHelper helper = SurfaceTextureHelper.create("SurfaceTexture", rootEglBase.getEglBaseContext());
        videoSource = peerConnectionFactory.createVideoSource(true);

        videoCapturer.initialize(helper, this, videoSource.getCapturerObserver());
        videoTrack = peerConnectionFactory.createVideoTrack("VideoTrack", videoSource);
        videoCapturer.startCapture(1000, 720, 30);
        videoTrack.addSink(mViewLocal);
    }

    private void initAudioTrack() {
        mediaConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(mediaConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("AudioTrack", audioSource);
    }

    private void initSurfaceView() {
        rootEglBase = EglBase.create();
        mViewLocal.init(rootEglBase.getEglBaseContext(), null);
        mViewLocal.setMirror(true);
        mViewLocal.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mViewLocal.setEnableHardwareScaler(true);
        mViewLocal.setZOrderMediaOverlay(true);
    }


    private boolean isCamera2Supported() {
        return Camera2Enumerator.isSupported(this);
    }
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for(String deviceName : deviceNames) { // 전면 카메라
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null)
                    return videoCapturer;
            }
        }
        for(String deviceName : deviceNames) { // 후면 카메라
            if(!enumerator.isFrontFacing(deviceName))
                return enumerator.createCapturer(deviceName, null);
        }
        return null;
    }

}