package com.example.calcounterapp;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

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

public class CameraActivity extends BaseActivity {

    private static final String TAG = "CameraPreview";
    private AutoFitTextureView textureView;
    private ImageView igPhotoPreview;
    private Group gpCapturePhoto, gpSaveOrRetry;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();



    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int maxPreviewWidth, maxPreviewHeight;

    /**
     Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * exposure state.
     */
    private static final int STATE_WAITING_NON_PRE_CAPTURE = 3;

    /**
     * Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private File file;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    public boolean photoTaken;


    private int mState = STATE_PREVIEW;


    private Semaphore cameraOpenCloseLockSemaphore = new Semaphore(1);

    /**
     *  Flash or not.
     */
    private boolean flashSupported;

    /**
     * sensing camera sensor
     */
    private int sensorOrientation;

    private Size previewSize;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (!checkCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        textureView = findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);
        igPhotoPreview = findViewById(R.id.ig_photo_preview);
        gpCapturePhoto = findViewById(R.id.gp_take_photo);
        gpSaveOrRetry = findViewById(R.id.gp_save_retry);

        ImageButton takePictureButton = findViewById(R.id.btn_takepicture);
        takePictureButton.setOnClickListener(v -> {
            takePicture();
        });

        ImageButton deletePictureButton = findViewById(R.id.btn_delete);
        deletePictureButton.setOnClickListener(v -> {
            if (file != null && file.exists()) {
                file.delete();
            }
            gpCapturePhoto.setVisibility(View.VISIBLE);
            gpSaveOrRetry.setVisibility(View.GONE);
        });

        ImageButton cancelPictureButton = findViewById(R.id.btn_retry_take_photo);

        cancelPictureButton.setOnClickListener(v -> {
            gpCapturePhoto.setVisibility(View.VISIBLE);
            gpSaveOrRetry.setVisibility(View.GONE);
        });
    }


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            File storageDirector = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            file = new File((storageDirector != null ? storageDirector.getAbsolutePath() : null) + "/" + System.currentTimeMillis() + ".jpg");
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file));
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //Camera is open
            Log.e(TAG, "onOpened");
            cameraOpenCloseLockSemaphore.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLockSemaphore.release();
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLockSemaphore.release();
            cameraDevice.close();
            cameraDevice = null;
            finish();
        }
    };


    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Log.d(TAG, "process: STATE_WAITING_LOCK");
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        Log.d(TAG, "process: afState is null");
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Log.d(TAG, "process: afState is locked");
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
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRE_CAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRE_CAPTURE: {
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

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder
                    = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // Closed Camera
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSessions = cameraCaptureSession;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_AUTO);
                                setAutoFlash(captureRequestBuilder);
                                captureRequest = captureRequestBuilder.build();
                                cameraCaptureSessions.setRepeatingRequest(captureRequest,
                                        captureCallbackListener, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "onConfigureFailed: failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "camera open");
        try {
            if (!cameraOpenCloseLockSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if (checkCameraPermission()) {
                manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
        Log.e(TAG, "openCamera X");
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLockSemaphore.acquire();
            if (cameraCaptureSessions != null) {
                cameraCaptureSessions.close();
                cameraCaptureSessions = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLockSemaphore.release();
        }
    }
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                imageReader.setOnImageAvailableListener(
                        onImageAvailableListener, mBackgroundHandler);

                int displayRotation = this.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                this.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                maxPreviewWidth = displaySize.x;
                maxPreviewHeight = displaySize.y;

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
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                }

                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                flashSupported = available == null ? false : available;

                this.cameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.d(TAG, "chooseOptimalSize: option width is " + option.toString());
            Log.d(TAG, "chooseOptimalSize: max value is " + maxWidth + " X " + maxHeight);

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
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }
    private void takePicture() {
        lockFocus();
    }
    private void lockFocus() {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void runPrecaptureSequence() {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            if (cameraDevice == null) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO);
            setAutoFlash(captureBuilder);

            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            cameraCaptureSessions.stopRepeating();
            cameraCaptureSessions.abortCaptures();
            cameraCaptureSessions.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }
    private class ImageSaver implements Runnable {

        private final Image mImage;

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
                photoTaken = true;

                BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                bitmapOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(mFile.getAbsolutePath(), bitmapOptions);

                float widthScale = (float) bitmapOptions.outWidth / maxPreviewWidth;
                float heightScale = (float) bitmapOptions.outHeight / maxPreviewHeight;
                float scale = Math.min(widthScale, heightScale);

                int sampleSize = 1;
                while (sampleSize < scale) {
                    sampleSize *= 2;
                }
                bitmapOptions.inSampleSize = sampleSize;
                bitmapOptions.inJustDecodeBounds = false;

                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this, getResources().getString(R.string.message_photo_taken), Toast.LENGTH_SHORT).show();
                    Bitmap thumbnail = BitmapFactory.decodeFile(file.getAbsolutePath(), bitmapOptions);
                    igPhotoPreview.setImageBitmap(thumbnail);
                    gpSaveOrRetry.setVisibility(View.VISIBLE);
                    gpCapturePhoto.setVisibility(View.GONE);
                });
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    private void unlockFocus() {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(captureRequestBuilder);
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener,
                    mBackgroundHandler);
            mState = STATE_PREVIEW;
            cameraCaptureSessions.setRepeatingRequest(captureRequest, captureCallbackListener,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
}
