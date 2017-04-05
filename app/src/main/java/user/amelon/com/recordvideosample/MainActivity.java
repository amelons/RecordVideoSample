package user.amelon.com.recordvideosample;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnTouchListener, BothWayProgressBar.OnProgressEndListener {

    private static final int LISTENER_START = 200;
    //预览SurfaceView
    private SurfaceView mSurfaceView;
    private Camera mCamera;
    //底部"按住拍"按钮
    private View mStartButton;
    //进度条
    private BothWayProgressBar mProgressBar;
    //录制视频
    private MediaRecorder mMediaRecorder;
    private SurfaceHolder mSurfaceHolder;
    //屏幕分辨率
    private int videoWidth, videoHeight;
    //判断是否正在录制
    private boolean isRecording;
    //段视频保存的目录
    private File recordFile;
    //当前进度/时间
    private int mProgress;
    //录制最大时间
    public static final int MAX_TIME = 10;
    //是否上滑取消
    private boolean isCancel;
    //手势处理, 主要用于变焦 (双击放大缩小)
    private GestureDetector mDetector;
    //是否放大
    private boolean isZoomIn = false;
    private TextView mTvTip;
    private boolean isRunning;
    private ExecutorService mRecorderService;
    private ExecutorService mUpdateProgressService;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initSurfaceView();
        mRecorderService = Executors.newSingleThreadExecutor();
        mUpdateProgressService = Executors.newSingleThreadExecutor();
    }

    private void initView() {
        videoWidth = 640;
        videoHeight = 480;
        mSurfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        mDetector = new GestureDetector(this, new ZoomGestureListener());
        mStartButton = findViewById(R.id.main_press_control);
        mTvTip = (TextView) findViewById(R.id.main_tv_tip);

        mStartButton.setOnTouchListener(this);
        //自定义双向进度条
        mProgressBar = (BothWayProgressBar) findViewById(R.
                id.main_progress_bar);
        mProgressBar.setOnProgressEndListener(this);
    }

    private void initSurfaceView() {
        mSurfaceHolder = mSurfaceView.getHolder();
        //设置屏幕分辨率
        mSurfaceHolder.setFixedSize(videoWidth, videoHeight);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(this);
        /**
         * 单独处理mSurfaceView的双击事件
         */
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });

    }

    // SurfaceView回调
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        startPreView(holder);
    }

    /**
     * 开启预览
     *
     * @param holder
     */
    private void startPreView(SurfaceHolder holder) {
        if (mCamera == null) {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);

        }
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        if (mCamera != null) {
            mCamera.setDisplayOrientation(90);
            try {
                mCamera.setPreviewDisplay(holder);
                Camera.Parameters parameters = mCamera.getParameters();
                //实现Camera自动对焦
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String mode : focusModes) {
                        mode.contains("continuous-video");
                        parameters.setFocusMode("continuous-video");
                    }
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            //停止预览并释放摄像头资源
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 触摸事件的触发
     *
     * @param v
     * @param event
     * @return
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean ret = false;
        int action = event.getAction();
        float ey = event.getY();
        float ex = event.getX();
        //只监听中间的按钮处
        int vW = v.getWidth();
        int left = LISTENER_START;
        int right = vW - LISTENER_START;
        float downY = 0;
        switch (v.getId()) {
            case R.id.main_press_control: {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (ex > left && ex < right) {
                            mProgressBar.setCancel(false);
                            //显示上滑取消
                            mTvTip.setVisibility(View.VISIBLE);
                            mTvTip.setText("↑ 上滑取消");
                            //记录按下的Y坐标
                            downY = ey;
                            // TODO: 2016/10/20 开始录制视频, 进度条开始走
                            mProgressBar.setVisibility(View.VISIBLE);
                            //开始录制
                            Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
                            mRecorderService.submit(new Runnable() {
                                @Override
                                public void run() {
                                    startRecord();
                                }
                            });

                            //更新进度条
                            updateProgress();
                            ret = true;
                        }


                        break;
                    case MotionEvent.ACTION_UP:
                        if (ex > left && ex < right) {
                            mTvTip.setVisibility(View.INVISIBLE);
                            mProgressBar.setVisibility(View.INVISIBLE);
                            stopRecord();
                            ret = false;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (ex > left && ex < right) {
                            float currentY = event.getY();
                            if (downY - currentY > 10) {
                                isCancel = true;
                                mProgressBar.setCancel(true);
                            }
                        }
                        break;
                }
                break;

            }

        }
        return ret;
    }


    // 进度条结束后的回调方法
    @Override
    public void onProgressEndListener() {
        //视频停止录制
        stopRecordSave();
    }

    /**
     * 开始录制
     */
    private void startRecord() {
        if (mMediaRecorder != null) {
            //没有外置存储, 直接停止录制
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                return;
            }
            try {
                if (mMediaRecorder == null) {
                    mMediaRecorder = new MediaRecorder();
                    /// mMediaRecorder.setOnErrorListener(this);
                } else {
                    mMediaRecorder.reset();
                }
                // Step 1: Unlock and set camera to MediaRecorder
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
                mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                // Step 2: Set sources
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//before setOutputFormat()
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//before setOutputFormat()
                //设置视频输出的格式和编码
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_CIF);
                //after setVideoSource(),after setOutFormat()
                //mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
                mMediaRecorder.setVideoSize(videoWidth, videoHeight);
                mMediaRecorder.setAudioEncodingBitRate(44100);
                if (mProfile.videoBitRate > 2 * 1024 * 1024)
                    mMediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024);
                else
                    mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
                //after setVideoSource(),after setOutFormat();
                mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
                //after setOutputFormat()
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                //after setOutputFormat()
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                // Step 3: Set output file 新建文件保存录制视频
                recordFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/RecordDemo/" + System.currentTimeMillis() + ".mp4");
                if (!recordFile.getParentFile().exists()) recordFile.getParentFile().mkdirs();
                recordFile.createNewFile();
                //设置输出路径
                mMediaRecorder.setOutputFile(recordFile.getAbsolutePath());
                //解决录制视频, 播放器横向问题
                mMediaRecorder.setOrientationHint(90);
                // Step 4: start and return
                mMediaRecorder.prepare();
                mMediaRecorder.start();
                //     mMediaObject.setStartTime(System.currentTimeMillis());
                isRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 跟新进度条,启用一个线程
     */
    private void updateProgress() {
        mUpdateProgressService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mProgress = 0;
                    isRunning = true;
                    while (isRunning) {
                        mProgress++;
                        Thread.sleep(20);
                        //主线程更新进度条
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mProgressBar.setProgress(mProgress);
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void stopRecord() {
        //判断是否为录制结束, 或者为成功录制(时间过短)
        if (!isCancel) {
            if (mProgress < 50) {
                //时间太短不保存
                stopRecordUnSave();
                Toast.makeText(this, "时间太短", Toast.LENGTH_SHORT).show();
            } else {
                //停止录制
                stopRecordSave();
            }

        } else {
            //现在是取消状态,不保存
            stopRecordUnSave();
            isCancel = false;
            Toast.makeText(this, "取消录制", Toast.LENGTH_SHORT).show();
            mProgressBar.setCancel(false);
        }

    }

    /**
     * 停止录制 并且保存
     */
    private void stopRecordSave() {
        if (isRecording) {
            isRunning = false;
            mMediaRecorder.stop();
            isRecording = false;
            Toast.makeText(MainActivity.this, "视频已经放至" + recordFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 停止录制, 不保存
     */
    private void stopRecordUnSave() {
        if (isRecording) {
            isRunning = false;
            mMediaRecorder.stop();
            isRecording = false;
            if (recordFile.exists()) {
                //不保存直接删掉
                recordFile.delete();
            }
        }
    }

    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder.reset();
            ;
            mMediaRecorder = null;
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }


    // 变焦手势处理类
    class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener {
        //双击手势事件
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);
            if (mMediaRecorder != null) {
                if (!isZoomIn) {
                    setZoom(20);
                    isZoomIn = true;
                } else {
                    setZoom(0);
                    isZoomIn = false;
                }
            }
            return true;
        }
    }

    /**
     * 相机变焦
     *
     * @param zoomValue
     */
    public void setZoom(int zoomValue) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.isZoomSupported()) {//判断是否支持
                int maxZoom = parameters.getMaxZoom();
                if (maxZoom == 0) {
                    return;
                }
                if (zoomValue > maxZoom) {
                    zoomValue = maxZoom;
                }
                parameters.setZoom(zoomValue);
                mCamera.setParameters(parameters);
            }
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseRecorder();
    }
}
