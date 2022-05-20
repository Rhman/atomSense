package com.nick.atomsense;

import android.content.*;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import lecho.lib.hellocharts.view.LineChartView;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private AudioManager am;
    private BroadcastReceiver receiver;

    private Button runButton;
    private Button stopButton;
    private Button saveButton;

    private boolean btEnabled = false;
    private Switch btEnableSwitch;

    private TextView cpsLabel;

    private TextView triggerLabel;
    private static final String TRIGGER_LEVEL_LABEL = "Trigger level: ";
    private static final String TRIGGER_LEVEL_DIMENSION = " %";
    private SeekBar triggerLevelBar;
    private Integer triggerLevel;

    private TextView impulseWidthLabel;
    private static final String IMPULSE_WIDTH_LABEL = "Impulse width: ";
    private static final String IMPULSE_WIDTH_DIMENSION = " ms";
    private SeekBar impulseWidthBar;
    private Integer impulseWidth;

    private LineChartView chart;

    private AudioService audioService = new AudioService();

    private boolean settingsMode = true;

    private EditText aFactorView;
    private Float factorA;

    private EditText bFactorView;
    private Float factorB;

    private EditText cFactorView;
    private Float factorC;

    public static final String SETTING_MENU_MODE = "SETTING_MENU_MODE";
    public static final String SETTING_BT_ENABLE = "SETTING_BT_ENABLE";
    public static final String SETTING_TRIGGER_LEVEL = "SETTING_TRIGGER_LEVEL";
    public static final String SETTING_IMPULSE_WIDTH = "SETTING_IMPULSE_WIDTH";
    public static final String SETTING_FACTOR_A = "SETTING_FACTOR_A";
    public static final String SETTING_FACTOR_B = "SETTING_FACTOR_B";
    public static final String SETTING_FACTOR_C = "SETTING_FACTOR_C";

    private Thread cpsPreview;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try{
            setContentView(R.layout.activity_settings);
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            settingsMode = preferences.getBoolean(SETTING_MENU_MODE, true);
            btEnabled = preferences.getBoolean(SETTING_BT_ENABLE, false);
            triggerLevel = preferences.getInt(SETTING_TRIGGER_LEVEL, -1);
            triggerLevel = triggerLevel == -1 ? null : triggerLevel;
            impulseWidth = preferences.getInt(SETTING_IMPULSE_WIDTH, -1);
            impulseWidth = impulseWidth == -1 ? null : impulseWidth;
            factorA = preferences.getFloat(SETTING_FACTOR_A, 0);
            factorB = preferences.getFloat(SETTING_FACTOR_B, 0);
            factorC = preferences.getFloat(SETTING_FACTOR_C, 0);

            initControls();
            am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioService.init();

            if(!settingsMode){
                openMeasureActivity();
            }
        } catch (Exception e){
            Log.e(TAG, "in onCreate:" + e);
            throw e;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try{
            audioService.stop();
            if(am.isBluetoothScoOn()){
                am.stopBluetoothSco();
            }
            if(cpsPreview != null){
                cpsPreview.interrupt();
                cpsPreview = null;
            }
        } catch (Exception e){
            Log.e(TAG, "in onStop:" + e);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        try{
            aFactorView.setText(factorA.toString());
            bFactorView.setText(factorB.toString());
            cFactorView.setText(factorC.toString());
        } catch (Exception e){
            Log.e(TAG, "in onRestart:" + e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(receiver != null){
                unregisterReceiver(receiver);
            }
        } catch (Exception e){
            Log.e(TAG, "in onDestroy:" + e);
        }
    }

    private void initControls(){
        try {
            runButton = (Button)findViewById(R.id.btnRunId);
            runButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    updateCps();
                    if (btEnabled){
                        am.startBluetoothSco();
                    }
                    try {
                        audioService.runSettingsMode(chart);
                    } catch (Exception e){
                        Log.e(TAG, "in onClick(runButton):" + e);
                    }
                }
            });

            stopButton = (Button)findViewById(R.id.btnStopId);
            stopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (btEnabled){
                        am.stopBluetoothSco();
                    }
                    audioService.stop();
                    if(cpsPreview != null){
                        cpsPreview.interrupt();
                        cpsPreview = null;
                    }
                }
            });

            saveButton = (Button)findViewById(R.id.btnSaveId);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openMeasureActivity();
                    saveSettings();
                    audioService.stop();
                    if(cpsPreview != null){
                        cpsPreview.interrupt();
                        cpsPreview = null;
                    }
                }
            });

            btEnableSwitch = (Switch)findViewById(R.id.btEnableId);
            btEnableSwitch.setChecked(btEnabled);
            btEnableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    btEnabled = isChecked;
                    if (!isChecked){
                        am.stopBluetoothSco();
                        audioService.stop();
                        Log.d(TAG, "stop BT_SCO");
                    } else {
                        receiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                int state = intent.getIntExtra("android.media.extra.SCO_AUDIO_STATE", -1);
                                if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {

                                } else if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state){

                                }
                            }
                        };
                        registerReceiver(receiver, new IntentFilter("android.media.SCO_AUDIO_STATE_CHANGED"));
                    }
                }
            });

            cpsLabel = (TextView)findViewById(R.id.cpsLabelId);

            triggerLabel = (TextView)findViewById(R.id.triggerLabelId);
            triggerLevelBar = (SeekBar)findViewById(R.id.triggerLevelId);
            triggerLevelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                    triggerLevel = value;
                    triggerLabel.setText(TRIGGER_LEVEL_LABEL + value + TRIGGER_LEVEL_DIMENSION);
                    audioService.setTriggerLevel(value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            if(triggerLevel != null){
                triggerLevelBar.setProgress(triggerLevel);
            }
            int triggerLevelValue = triggerLevelBar.getProgress();
            triggerLabel.setText(TRIGGER_LEVEL_LABEL + triggerLevelValue + TRIGGER_LEVEL_DIMENSION);
            audioService.setTriggerLevel(triggerLevelValue);

            impulseWidthLabel = (TextView)findViewById(R.id.impulseWidthLabelId);
            impulseWidthBar = (SeekBar)findViewById(R.id.impulseWidthId);
            impulseWidthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                    impulseWidth = value;
                    impulseWidthLabel.setText(IMPULSE_WIDTH_LABEL + value + IMPULSE_WIDTH_DIMENSION);
                    audioService.setImpulseWidth(value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            if(impulseWidth != null){
                impulseWidthBar.setProgress(impulseWidth);
            }
            int impulseWidthValue = impulseWidthBar.getProgress();
            impulseWidthLabel.setText(IMPULSE_WIDTH_LABEL + impulseWidthValue + IMPULSE_WIDTH_DIMENSION);
            audioService.setImpulseWidth(impulseWidthValue);

            chart = (LineChartView)findViewById(R.id.chartId);

            aFactorView = (EditText)findViewById(R.id.factorAId);
            aFactorView.setText(factorA.toString());
            aFactorView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void afterTextChanged(Editable value) {
                    String stringValue = value.toString();
                    try{
                        factorA = Float.parseFloat(stringValue);
                    } catch (Exception e){
                        Log.e(TAG, "in afterTextChanged factorA:" + e);
                    }
                }
            });

            bFactorView = (EditText)findViewById(R.id.factorBId);
            bFactorView.setText(factorB.toString());
            bFactorView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void afterTextChanged(Editable value) {
                    String stringValue = value.toString();
                    try{
                        factorB = Float.parseFloat(stringValue);
                    } catch (Exception e){
                        Log.e(TAG, "in afterTextChanged factorB:" + e);
                    }
                }
            });

            cFactorView = (EditText)findViewById(R.id.factorCId);
            cFactorView.setText(factorC.toString());
            cFactorView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }
                @Override
                public void afterTextChanged(Editable value) {
                    String stringValue = value.toString();
                    try{
                        factorC = Float.parseFloat(stringValue);
                    } catch (Exception e){
                        Log.e(TAG, "in afterTextChanged factorC:" + e);
                    }
                }
            });

        } catch (Exception e){
            Log.e(TAG, "in initControls:" + e);
            throw e;
        }
    }

    private void saveSettings(){
        try{
            settingsMode = false;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTING_MENU_MODE, settingsMode);
            editor.putBoolean(SETTING_BT_ENABLE, btEnabled);
            editor.putInt(SETTING_TRIGGER_LEVEL, triggerLevel);
            editor.putInt(SETTING_IMPULSE_WIDTH, impulseWidth);
            editor.putFloat(SETTING_FACTOR_A, factorA);
            editor.putFloat(SETTING_FACTOR_B, factorB);
            editor.putFloat(SETTING_FACTOR_C, factorC);
            editor.commit();
        } catch (Exception e){
            Log.e(TAG, "in initControls:" + e);
            Toast.makeText(this, "Can't save settings:" + e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openMeasureActivity(){
        try{
            Intent intent = new Intent(this, MeasureActivity.class);
            startActivity(intent);
        } catch (Exception e){
            Log.e(TAG, "in openMeasureActivity:" + e);
        }
    }

    private void updateCps(){
        cpsPreview = new Thread(new Runnable() {
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String cpsValue = String.format("%.01f", audioService.getCps());
                        runOnUiThread(new Runnable() {
                            public void run() {
                                cpsLabel.setText(cpsValue);
                            }
                        });
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e){
                    // thread was interrupted
                } catch (Exception e) {
                    Log.e(TAG, "in updateCps");
                    e.printStackTrace();
                }
            }
        });
        cpsPreview.start();
    }
}
