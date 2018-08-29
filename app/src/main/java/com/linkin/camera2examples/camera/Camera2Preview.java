package com.linkin.camera2examples.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.linkin.camera2examples.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Author: Linkin
 * Time：2018/8/27
 * Email：liuzhongjun@novel-supertv.com
 * Blog：https://blog.csdn.net/Android_Technology
 * Desc: TODO
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Preview extends TextureView implements CameraView {


    private static final String TAG = "Camera2Preview";
    private Context mContext;
    private WindowManager mWindowManager;


    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    private int mCameraCount;
    private int mCurrentCameraFacing = CameraCharacteristics.LENS_FACING_BACK;


    public Camera2Preview(Context context) {
        this(context, null);
    }

    public Camera2Preview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Camera2Preview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        init();
    }

    private void init() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }


    /**
     * 同生命周期 onResume
     */
    @Override
    public void onResume() {
        startBackgroundThread();
        // 当关闭并打开屏幕时，SurfaceTexture已经可用，并且不会调用“onSurfaceTextureAvailable”。
        // 在这种情况下，我们可以打开相机并从这里开始预览（否则，我们将等待，直到Surface在SurfaceTextureListener中准备好）。
        if (isAvailable()) {
            openCamera(getWidth(), getHeight());
        } else {
            setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * 同生命周期 onPause
     */
    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }


    /**
     * 启动拍照的方法
     */

    @Override
    public void takePicture() {
        takePictureInternal();
    }

    private TakePictureCallback mTakePictureCallback;

    @Override
    public void takePicture(TakePictureCallback takePictureCallback) {
        mTakePictureCallback = takePictureCallback;
        takePictureInternal();
    }

    private void takePictureInternal() {
        if (mCurrentCameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
            lockFocus();
        } else {
            runPrecaptureSequence();
        }
    }


    /**
     * 设置图片的保存路径(文件夹)
     *
     * @param pictureSavePath
     */
    private String mPictureSaveDir;

    @Override
    public void setPictureSavePath(String pictureSavePath) {
        mPictureSaveDir = pictureSavePath;
    }

    @Override
    public void switchCameraFacing() {
        if (mCameraCount > 1) {
            mCurrentCameraFacing = mCurrentCameraFacing == CameraCharacteristics.LENS_FACING_BACK ?
                    CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
            closeCamera();
            openCamera(getWidth(), getHeight());
        }
    }


    /**
     * 设置此视图的宽高比。
     * 视图的大小将基于从参数计算的比率来测量。
     * 请注意，参数的实际大小并不重要，也就是说，setAspectRatio（2, 3）setAspectRatio（4, 6）会得到相同的结果。
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    /**
     * 相机状态: 显示相机预览
     */
    private static final int STATE_PREVIEW = 0;
    /**
     * 相机状态: 等待焦点被锁定
     */
    private static final int STATE_WAITING_LOCK = 1;
    /**
     * 相机状态: 等待曝光前的状态。
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /**
     * 相机状态: 等待曝光状态非预先捕获的东西.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    /**
     * 相机状态: 照片拍摄
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * 最大的预览宽度
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * 最大的预览高度
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * 相机预览要用到的{@link CameraCaptureSession } .
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * 相机预览的 {@link android.util.Size}
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // 当相机打开时调用此方法。我们在这里开始相机预览。
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Log.e(TAG, "CameraDevice.StateCallback onError errorCode= " + error);
        }

    };

    /**
     * 用于运行不应该阻止UI的任务的附加线程。
     */
    private HandlerThread mBackgroundThread;

    /**
     * 在后台运行任务的Handler。
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} 用于处理图像捕获(抓拍).
     */
    private ImageReader mImageReader;


    /**
     * 图片文件
     */
    private File mPictureFile;
    /**
     * 这是{@link ImageReader}的回调对象. 当静态图像准备好保存时，将回调"onImageAvailable"
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //检测外部存储是否存在
            //根据时间戳生成图片名称
            if (FileUtils.checkSDCard()) {
                if (mPictureSaveDir == null) {
                    mPictureFile = FileUtils.getOutputMediaFile(mContext, FileUtils.MEDIA_TYPE_IMAGE);
                } else {
                    mPictureFile = FileUtils.getTimeStampMediaFile(mPictureSaveDir, FileUtils.MEDIA_TYPE_IMAGE);
                }
                if (mPictureFile == null) {
                    Log.e(TAG, "error creating media file, check storage permissions");
                    if (mTakePictureCallback != null) {
                        mTakePictureCallback.error("error creating media file, check storage permissions");
                    }
                    return;
                }
            } else {
                mPictureFile = FileUtils.getOutputMediaFile(mContext, FileUtils.MEDIA_TYPE_IMAGE);
            }
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mPictureFile));
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * 拍照相机的当前状态
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} 防止相机关闭前退出应用程序。
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * 当前的相机设备是否支持Flash。
     */
    private boolean mFlashSupported;

    /**
     * 相机传感器的方向
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} 处理JPEG捕获的相关事件
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };


    /**
     * 给定相机支持的{@code Size}的{@code choices}，
     * 选择至少与相应TextureView大小一样大、最多与相应最大大小一样大、且纵横比与指定值匹配的最小一个。
     * 如果不存在这样的尺寸，则选择最大尺寸与相应的最大尺寸一样大，并且其纵横比与指定值匹配的最大尺寸。
     *
     * @param choices           摄像机支持预期输出类的大小列表。
     * @param textureViewWidth  TextureView相对于传感器坐标的宽度
     * @param textureViewHeight TextureView相对于传感器坐标的高度
     * @param maxWidth          可选择的最大宽度
     * @param maxHeight         可选择的最大高度
     * @param aspectRatio       宽高比
     * @return 最佳的 {@code Size}, 或任意一个，如果没有足够大的话
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // 收集至少与预览表面一样大的支持分辨率。
        List<Size> bigEnough = new ArrayList<>();
        // 收集小于预览表面的支持的分辨率
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // 挑那些最小中的足够大的。如果没有足够大的，选择最大的那些不够大的。
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    /**
     * 设置与摄像机相关的成员变量。
     *
     * @param width  相机预览可用尺寸的宽度
     * @param height 相机预览可用尺寸的高度
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            mCameraCount = cameraIdList.length;
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                //判断当前摄像头是前置还是后置摄像头
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != mCurrentCameraFacing) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // 对于静态图像捕获，我们使用最大可用的大小。
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // 找出是否需要交换尺寸以获得相对与传感器坐标的预览大小
                int displayRotation = mWindowManager.getDefaultDisplay().getRotation();
                //检验条件
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                mWindowManager.getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // 危险，W.R.！尝试使用太大的预览大小可能超过相机总线的带宽限制，导致高清的预览，但存储垃圾捕获数据。
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // 我们将TextureView的宽高比与我们选择的预览大小相匹配。
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                //检验是否支持flash
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            //抛出空指针一般代表当前设备不支持Camera2API
            Log.e(TAG, "This device doesn't support Camera2 API.");

        }
    }

    /**
     * 打开指定的相机（mCameraId）
     */
    private void openCamera(int width, int height) {

        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * 关闭相机
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * 启动后台线程和Handler.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * 停止后台线程和Handler.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建一个新的 {@link CameraCaptureSession} 用于相机预览.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = getSurfaceTexture();
            assert texture != null;

            // 我们将默认缓冲区的大小设置为我们想要的相机预览的大小。
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // 我们需要开始预览输出Surface
            Surface surface = new Surface(texture);

            // 我们建立了一个具有输出Surface的捕获器。
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // 这里，我们创建了一个用于相机预览的CameraCaptureSession
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // 相机已经关闭
                            if (null == mCameraDevice) {
                                return;
                            }

                            // 当session准备好后，我们开始显示预览
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // 相机预览时应连续自动对焦
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 设置闪光灯在必要时自动打开
                                setAutoFlash(mPreviewRequestBuilder);

                                // 最终,显示相机预览
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "CameraCaptureSession.StateCallback onConfigureFailed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 配置必要的 {@link android.graphics.Matrix} 转换为 `mTextureView`.
     * <p>
     * 该方法应该在setUpCameraOutputs中确定相机预览大小以及“mTextureView”的大小固定之后调用。
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mPreviewSize || null == mContext) {
            return;
        }

        int rotation = mWindowManager.getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        setTransform(matrix);
    }


    /**
     * 锁定焦点作为静态图像捕获的第一步
     */
    private void lockFocus() {
        try {
            //  这里是让相机锁定焦点
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 告知 #mCaptureCallback 等待锁
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行预捕获序列捕获一张静态图片。
     * <p>
     * 这个方法应该在我们从得到mCaptureCallback的响应后调用
     */
    private void runPrecaptureSequence() {
        try {
            // 这就是如何告诉相机触发。
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // 告知 #mCaptureCallback 等待设置预捕获序列。
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 捕获一张静态图片
     * 这个方法应该在我们从得到mCaptureCallback的响应后调用
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // 这是 CaptureRequest.Builder ，我们用它来进行拍照
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            //  使用相同的AE和AF模式作为预览。
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // 方向
            int rotation = mWindowManager.getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "CameraCaptureSession.CaptureCallback onCaptureCompleted 图片保存地址为：" + mPictureFile.toString());
                    if (mTakePictureCallback != null) {
                        mTakePictureCallback.success(mPictureFile.getAbsolutePath());
                    }
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从指定的屏幕旋转中检索JPEG方向。
     *
     * @param rotation 图片旋转
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // 对于大多数设备，传感器定向是90，对于某些设备（例如Nexus 5X）是270。
        //我们必须考虑到这一点，并适当的旋转JPEG。
        //对于取向为90的设备，我们只需从方向返回映射即可。
        //对于方向为270的设备，我们需要旋转JPEG 180度。
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * 解锁焦点.
     * <p>
     * 此方法应该在静态图片捕获序列结束后调用
     */
    private void unlockFocus() {
        try {
            // 重置自动对焦触发
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // 在此之后，相机将回到正常的预览状态。
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * 将JPEG{@link Image}保存并放到指定的文件中
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * 根据它们的区域比较两个的大小 {@code Size}。
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


}
