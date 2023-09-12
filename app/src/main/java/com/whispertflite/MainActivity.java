package com.whispertflite;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.whispertflite.common.Recorder;
import com.whispertflite.common.Transcriber;
import com.whispertflite.common.WaveUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";

    private Button btnMicRec;
    private Button btnTranscb;
    private TextView tvResult;
    private Handler mHandler;
    private String mSelectedFile;
    private Recorder mRecorder = null;
    private Transcriber mTranscriber = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mHandler = new Handler(Looper.getMainLooper());
        tvResult = findViewById(R.id.tvResult);

        // Implementation of transcribe button functionality
        btnTranscb = findViewById(R.id.btnTranscb);
        btnTranscb.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isRecordingInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }

            if (mTranscriber != null && mTranscriber.isTranscriptionInProgress()) {
                Log.d(TAG, "Transcription is already in progress...!");
            } else {
                Log.d(TAG, "Start transcription...");
                startTranscription();
            }
        });

        // Implementation of record button functionality
        btnMicRec = findViewById(R.id.btnMicRecord);
        btnMicRec.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isRecordingInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            } else {
                Log.d(TAG, "Start recording...");
                startRecording();
            }
        });

        // Implementation of file spinner functionality
        ArrayList<String> files = new ArrayList<>();
        // files.add(WaveUtil.RECORDING_FILE);
        try {
            String[] assetFiles = getAssets().list("");
            for (String file : assetFiles) {
                if (file.endsWith(".wav"))
                    files.add(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] fileArray = new String[files.size()];
        files.toArray(fileArray);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = findViewById(R.id.spnrFiles);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedFile = fileArray[position];
                if (mSelectedFile.equals(WaveUtil.RECORDING_FILE))
                    btnMicRec.setVisibility(View.VISIBLE);
                else
                    btnMicRec.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // English-only model and vocab
//        boolean isMultilingual = false;
//        String modelPath = getFilePath("whisper-tiny-en.tflite");
//        String vocabPath = getFilePath("filters_vocab_gen.bin");

        // Multilingual model and vocab
        boolean isMultilingual = true;
        String modelPath = getFilePath("whisper-tiny.tflite");
        String vocabPath = getFilePath("filters_vocab_multilingual.bin");

        // TODO: pass model and vocab as per requirement
        mTranscriber = new Transcriber(this, modelPath, vocabPath, isMultilingual);
        mTranscriber.setUpdateListener(message -> mHandler.post(() -> tvResult.setText(message)));

        mRecorder = new Recorder(this);
        mRecorder.setUpdateListener(message -> {
            mHandler.post(() -> tvResult.setText(message));
            if (message.equals(getString(R.string.recording_is_completed)))
                mHandler.post(() -> btnMicRec.setText(getString(R.string.record)));
        });

        // Call the method to copy specific file types from assets to data folder
        String[] extensionsToCopy = {"pcm", "bin", "wav", "tflite"};
        copyAssetsWithExtensionsToDataFolder(this, extensionsToCopy);

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission();

        // for debugging
//        testParallelTranscriptions();
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Requesting record permission");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "Record permission is granted");
        else
            Log.d(TAG, "Record permission is not granted");
    }

    private void startRecording() {
        checkRecordPermission();

        String waveFilePath = getFilePath(WaveUtil.RECORDING_FILE);
        mRecorder.setFilePath(waveFilePath);
        mRecorder.startRecording();
        mHandler.post(() -> btnMicRec.setText(getString(R.string.stop)));
    }

    private void stopRecording() {
        mRecorder.stopRecording();
        mHandler.post(() -> btnMicRec.setText(getString(R.string.record)));
    }

    private void startTranscription() {
        String waveFilePath = getFilePath(mSelectedFile);
        mTranscriber.setFilePath(waveFilePath);
        mTranscriber.startTranscription();
    }

    public static void copyAssetsWithExtensionsToDataFolder(Context context, String[] extensions) {
        AssetManager assetManager = context.getAssets();

        try {
            // Specify the destination directory in the app's data folder
            String destFolder = context.getFilesDir().getAbsolutePath();

            for (String extension : extensions) {
                // List all files in the assets folder with the specified extension
                String[] assetFiles = assetManager.list("");
                for (String assetFileName : assetFiles) {
                    if (assetFileName.endsWith("." + extension)) {
                        InputStream inputStream = assetManager.open(assetFileName);
                        File outFile = new File(destFolder, assetFileName);
                        OutputStream outputStream = new FileOutputStream(outFile);

                        // Copy the file from assets to the data folder
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }

                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Copies specified asset to app's files directory and returns its absolute path.
    private String getFilePath(String assetName) {
        File outfile = new File(getFilesDir(), assetName);
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.getAbsolutePath());
        }

        Log.d(TAG, "Returned asset path: " + outfile.getAbsolutePath());
        return outfile.getAbsolutePath();
    }

    // Test code for parallel transcriptions
    private void testParallelTranscriptions() {

        // Define the file names in an array
        String[] fileNames = {
                "english_test1.wav",
                "english_test2.wav",
                "english_test_3_bili.wav"
        };

        // Multilingual model and vocab
        String modelMultilingual = getFilePath("whisper-tiny.tflite");
        String vocabMultilingual = getFilePath("filters_vocab_multilingual.bin");

        // Perform transcription for multiple audio files using multilingual model
        for (String fileName : fileNames) {
            Transcriber transcriber = new Transcriber(this, modelMultilingual, vocabMultilingual, true);
            transcriber.setUpdateListener(message -> mHandler.post(() -> tvResult.setText(message)));
            String waveFilePath = getFilePath(fileName);
            transcriber.setFilePath(waveFilePath);
            transcriber.startTranscription();
        }

        // English-only model and vocab
        String modelEnglish = getFilePath("whisper-tiny-en.tflite");
        String vocabEnglish = getFilePath("filters_vocab_gen.bin");

        // Perform transcription for multiple audio files using english only model
        for (String fileName : fileNames) {
            Transcriber transcriber = new Transcriber(this, modelEnglish, vocabEnglish, false);
            transcriber.setUpdateListener(message -> mHandler.post(() -> tvResult.setText(message)));
            String waveFilePath = getFilePath(fileName);
            transcriber.setFilePath(waveFilePath);
            transcriber.startTranscription();
        }
    }
}