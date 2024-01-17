package com.bluejay.filterrgb;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.media.Image;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ImageView previewView;
    private OrientationEventListener orientationEventListener;
    private String analysisResolution = "";
    private Matrix previewRotationMatrix = new Matrix();

    private float colorDeviation = 0;
    private float colorFilter = 0;
    float[] colorRange = new float[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        analysisResolution = "360x640";


        setSeekBars();

        orientationEventListener = new OrientationEventListener((Context)this) {
            @Override
            public void onOrientationChanged(int orientation) {


                if (orientation >= 45 && orientation < 135) {
                    previewRotationMatrix.postRotate(180);
                } else if (orientation >= 225 && orientation < 315) {
                    previewRotationMatrix.postRotate(0);
                }
                else {
                    previewRotationMatrix.postRotate(90);
                }
                disableOrientationListener();
            }
        };

        orientationEventListener.enable();

        requestCameraPermission();
    }


    private void setSeekBars(){
        SeekBar sbRGB = findViewById(R.id.seekBarRGB);
        SeekBar sbRange = findViewById(R.id.seekBarRange);
        sbRGB.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);

        sbRGB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                colorFilter = (float) 3.6 * i;
                float[] hsv = {colorFilter, 100, 100};
                int color = Color.HSVToColor(hsv);
                sbRGB.getThumb().setColorFilter(color, PorterDuff.Mode.MULTIPLY);

                if (colorDeviation == 180){
                    colorRange[0] = -1;
                    colorRange[1] = 361;
                }
                else {
                    colorRange[0] = (360 + colorFilter - colorDeviation) % 360;
                    colorRange[1] = (colorFilter + colorDeviation) % 360;
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbRange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                colorDeviation = (float) 1.8 * i;

                if (i == 100){
                    colorRange[0] = -1;
                    colorRange[1] = 361;
                }
                else {
                    colorRange[0] = (360 + colorFilter - colorDeviation) % 360;
                    colorRange[1] = (colorFilter + colorDeviation) % 360;
                }
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
                            .setTargetResolution(Size.parseSize(analysisResolution))
                            .setTargetRotation(Surface.ROTATION_0)
                            .build();
                }
                else {
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .setTargetRotation(Surface.ROTATION_0)
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


    private float[] RGBtoHSV(int[] rgb){
        float r = (float)rgb[0] / 255;
        float g = (float)rgb[1] / 255;
        float b = (float)rgb[2] / 255;

        float cmax = Math.max(r, Math.max(g, b));
        float cmin = Math.min(r, Math.min(g, b));
        float diff = cmax - cmin;
        float h = -1, s = -1;

        if (cmax == cmin) {
            h = 0;
        }
        else if (cmax == r) {
            h = (60 * ((g - b) / diff) + 360) % 360;
        }

        else if (cmax == g) {
            h = (60 * ((b - r) / diff) + 120) % 360;
        }

        else if (cmax == b) {
            h = (60 * ((r - g) / diff) + 240) % 360;
        }

        if (cmax == 0) {
            s = 0;
        }
        else {
            s = (diff / cmax) * 100;
        }

        float v = cmax * 100;

        return new float[]{h,s,v};
    }

    public void analyze(@NonNull ImageProxy imageProxy) {
        ImageProxy.PlaneProxy planeProxy = imageProxy.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();

        Bitmap bm = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
        for (int i = 0; i < buffer.capacity(); i += 4){

            int valueR = Byte.toUnsignedInt(buffer.get(i));
            int valueG = Byte.toUnsignedInt(buffer.get(i + 1));
            int valueB = Byte.toUnsignedInt(buffer.get(i + 2));

            float[] hsv = RGBtoHSV(new int[]{valueR, valueG, valueB});

            if ((int)colorRange[1] < (int)colorRange[0]){
                if (!(hsv[0] > colorRange[0] || hsv[0] < colorRange[1])){
                    int average = (valueR + valueG + valueB)/3;
                    buffer.put(i, (byte)average);
                    buffer.put(i + 1, (byte)average);
                    buffer.put(i + 2, (byte)average);
                }
            }
            else{
                if (!(hsv[0] > colorRange[0] && hsv[0] < colorRange[1])){
                    int average = (valueR + valueG + valueB)/3;
                    buffer.put(i, (byte)average);
                    buffer.put(i + 1, (byte)average);
                    buffer.put(i + 2, (byte)average);
                }
            }
        }
        bm.copyPixelsFromBuffer(buffer);

        Bitmap finalBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), previewRotationMatrix, true);

        runOnUiThread(() -> previewView.setImageBitmap(finalBm));

        imageProxy.close();
    }
}