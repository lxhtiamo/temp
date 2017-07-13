package com.linewell.myapplication;

import android.Manifest.permission;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.FaceDetector;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.util.Accelerometer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by  on 2017/4/15.
 */

public class BaseVideoActivity extends Activity {
    private final static String TAG = BaseVideoActivity.class.getSimpleName();
    /**
     * 拍照预览
     */
    protected SurfaceView mPreviewSurface;
    /**
     * 脸部识别框
     */
    private SurfaceView mFaceSurface;
    protected Camera mCamera;
    protected int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    // Camera nv21格式预览帧的尺寸，默认设置640*480
    protected int PREVIEW_WIDTH = 640;
    protected int PREVIEW_HEIGHT = 480;
    // 预览帧数据存储数组和缓存数组
    protected byte[] nv21;
    protected byte[] buffer;
    // 缩放矩阵
    private Matrix mScaleMatrix = new Matrix();
    // 加速度感应器，用于获取手机的朝向
    private Accelerometer mAcc;
    // FaceDetector对象，集成了离线人脸识别：人脸检测、视频流检测功能
    private FaceDetector mFaceDetector;
    private boolean mStopTrack;
    private Toast mToast;
    private long mLastClickTime;
    private int isAlign = 0;
    private Button button_take_photos;
    private ImageView imageView_preview, testImageView;
    private Bitmap mBitmap1;
    private TextView tv_time;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // SpeechUtility.createUtility(this, "appid=5900325a");
        // 可以替换成自己的讯飞的开发者编码.注意:没有集成动态的权限,6.0以上需要在设置里面开启摄像头权限,
        // 因为很多app权限都是在开启页面就申请权限了,而且权限的管理库很多,每个人的使用不相同.
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=5900325a");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        initUI();
        nv21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
        buffer = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
        mAcc = new Accelerometer(BaseVideoActivity.this);
        mFaceDetector = FaceDetector.createDetector(BaseVideoActivity.this, null);
    }


    private Callback mPreviewCallback = new Callback() {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            closeCamera();
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            openCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            mScaleMatrix.setScale(width / (float) PREVIEW_HEIGHT, height / (float) PREVIEW_WIDTH);
        }
    };

    private void setSurfaceSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int width = metrics.widthPixels;
        //用宽高比来显示图片
        int height = (int) (width * PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);
        RelativeLayout.LayoutParams params = new LayoutParams(width, height);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mPreviewSurface.setLayoutParams(params);
        mFaceSurface.setLayoutParams(params);
    }


    private void initUI() {
        tv_time = (TextView) findViewById(R.id.tv_time);
        testImageView = (ImageView) findViewById(R.id.testImageView);
        imageView_preview = (ImageView) findViewById(R.id.imageView_preview);
        mPreviewSurface = (SurfaceView) findViewById(R.id.sfv_preview);
        mFaceSurface = (SurfaceView) findViewById(R.id.sfv_face);

        mPreviewSurface.getHolder().addCallback(mPreviewCallback);
        mPreviewSurface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mFaceSurface.setZOrderOnTop(true);
        mFaceSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        // 点击SurfaceView，切换摄相头
        mFaceSurface.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // 只有一个摄相头，不支持切换
                if (Camera.getNumberOfCameras() == 1) {
                    showTip("只有后置摄像头，不能切换");
                    return;
                }
                closeCamera();
                if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
                    mCameraId = CameraInfo.CAMERA_FACING_BACK;
                } else {
                    mCameraId = CameraInfo.CAMERA_FACING_FRONT;
                }
                openCamera();
            }
        });

        // 长按SurfaceView 500ms后松开，摄相头聚集
        mFaceSurface.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastClickTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - mLastClickTime > 500) {
                            mCamera.autoFocus(null);
                            return true;
                        }
                        break;

                    default:
                        break;
                }
                return false;
            }
        });

        setSurfaceSize();
        mToast = Toast.makeText(BaseVideoActivity.this, "", Toast.LENGTH_SHORT);

        button_take_photos = (Button) findViewById(R.id.button_take_photos);
        button_take_photos.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //button_take_photos.setClickable(false);
                mCamera.takePicture(null, null, jpeg);
            }
        });
    }

    Camera.PictureCallback jpeg = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "onPictureTaken_data.length: " + data.length);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String format1 = format.format(new Date());
            String filename = format1 + ".jpg";
            FileOutputStream fos;
            //获取拍到的图片Bitmap
            Bitmap bitmap_source = null;
            String pictureStoragePath = PictureUtil.getPictureStoragePath(getApplicationContext());
            File f = new File(pictureStoragePath, filename);
            try {
                fos = new FileOutputStream(f);
                if (data.length < 35000) {
                    YuvImage image = new YuvImage(nv21, ImageFormat.NV21, PREVIEW_WIDTH, PREVIEW_HEIGHT, null);   //将NV21 data保存成YuvImage
                    //图像压缩
                    image.compressToJpeg(
                            new Rect(0, 0, image.getWidth(), image.getHeight()),
                            100, fos);
                    Log.i(TAG, "onPictureTaken_data.length<20000: " + data.length);
                    Log.i(TAG, "onPictureTaken_nv21.length: " + nv21.length);
                    bitmap_source = PictureUtil.compressFacePhoto(f.getAbsolutePath());
                    fos = new FileOutputStream(f);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    //旋转图片
                    // 根据旋转角度，生成旋转矩阵
                    Matrix matrix = new Matrix();
                    if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        matrix.postRotate(270);
                    } else {
                        matrix.postRotate(90);
                    }
                    mBitmap1 = Bitmap.createBitmap(bitmap_source, 0, 0, bitmap_source.getWidth(), bitmap_source.getHeight(), matrix, true);
                    testImageView.setImageBitmap(mBitmap1);
                    boolean result = mBitmap1.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    bos.close();
                    Log.i(TAG, "onPictureTaken_mBitmap1.compress: " + result);
                } else {
                    bitmap_source = PictureUtil.compressFacePhoto(data);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    //旋转图片
                    // 根据旋转角度，生成旋转矩阵
                    Matrix matrix = new Matrix();
                    if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        matrix.postRotate(270);
                    } else {
                        matrix.postRotate(90);
                    }
                    mBitmap1 = Bitmap.createBitmap(bitmap_source, 0, 0, bitmap_source.getWidth(), bitmap_source.getHeight(), matrix, true);
                    testImageView.setImageBitmap(mBitmap1);
                    mBitmap1.compress(Bitmap.CompressFormat.JPEG, 70, bos);
                    bos.close();
                }
                //fos.write(data);
                fos.flush();
                fos.close();

                Log.i(TAG, "onPictureTaken: 图片保存成功");
              /*  //重新初始化相机控件
                mCameraId = CameraInfo.CAMERA_FACING_FRONT;
                openCamera();
                mCamera.startPreview();*/
               /* Intent intent = new Intent(getApplicationContext(), DialogInputNameActivity.class);
                startActivityForResult(intent, 0);*/
                if (mBitmap1 != null) {
                    String result = mFaceDetector.detectARGB(mBitmap1);
                    Log.d(TAG, "result:" + result);
                    // 解析人脸结果
                    FaceRect[] faceRects = ParseResult.parseResult(result);
                    //存放人脸的数据>0说明有人脸
                    if (faceRects != null && faceRects.length > 0) {

                        showTip("识别到人脸:" + faceRects.length + "张,----开始上传");
                        // TODO: 2017/6/29 发送人脸

                    } else {
                        // 在无人脸的情况下，判断结果信息
                        int errorCode = 0;
                        JSONObject object;
                        try {
                            object = new JSONObject(result);
                            errorCode = object.getInt("ret");
                        } catch (JSONException e) {
                        }
                        // errorCode!=0，表示人脸发生错误，请根据错误处理
                        if (ErrorCode.SUCCESS == errorCode) {
                            showTip("没有检测到人脸");
                        } else {
                            showTip("检测发生错误，错误码：" + errorCode);
                        }

                        reStartCamera();

                    }

                } else {
                    showTip("没有找到图片");
                }

                reStartCamera();

            } catch (Exception e) {
                System.out.println("图片保存异常" + e.getMessage());
                e.printStackTrace();
            }
        }
    };

    /**
     * 重新启动相机
     */
    private void reStartCamera() {
        mCameraId = CameraInfo.CAMERA_FACING_FRONT;
        openCamera();
        mCamera.startPreview();
    }

    protected void openCamera() {
        if (null != mCamera) {
            return;
        }

        if (!checkCameraPermission()) {
            showTip("摄像头权限未打开，请打开后再试");
            mStopTrack = true;
            return;
        }

        // 只有一个摄相头，打开后置
        if (Camera.getNumberOfCameras() == 1) {
            mCameraId = CameraInfo.CAMERA_FACING_BACK;
        }

        try {
            mCamera = Camera.open(mCameraId);
            if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
                showTip("前置摄像头已开启，点击可切换");
            } else {
                showTip("后置摄像头已开启，点击可切换");
            }
        } catch (Exception e) {
            e.printStackTrace();
            closeCamera();
            return;
        }

        Parameters params = mCamera.getParameters();
        if (params.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        params.setPreviewFormat(ImageFormat.NV21);
        params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        mCamera.setParameters(params);

        // 设置显示的偏转角度，大部分机器是顺时针90度，某些机器需要按情况设置
        mCamera.setDisplayOrientation(90);
        mCamera.setPreviewCallback(new PreviewCallback() {

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                System.arraycopy(data, 0, nv21, 0, data.length);
            }
        });

        try {
            mCamera.setPreviewDisplay(mPreviewSurface.getHolder());
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mFaceDetector == null) {

            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化");
        }
    }

    private void closeCamera() {
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }


    private boolean checkCameraPermission() {
        int status = checkPermission(permission.CAMERA, Process.myPid(), Process.myUid());
        return PackageManager.PERMISSION_GRANTED == status;

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null != mAcc) {
            mAcc.start();
        }

        mStopTrack = false;
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (!mStopTrack) {
                    if (null == nv21) {
                        continue;
                    }

                    synchronized (nv21) {
                        System.arraycopy(nv21, 0, buffer, 0, nv21.length);
                    }

                    // 获取手机朝向，返回值0,1,2,3分别表示0,90,180和270度
                    int direction = Accelerometer.getDirection();
                    boolean frontCamera = (CameraInfo.CAMERA_FACING_FRONT == mCameraId);
                    // 前置摄像头预览显示的是镜像，需要将手机朝向换算成摄相头视角下的朝向。
                    // 转换公式：a' = (360 - a)%360，a为人眼视角下的朝向（单位：角度）
                    if (frontCamera) {
                        // SDK中使用0,1,2,3,4分别表示0,90,180,270和360度
                        direction = (4 - direction) % 4;
                    }

                    if (mFaceDetector == null) {

                        // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
                        showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化");
                        break;
                    }

                    String result = mFaceDetector.trackNV21(buffer, PREVIEW_WIDTH, PREVIEW_HEIGHT, isAlign, direction);
                    //Log.d(TAG, "result:"+result);

                    final FaceRect[] faces = ParseResult.parseResult(result);

                    Canvas canvas = mFaceSurface.getHolder().lockCanvas();
                    if (null == canvas) {
                        continue;
                    }

                    canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    canvas.setMatrix(mScaleMatrix);

                    if (faces == null || faces.length <= 0) {
                        mFaceSurface.getHolder().unlockCanvasAndPost(canvas);
                        is_empty_head = true;
                        if (is_empty_head) {
                            handler.sendEmptyMessage(STOP_TIME);
                        }
                        continue;
                    }

                    if (null != faces && frontCamera == (CameraInfo.CAMERA_FACING_FRONT == mCameraId)) {

                        for (FaceRect face : faces) {
                            face.bound = PictureUtil.RotateDeg90(face.bound, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                            if (face.point != null) {
                                for (int i = 0; i < face.point.length; i++) {
                                    face.point[i] = PictureUtil.RotateDeg90(face.point[i], PREVIEW_WIDTH, PREVIEW_HEIGHT);
                                }
                            }
                            PictureUtil.drawFaceRect(canvas, face, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                                    frontCamera, false);
                        }

                    } else {
                        Log.d(TAG, "faces:0");
                    }

                    mFaceSurface.getHolder().unlockCanvasAndPost(canvas);

                    //打开倒计时拍照

                    is_empty_head = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (faces != null && faces.length > 0 && faces.length == 1) {
                                is_delayed = true;
                                if (is_delayed) {
                                    showTip("检测到人脸:" + faces.length + "张,开始倒计时拍照,请保持姿态");
                                    Message message = handler.obtainMessage(KEEP_COUNTING);     // Message
                                    handler.sendMessageDelayed(message, 1000);
                                    is_delayed = false;
                                }
                            }else {
                                is_delayed = false;
                                is_empty_head = true;
                                handler.sendEmptyMessage(STOP_TIME);

                            }
                        }
                    });

                }
            }
        }).start();
    }

    boolean is_empty_head = true;
    boolean is_delayed = true;
    int time = -1;
    private static final int KEEP_COUNTING = 713;
    private static final int RESET_TIME = 15;
    private static final int STOP_TIME = 992;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KEEP_COUNTING:
                    if (!is_empty_head) {
                        if (time == -1) {
                            Log.i(TAG, "handleMessage: 3");
                            time = 5;
                            tv_time.setVisibility(View.VISIBLE);
                            tv_time.setText("" + time);
                            Message message = handler.obtainMessage(RESET_TIME);
                            handler.sendMessageDelayed(message, 950);      // send message
                        }
                    }

                    break;
                case RESET_TIME:
                    if (time >= 0) {
                        Log.i(TAG, "handleMessage: 2");
                        time--;
                        Message message = handler.obtainMessage(RESET_TIME);
                        handler.sendMessageDelayed(message, 950);
                        //开始拍照
                        if (time == -1) {
                            if (mCamera != null) {
                                mCamera.takePicture(null, null, jpeg);
                                is_delayed = true;
                            }
                            handler.removeMessages(RESET_TIME);
                            tv_time.setVisibility(View.GONE);
                        } else {
                            tv_time.setText("" + time);
                        }
                    }
                    break;
                case STOP_TIME:
                    if (is_empty_head) {
                        time = -1;
                        tv_time.setVisibility(View.GONE);
                        handler.removeMessages(RESET_TIME);
                        is_delayed = false;
                        Log.i(TAG, "handleMessage: 1");
                    }
                    break;
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        if (null != mAcc) {
            mAcc.stop();
        }
        mStopTrack = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mFaceDetector) {
            // 销毁对象
            mFaceDetector.destroy();
        }
        //销毁消息队列
        handler.removeCallbacksAndMessages(null);
    }

    private void showTip(final String str) {
        if (mToast != null) {
            mToast.setText(str);
            mToast.show();
        }
    }
}
