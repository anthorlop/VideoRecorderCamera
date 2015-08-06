/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package es.anthorlop.camera.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import es.anthorlop.CameraConfig;
import es.anthorlop.camera.R;
import es.anthorlop.camera.custom.CameraPreview;

/**
 *
 * Actividad principal para el control de la camara
 *
 * @author anthorlop
 *
 */
public class CameraActivity extends Activity {

    private Camera mCamera;
    private CameraPreview mPreview;

    private MediaRecorder mediaRecorder;

    @InjectView(R.id.button_capture)
    ImageView capture;

    @InjectView(R.id.button_ChangeCamera)
    ImageView switchCamera;

    private Context myContext;

    @InjectView(R.id.camera_preview)
    LinearLayout cameraPreview;

    private String url_file;

    private static boolean cameraFront = false;
    private static boolean flash = false;

    @InjectView(R.id.buttonQuality)
    Button buttonQuality;

    @InjectView(R.id.listOfQualities)
    ListView listOfQualities;

    @InjectView(R.id.buttonFlash)
    ImageView buttonFlash;

    @InjectView(R.id.chronoRecordingImage)
    ImageView chronoRecordingImage;

    @InjectView(R.id.textChrono)
    Chronometer chrono;

    private long countUp;

    private int quality = CamcorderProfile.QUALITY_480P;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (recording) {
            // stop recording and release camera
            mediaRecorder.stop(); // stop the recording

            if (chrono != null && chrono.isActivated())
                chrono.stop();

            File mp4 = new File(url_file);
            if (mp4.exists() && mp4.isFile()) {
                mp4.delete();
            }

            releaseMediaRecorder(); // release the MediaRecorder object
            Toast.makeText(CameraActivity.this, "La grabación se ha detenido", Toast.LENGTH_LONG).show();
            recording = false;
        }

