package me.singleneuron.qscompass.wear;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.wear.ambient.AmbientModeSupport;

import java.util.Arrays;

import me.singleneuron.qscompass.wear.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SensorEventListener, AmbientModeSupport.AmbientCallbackProvider {

    private SensorManager sensorManager;
    private AmbientModeSupport.AmbientController ambientController;
    private Sensor aSensor;
    private Sensor mSensor;
    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];
    private boolean[] lastSensorValue = new boolean[2];
    private float radianFloat = 0F;
    private float degreeFloat = 0F;
    private int lastDegree = 0;
    private boolean hasFeature = true;
    private boolean mDoBurnInProtection;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        PackageManager packageManager = getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)) {
            Toast.makeText(this, "Compass Not Found", Toast.LENGTH_SHORT).show();
            hasFeature = false;
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            Toast.makeText(this, "Accelerometer Not Found", Toast.LENGTH_SHORT).show();
            hasFeature = false;
        }
        if (!hasFeature) return;

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        aSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (aSensor == null) {
            Toast.makeText(this, "Accelerometer Not Found", Toast.LENGTH_SHORT).show();
            hasFeature = false;
        }

        if (mSensor == null) {
            Toast.makeText(this, "Magnetic Not Found", Toast.LENGTH_SHORT).show();
            hasFeature = false;
        }
        if (!hasFeature) return;

        ambientController = AmbientModeSupport.attach(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
    }

    private void resume() {
        if (hasFeature) {
            sensorManager.registerListener(this, aSensor, BuildConfig.DEBUG ? SensorManager.SENSOR_DELAY_NORMAL : SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, mSensor, BuildConfig.DEBUG ? SensorManager.SENSOR_DELAY_NORMAL : SensorManager.SENSOR_DELAY_UI);
        } else {
            binding.constraintLayout.setVisibility(View.GONE);
            binding.tips.setText(getString(R.string.notSupport));
            binding.constraintLayout.setVisibility(View.VISIBLE);
        }
    }

    private void stop() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticFieldValues = event.values;
            Log.d("Magnetic update", Arrays.toString(event.values));
            lastSensorValue[0] = true;
            //Log.d("sensorUpdate:MAGNETIC",magneticFieldValues.joinToString())
        }
        if (event != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values;
            Log.d("Accelerometer update", Arrays.toString(event.values));
            lastSensorValue[1] = true;
            //Log.d("sensorUpdate:ACCELEROMETER",accelerometerValues.joinToString())
        }
        if (!(lastSensorValue[0] && lastSensorValue[1])) return;
        lastSensorValue[0] = false;
        lastSensorValue[1] = false;
        calculateOrientation();
    }

    private void calculateOrientation() {
        //Log.d("statue","calculateOrientation");
        float[] values = new float[3];
        float[] R1 = new float[9];
        SensorManager.getRotationMatrix(R1, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(R1, values);
        //if (values[0]==radianFloat) return;
        radianFloat = values[0];
        degreeFloat = (float) Math.toDegrees(values[0]);
        int degree = (int) degreeFloat;
        //if (degree==lastDegree) return;
        lastDegree = degree;
        if (degree < 0) degree += 360;
        String s = "";
        if ((degree >= 355 && degree < 360) || (degree >= 0 && degree < 5))
            s = getString(R.string.N);
        else if (degree >= 5 && degree < 85) s = getString(R.string.EN);
        else if (degree >= 85 && degree < 95) s = getString(R.string.E);
        else if (degree >= 95 && degree < 175) s = getString(R.string.ES);
        else if (degree >= 175 && degree < 185) s = getString(R.string.S);
        else if (degree >= 185 && degree < 265) s = getString(R.string.WS);
        else if (degree >= 265 && degree < 275) s = getString(R.string.W);
        else if (degree >= 275 && degree < 355) s = getString(R.string.WN);

        binding.textView.setText(s + " " + degree + "°");

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.navigation);
        Bitmap bmResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmResult);
        canvas.rotate(360F - degreeFloat, (float) (bitmap.getWidth() / 2.0), (float) (bitmap.getHeight() / 2.0));
        canvas.drawBitmap(bitmap, 0f, 0f, null);

        binding.imageView.setImageBitmap(bmResult);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new MyAmbientCallback();
    }

    private class MyAmbientCallback extends AmbientModeSupport.AmbientCallback {

        private static final int BURN_IN_OFFSET_PX = 10;

        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            super.onEnterAmbient(ambientDetails);
            stop();
            mDoBurnInProtection = ambientDetails.getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false);
            binding.constraintLayout.setVisibility(View.GONE);
            binding.tips.setText(R.string.ambientMode);
            binding.tips.setVisibility(View.VISIBLE);
            binding.contentView.setBackgroundColor(getColor(R.color.oled_dark));
        }

        @Override
        public void onUpdateAmbient() {
            super.onUpdateAmbient();
            if (mDoBurnInProtection) {
                int x = (int) (Math.random() * 2 * BURN_IN_OFFSET_PX - BURN_IN_OFFSET_PX);
                int y = (int) (Math.random() * 2 * BURN_IN_OFFSET_PX - BURN_IN_OFFSET_PX);
                binding.getRoot().setPadding(x, y, 0, 0);
            }
        }

        @Override
        public void onExitAmbient() {
            super.onExitAmbient();
            resume();
            if (mDoBurnInProtection) {
                binding.getRoot().setPadding(0, 0, 0, 0);
            }
            binding.constraintLayout.setVisibility(View.VISIBLE);
            binding.tips.setVisibility(View.GONE);
            binding.contentView.setBackgroundColor(getColor(R.color.dark_grey));
        }
    }

}
