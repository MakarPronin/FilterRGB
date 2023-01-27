package com.bluejay.filterrgb;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ImageView previewView;
    private OrientationEventListener orientationEventListener;
    private String analysisResolution = "";

    private int filterR = 0;
    private int filterG = 0;
    private int filterB = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        //analysisResolution = "1920x1080";

        setSeekBars();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        int screenWidth = displayMetrics.widthPixels;

        previewView.setRotation(90);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(screenHeight, screenHeight);
        previewView.setLayoutParams(layoutParams);

        orientationEventListener = new OrientationEventListener((Context)this) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation >= 45 && orientation < 135) {
                    previewView.setRotation(180);
                    ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(screenWidth, screenHeight);
                    previewView.setLayoutParams(layoutParams);
                } else if (orientation >= 225 && orientation < 315) {
                    previewView.setRotation(0);
                    ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(screenWidth, screenHeight);
                    previewView.setLayoutParams(layoutParams);
                }
                disableOrientationListener();
            }
        };

        orientationEventListener.enable();
        requestCameraPermission();
    }


    private void setSeekBars(){
        SeekBar sbR = findViewById(R.id.seekBarR);
        sbR.getThumb().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
        SeekBar sbG = findViewById(R.id.seekBarG);
        sbG.getThumb().setColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
        SeekBar sbB = findViewById(R.id.seekBarB);
        sbB.getThumb().setColorFilter(0xFF0000FF, PorterDuff.Mode.MULTIPLY);

        sbR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                filterR = i;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbG.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                filterG = i;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                filterB = i;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void disableOrientationListener(){
        orientationEventListener.disable();
    }

    private void requestCameraPermission () {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            startCamera();
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestCameraPermissionLauncher.launch(
                    android.Manifest.permission.CAMERA);
        }
    }

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                }
                else {
                    Toast toast = Toast.makeText(this, "Please allow the camera permission", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance(this);
        processCameraProvider.addListener(() -> {
            try {
                ImageAnalysis imageAnalysis;
                ProcessCameraProvider cameraProvider= processCameraProvider.get();

                if (!analysisResolution.equals("")) {
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setTargetRotation(Surface.ROTATION_90)
                            .setTargetResolution(Size.parseSize(analysisResolution))
                            .build();
                }
                else {
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setTargetRotation(Surface.ROTATION_90)
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .build();
                }

                imageAnalysis.setAnalyzer(Executors.newCachedThreadPool(), this::analyze);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    public void analyze(@NonNull ImageProxy imageProxy) {
        ImageProxy.PlaneProxy planeProxy = imageProxy.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();

        Bitmap bm = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
        for (int i = 0; i < buffer.capacity(); i += 4){
            int valueR = Byte.toUnsignedInt(buffer.get(i));
            int valueG = Byte.toUnsignedInt(buffer.get(i + 1));
            int valueB = Byte.toUnsignedInt(buffer.get(i + 2));
            //int valueA = Byte.toUnsignedInt(buffer.get(i + 3));

            int sum = valueR + valueG + valueB;
            int newValueR = sum/3;
            int newValueG = sum/3;
            int newValueB = sum/3;
            //int newValueA = valueA;

            boolean filterAppliedR = false;
            boolean filterAppliedG = false;
            boolean filterAppliedB = false;

            if ((float)valueR/sum > (float)(100 - filterR)/100) {
                newValueR = valueR;
                filterAppliedR = true;
            }
            if ((float)valueG/sum > (float)(100 - filterG)/100) {
                newValueG = valueG;
                filterAppliedG = true;
            }
            if ((float)valueB/sum > (float)(100 - filterB)/100) {
                newValueB = valueB;
                filterAppliedB = true;
            }

            if (filterAppliedR || filterAppliedG ||filterAppliedB){
                if (!filterAppliedR){
                    newValueR = 0;
                }
                if (!filterAppliedG){
                    newValueG = 0;
                }
                if (!filterAppliedB){
                    newValueB = 0;
                }
            }

            byte newByteValueR = (byte)newValueR;
            byte newByteValueG = (byte)newValueG;
            byte newByteValueB = (byte)newValueB;
            //byte newByteValueA = (byte)newValueA;
            buffer.put(i, newByteValueR);
            buffer.put(i + 1, newByteValueG);
            buffer.put(i + 2, newByteValueB);
            //buffer.put(i + 3, newByteValueA);
        }
        bm.copyPixelsFromBuffer(buffer);
        runOnUiThread(() -> previewView.setImageBitmap(bm));

        imageProxy.close();
    }
}