        ButterKnife.reset(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        myContext = this;

        ButterKnife.inject(this);

        initialize();
    }

    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                cameraFront = true;
                break;
            }
        }
        return cameraId;
    }

    private int findBackFacingCamera() {
        int cameraId = -1;
        // Search for the back facing camera
        // get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        // for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                cameraFront = false;
                break;
            }
        }
        return cameraId;
    }

    public void onResume() {
        super.onResume();
        if (!hasCamera(myContext)) {
            Toast toast = Toast.makeText(myContext, "Sorry, your phone does not have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null) {

            releaseCamera();

            final boolean frontal = cameraFront;

            // if the front facing camera does not exist
            int cameraId = findFrontFacingCamera();
            if (cameraId < 0) {

                // desactivar el cambio de camara
                switchCameraListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(CameraActivity.this, "No front facing camera found.", Toast.LENGTH_LONG).show();
                    }
                };

                // seleccionar la camara trasera
                cameraId = findBackFacingCamera();
                if (flash) {
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    buttonFlash.setImageResource(R.drawable.ic_flash_on_white);
                }
            } else if (!frontal) {

                // seleccionar la camara trasera sin desactivar la delantera
                cameraId = findBackFacingCamera();
                if (flash) {
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    buttonFlash.setImageResource(R.drawable.ic_flash_on_white);
                }
            }

            mCamera = Camera.open(cameraId);
            mPreview.refreshCamera(mCamera);
            reloadQualities(cameraId);

        }
    }

    public void initialize() {

        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);

        capture.setOnClickListener(captrureListener);

        switchCamera.setOnClickListener(switchCameraListener);

        buttonQuality.setOnClickListener(qualityListener);

        buttonFlash.setOnClickListener(flashListener);
    }

    private void reloadQualities(int idCamera) {


        SharedPreferences prefs = getSharedPreferences("RECORDING", Context.MODE_PRIVATE);

        quality = prefs.getInt("QUALITY", CamcorderProfile.QUALITY_480P);

        changeVideoQuality(quality);

        final ArrayList<String> list = new ArrayList<String>();

        int maxQualitySupported = CamcorderProfile.QUALITY_480P;

        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_480P)) {
            list.add("480p");
            maxQualitySupported = CamcorderProfile.QUALITY_480P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_720P)) {
            list.add("720p");
            maxQualitySupported = CamcorderProfile.QUALITY_720P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_1080P)) {
            list.add("1080p");
            maxQualitySupported = CamcorderProfile.QUALITY_1080P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_2160P)) {
            list.add("2160p");
            maxQualitySupported = CamcorderProfile.QUALITY_2160P;
        }

        if (!CamcorderProfile.hasProfile(idCamera, quality)) {
            quality = maxQualitySupported;
            updateButtonText(maxQualitySupported);
        }

        final StableArrayAdapter adapter = new StableArrayAdapter(this,
                android.R.layout.simple_list_item_1, list);
        listOfQualities.setAdapter(adapter);

        listOfQualities.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                final String item = (String) parent.getItemAtPosition(position);

                buttonQuality.setText(item);

                if (item.equals("480p")) {
                    changeVideoQuality(CamcorderProfile.QUALITY_480P);
                } else if (item.equals("720p")) {
                    changeVideoQuality(CamcorderProfile.QUALITY_720P);
                } else if (item.equals("1080p")) {
                    changeVideoQuality(CamcorderProfile.QUALITY_1080P);
                } else if (item.equals("2160p")) {
                    changeVideoQuality(CamcorderProfile.QUALITY_2160P);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    listOfQualities.animate().setDuration(200).alpha(0)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    listOfQualities.setVisibility(View.GONE);
                                }
                            });
                } else {
                    listOfQualities.setVisibility(View.GONE);
                }
            }

        });

    }

    View.OnClickListener qualityListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // get the number of cameras
            if (!recording) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                        && listOfQualities.getVisibility() == View.GONE) {
                    listOfQualities.setVisibility(View.VISIBLE);
                    listOfQualities.animate().setDuration(200).alpha(95)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {

                                }

                            });
                } else {
                    listOfQualities.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    View.OnClickListener flashListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // get the number of cameras
            if (!recording && !cameraFront) {
                if (flash) {
                    flash = false;
                    buttonFlash.setImageResource(R.drawable.ic_flash_off_white);
                    setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                } else {
                    flash = true;
                    buttonFlash.setImageResource(R.drawable.ic_flash_on_white);
                    setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            }
        }
    };

    View.OnClickListener switchCameraListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // get the number of cameras
            if (!recording) {
                int camerasNumber = Camera.getNumberOfCameras();
                if (camerasNumber > 1) {
                    // release the old camera instance
                    // switch camera, from the front and the back and vice versa

                    releaseCamera();
                    chooseCamera();
                } else {
                    Toast toast = Toast.makeText(myContext, "Sorry, your phone has only one camera!", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        }
    };

    public void chooseCamera() {
        // if the camera preview is the front
        if (cameraFront) {
            int cameraId = findBackFacingCamera();
            if (cameraId >= 0) {
                // open the backFacingCamera
                // set a picture callback
                // refresh the preview

                mCamera = Camera.open(cameraId);

                // mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);

                reloadQualities(cameraId);

            }
        } else {
            int cameraId = findFrontFacingCamera();
            if (cameraId >= 0) {
                // open the backFacingCamera
                // set a picture callback
                // refresh the preview

                mCamera = Camera.open(cameraId);

                // al poner la camara frontal se desactiva el flash
                if (flash) {
                    flash = false;
                    buttonFlash.setImageResource(R.drawable.ic_flash_off_white);
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }

                // mPicture = getPictureCallback();
                mPreview.refreshCamera(mCamera);

                reloadQualities(cameraId);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // when on Pause, release camera in order to be used from other
        // applications
        releaseCamera();
    }

    private boolean hasCamera(Context context) {
        // check if the device has camera
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    boolean recording = false;
    View.OnClickListener captrureListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (recording) {
                // stop recording and release camera
                mediaRecorder.stop(); // stop the recording

                stopChronometer();

                capture.setImageResource(R.drawable.player_record);

                changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

                releaseMediaRecorder(); // release the MediaRecorder object
                Toast.makeText(CameraActivity.this, "Video captured!", Toast.LENGTH_LONG).show();
                recording = false;
            } else {
                if (!prepareMediaRecorder()) {
                    Toast.makeText(CameraActivity.this, "Fail in prepareMediaRecorder()!\n - Ended -", Toast.LENGTH_LONG).show();
                    finish();
                }
                // work on UiThread for better performance
                runOnUiThread(new Runnable() {
                    public void run() {
                        // If there are stories, add them to the table

                        try {

                            mediaRecorder.start();

                            startChronometer();

                            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                                changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            } else {
                                changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            }

                            capture.setImageResource(R.drawable.player_stop);

                        } catch (final Exception ex) {
                            // Log.i("---","Exception in thread");
                        }
                    }
                });

                recording = true;
            }
        }
    };

    private void changeRequestedOrientation(int orientation) {
        setRequestedOrientation(orientation);
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock(); // lock camera for later use
        }
    }

    private boolean prepareMediaRecorder() {

        mediaRecorder = new MediaRecorder();

        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (cameraFront) {
                mediaRecorder.setOrientationHint(270);
            } else {
                mediaRecorder.setOrientationHint(90);
            }
        }

        mediaRecorder.setProfile(CamcorderProfile.get(quality));

        File file = new File(Environment.getExternalStorageDirectory().getPath() + "/anthorlopCamera");
        if (!file.exists()) {
            file.mkdirs();
        }

        Date d = new Date();
        String timestamp = String.valueOf(d.getTime());

        url_file = Environment.getExternalStorageDirectory().getPath() + "/reporter/preview_" + timestamp + ".mp4";

        mediaRecorder.setOutputFile(url_file);
        mediaRecorder.setMaxDuration(CameraConfig.MAX_DURATION_RECORD); // Set max duration 60 sec.
        mediaRecorder.setMaxFileSize(CameraConfig.MAX_FILE_SIZE_RECORD); // Set max file size 50M

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;

    }

    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void changeVideoQuality(int quality) {
        SharedPreferences prefs = getSharedPreferences("RECORDING", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("QUALITY", quality);

        editor.commit();

        this.quality = quality;

        updateButtonText(quality);
    }

    private void updateButtonText(int quality) {
        if (quality == CamcorderProfile.QUALITY_480P)
            buttonQuality.setText("480p");
        if (quality == CamcorderProfile.QUALITY_720P)
            buttonQuality.setText("720p");
        if (quality == CamcorderProfile.QUALITY_1080P)
            buttonQuality.setText("1080p");
        if (quality == CamcorderProfile.QUALITY_2160P)
            buttonQuality.setText("2160p");
    }

    private class StableArrayAdapter extends ArrayAdapter<String> {

        HashMap<String, Integer> mIdMap = new HashMap<String, Integer>();

        public StableArrayAdapter(Context context, int textViewResourceId,
                                  List<String> objects) {
            super(context, textViewResourceId, objects);
            for (int i = 0; i < objects.size(); ++i) {
                mIdMap.put(objects.get(i), i);
            }
        }

        @Override
        public long getItemId(int position) {
            String item = getItem(position);
            return mIdMap.get(item);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

    }

    public void setFlashMode(String mode) {

        try {
            if (getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_CAMERA_FLASH)
                    && mCamera != null
                    && !cameraFront) {

                mPreview.setFlashMode(mode);
                mPreview.refreshCamera(mCamera);

            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getBaseContext(), "Exception changing flashLight mode",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startChronometer() {

        chrono.setVisibility(View.VISIBLE);

        final long startTime = SystemClock.elapsedRealtime();

        chrono.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer arg0) {
                countUp = (SystemClock.elapsedRealtime() - startTime) / 1000;

                if (countUp % 2 == 0) {
                    chronoRecordingImage.setVisibility(View.VISIBLE);
                } else {
                    chronoRecordingImage.setVisibility(View.INVISIBLE);
                }

                String asText = String.format("%02d", countUp / 60) + ":" + String.format("%02d", countUp % 60);
                chrono.setText(asText);
            }
        });
        chrono.start();
    }

    private void stopChronometer() {
        chrono.stop();
        chronoRecordingImage.setVisibility(View.INVISIBLE);
        chrono.setVisibility(View.INVISIBLE);
    }

    public static void reset() {
        flash = false;
        cameraFront = false;
    }

}

