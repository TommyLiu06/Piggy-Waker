package com.tl06.piggywaker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainFragment extends Fragment implements SensorEventListener {
    private GestureDetector gestureDetector;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Vibrator vibrator;
    private boolean isFunctionStarted = false;
    private boolean isPaused = false;
    private long initTime;
    private long startTime;
    private Handler handler;
    private Runnable updateTimeRunnable;
    private TextView timeTextView;
    private TextView statusTextView;
    private TextView timerTextView;
    private TextView piggyWakerTextView;
    private TextView accelerationTextView;
    private static final int THRESHOLD = 1; // 合加速度阈值，单位：m/s²
    private static final int DURATION = 5000; // 检测持续时间（毫秒）

    // 用于高通滤波器的常量
    private static final float ALPHA = 0.8f;
    private final float[] gravity = new float[3];
    private final float[] linearAcceleration = new float[3];

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);

        timeTextView = view.findViewById(R.id.timeTextView);
        statusTextView = view.findViewById(R.id.statusTextView);
        timerTextView = view.findViewById(R.id.timerTextView);
        piggyWakerTextView = view.findViewById(R.id.piggyWakerTextView);
        accelerationTextView = view.findViewById(R.id.accelerationTextView);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (isFunctionStarted) {
                    if (isPaused) {
                        resumeFunction();
                    } else {
                        pauseFunction();
                    }
                } else {
                    startFunction();
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                if (isFunctionStarted) {
                    stopFunction();
                }
            }
        });

        view.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });

        handler = new Handler();
        updateTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFunctionStarted && !isPaused) {
                    long elapsedTime = System.currentTimeMillis() - initTime;
                    timerTextView.setText(formatTime(elapsedTime));
                }
                timeTextView.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                handler.postDelayed(this, 1000);
            }
        };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (isFunctionStarted && !isPaused) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        handler.post(updateTimeRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(updateTimeRunnable);
    }

    private void startFunction() {
        isFunctionStarted = true;
        isPaused = false;
        initTime = startTime = System.currentTimeMillis();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        accelerationTextView.setVisibility(View.VISIBLE);
        fadeOutTextView(piggyWakerTextView);
        fadeInTextView(statusTextView);
        fadeInTextView(timerTextView);
        statusTextView.setText(R.string.running);
    }

    private void pauseFunction() {
        isPaused = true;
        sensorManager.unregisterListener(this);
        statusTextView.setText(R.string.pause);
    }

    private void resumeFunction() {
        isPaused = false;
        startTime = System.currentTimeMillis();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        statusTextView.setText(R.string.running);
    }

    private void stopFunction() {
        isFunctionStarted = false;
        isPaused = false;
        sensorManager.unregisterListener(this);

        accelerationTextView.setVisibility(View.GONE);
        fadeOutTextView(statusTextView);
        fadeOutTextView(timerTextView);
        fadeInTextView(piggyWakerTextView);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isFunctionStarted && !isPaused) {
            // 高通滤波器，去除重力加速度的影响
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];

            // 计算合加速度
            double totalAcceleration = Math.sqrt(linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2]);

            // 实时显示合加速度值
            accelerationTextView.setText(String.format(Locale.getDefault(), "acc: %.2f m/s²", totalAcceleration));

            long currentTime = System.currentTimeMillis();

            if (totalAcceleration < THRESHOLD) {
                if (currentTime - startTime > DURATION) {
                    if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
                        vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
                        startTime += 250;
                    }
                }

            } else {
                startTime = currentTime;
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要实现
    }

    private void fadeOutTextView(TextView targetTextView) {
        View rootView = getView();
        if (rootView != null) {
            targetTextView.animate().alpha(0f).setDuration(500).withEndAction(() -> targetTextView.setVisibility(View.GONE));
        }
    }

    private void fadeInTextView(TextView targetTextView) {
        View rootView = getView();
        if (rootView != null) {
            targetTextView.setVisibility(View.VISIBLE);
            targetTextView.animate().alpha(1f).setDuration(500);
        }
    }

    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000) % 60;
        int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
        int hours = (int) ((milliseconds / (1000 * 60 * 60)) % 24);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
