package com.nick.atomsense;

import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class MeasureActivity extends AppCompatActivity {

    private static final String TAG = "MeasureActivity";
    private Thread measuringThread;
    private SharedPreferences preferences;
    private AudioService audioService = new AudioService();
    private AudioManager am;
    private BroadcastReceiver receiver;

    private boolean btEnabled = false;

    private Button menuBtn;
    private TextView rateLabel;
    private TextView rateLabelDimension;
    private TextView errorLabel;
    private Button restartBtn;

    private Integer triggerLevel;
    private Integer impulseWidth;

    private Float factorA;
    private Float factorB;
    private Float factorC;

    private static String DIMENSION_CPS = "CPS";
    private static String DIMENSION_CPM = "CPM";
    private static String DIMENSION_ML_R = "µR/h";

    private List<String> dimensionStack = new ArrayList();
    private String currentDimension;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try{
            setContentView(R.layout.activity_measure);

            // init preferences
            preferences = PreferenceManager.getDefaultSharedPreferences(this);

            btEnabled = preferences.getBoolean(SettingsActivity.SETTING_BT_ENABLE, false);

            factorA = preferences.getFloat(SettingsActivity.SETTING_FACTOR_A, 0);
            factorB = preferences.getFloat(SettingsActivity.SETTING_FACTOR_B, 0);
            factorC = preferences.getFloat(SettingsActivity.SETTING_FACTOR_C, 0);

            if (factorA == 0 && factorB == 0 && factorC == 0){
                dimensionStack.add(DIMENSION_CPM);
                dimensionStack.add(DIMENSION_CPS);

                currentDimension = DIMENSION_CPS;
            } else {
                dimensionStack.add(DIMENSION_CPS);
                dimensionStack.add(DIMENSION_CPM);
                dimensionStack.add(DIMENSION_ML_R);

                currentDimension = DIMENSION_ML_R;
            }

            triggerLevel = preferences.getInt(SettingsActivity.SETTING_TRIGGER_LEVEL, -1);
            triggerLevel = triggerLevel == -1 ? null : triggerLevel;
            impulseWidth = preferences.getInt(SettingsActivity.SETTING_IMPULSE_WIDTH, -1);
            impulseWidth = impulseWidth == -1 ? null : impulseWidth;

            // init audio interface
            am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioService.init();
            audioService.setTriggerLevel(triggerLevel);
            audioService.setImpulseWidth(impulseWidth);

            if(btEnabled){
                receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {

                    }
                };
                registerReceiver(receiver, new IntentFilter("android.media.SCO_AUDIO_STATE_CHANGED"));
            }

            initControls();
            startMeasure();
        } catch (Exception e){
            Log.e(TAG, "in onCreate:" + e);
            throw e;
        }
    }

    @Override
    public void onBackPressed(){

    }

    @Override
    protected void onStop() {
        super.onStop();
        try{
            audioService.stop();
            if(am.isBluetoothScoOn()){
                am.stopBluetoothSco();
            }
            if(measuringThread != null){
                measuringThread.interrupt();
                measuringThread = null;
            }
        } catch (Exception e){
            Log.e(TAG, "in onStop:" + e);
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
        try{
            menuBtn = (Button)findViewById(R.id.btnMenuId);
            menuBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean(SettingsActivity.SETTING_MENU_MODE, true);
                    editor.commit();
                    MeasureActivity.super.onBackPressed();
                }
            });

            rateLabel = (TextView)findViewById(R.id.rateLabelId);

            rateLabelDimension = (TextView)findViewById(R.id.rateLabelDimensionId);
            rateLabelDimension.setText(currentDimension);
            rateLabelDimension.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentDimension = dimensionStack.remove(0);
                    dimensionStack.add(currentDimension);
                    rateLabelDimension.setText(currentDimension);
                }
            });

            errorLabel = (TextView)findViewById(R.id. errorLabelId);

            restartBtn = (Button)findViewById(R.id.btnRestartId);
            restartBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view){
                    audioService.clearImpulseData();
                }
            });

        } catch (Exception e){
            Log.e(TAG, "in initControls:" + e);
        }
    }

    private void startMeasure(){
        measuringThread = new Thread(new Runnable() {
            public void run() {
                try {
                    if(btEnabled){
                        am.startBluetoothSco();
                    }
                    audioService.runMeasureMode();
                    while(!Thread.currentThread().isInterrupted()) {
                        List<Long> impulseData = audioService.getImpulseData();
                        if (impulseData.size() < 2) {
                            // avoid error in bellow in case if too low counts
                            impulseData.add(1l);
                            impulseData.add(2l);
                        }

                        Integer totalCounts = impulseData.size();
                        double errVal = (1.96 / Math.sqrt(totalCounts)) * 100;
                        String error = errVal >= 100 ? "99" : String.format("%.1f", errVal);

                        String rate = "--";
                        float remainMilSeconds = (float) (impulseData.get(totalCounts - 1) - impulseData.get(0));
                        //remainMilSeconds
                        float remainSeconds = remainMilSeconds / 1000;
                        float cps = totalCounts / remainSeconds;

                        if(cps > 1999) {
                            rate = "∞";
                        } else if (DIMENSION_ML_R.equals(currentDimension)){
                            double mkR = factorA * Math.pow(cps, 2) + factorB * cps + factorC;
                            rate = String.format("%.2f", mkR);
                        } else if (DIMENSION_CPS.equals(currentDimension)){
                            rate = String.format("%.2f", cps);
                        } else if (DIMENSION_CPM.equals(currentDimension)){
                            rate = String.format("%.1f", (cps * 60));
                        }

                        String rateText = rate;
                        String errText = error;

                        runOnUiThread(new Runnable() {
                            public void run() {
                                rateLabel.setText(rateText);
                                errorLabel.setText(errText);
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
        measuringThread.start();
    }



}
