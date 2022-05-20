package com.nick.atomsense;

import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import lecho.lib.hellocharts.model.*;
import lecho.lib.hellocharts.view.LineChartView;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioService implements Serializable {

    private final String TAG = "AudioService";
    private AudioRecord audioRecord;
    private int internalBufferSize;
    private boolean isRun = false;
    private short triggerLevel = 0;
    private int impulseWidthInSamples = 0;
    private int sampleRate = 8000;
    // 1000 ms = 1 s
    private float timeResolution = 1000 / sampleRate;
    // this is field represents how many samples contains in 1 ms
    private int timeResolutionInSamples = sampleRate / 1000;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float cps = 0f;
    private ConcurrentLinkedQueue<Long> impulseData = new ConcurrentLinkedQueue();

    public void init() {
        try{
            if (audioRecord == null){
                int minInternalBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                        channelConfig, audioFormat);
                internalBufferSize = minInternalBufferSize * 4;

                audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        sampleRate, channelConfig, audioFormat, internalBufferSize);
            }
            if (audioRecord == null){
                Log.e(TAG, "init(): audioRecord is null");
                return;
            }
        } catch (Exception e){
            Log.e(TAG, "in startRecord()");
            e.printStackTrace();
        }

    }

    private void startRecording(){
        if (audioRecord != null && audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING){
            audioRecord.startRecording();
            isRun = true;
        }
    }

    public void runSettingsMode(LineChartView chart) throws Exception{
        Line impulseWaveLine = new Line(new LinkedList<>()).setColor(Color.BLACK).setCubic(false);
        Line levelTriggerLine = new Line(new LinkedList<>()).setColor(Color.RED).setCubic(false);
        LineChartData linesData = new LineChartData();

        chart.setInteractive(true);

        impulseWaveLine.setPointRadius(0);
        impulseWaveLine.setStrokeWidth(1);

        levelTriggerLine.setPointRadius(0);
        levelTriggerLine.setStrokeWidth(1);

        List<Line> lines = new LinkedList();
        lines.add(impulseWaveLine);
        lines.add(levelTriggerLine);
        linesData.setLines(lines);
        impulseWaveLine.setHasLabels(true);

        new Thread(new Runnable() {
            public void run() {
                try {
                    startRecording();
                    short[] audioData = new short[internalBufferSize];
                    List<Long> cpsRate = new LinkedList();
                    while(isRun){
                        audioRecord.read(audioData, 0, internalBufferSize);
                        for(int i = 0; i < audioData.length; i++) {
                            short audioLevel = audioData[i];
                            if(audioLevel > triggerLevel){
                                List<PointValue> values = new LinkedList();
                                List<PointValue> trigger = new LinkedList();
                                // extract impulse audiodata
                                // with impulseWidthInSamples offset from left and right side
                                int impulseStartIndex = i - impulseWidthInSamples;
                                if(impulseStartIndex < 0){
                                    impulseStartIndex = 0;
                                }
                                int impulseEndIndex =
                                        (impulseStartIndex + impulseWidthInSamples * 2);
                                if(impulseEndIndex > audioData.length){
                                    impulseEndIndex = audioData.length;
                                }
                                int xAxisCounter = 0;
                                for(int k = impulseStartIndex; k < impulseEndIndex; k++){

                                    PointValue p = new PointValue(xAxisCounter, audioData[k]);
                                    p.setLabel("");
                                    values.add(p);
                                    PointValue t = new PointValue(xAxisCounter, triggerLevel);
                                    t.setLabel("");
                                    trigger.add(t);

                                    xAxisCounter ++;
                                }
                                impulseWaveLine.setValues(values);
                                levelTriggerLine.setValues(trigger);
                                chart.setLineChartData(linesData); // update the chart view

                                cpsRate.add(System.currentTimeMillis());
                            }
                            i = i + impulseWidthInSamples;
                            if (i > audioData.length){
                                break;
                            }
                        }
                        int totalCounts = cpsRate.size();
                        if (totalCounts > 2){
                            float remainMilSeconds = (float) (cpsRate.get(totalCounts - 1) - cpsRate.get(0));
                            //remainMilSeconds
                            float remainSeconds = remainMilSeconds / 1000;
                            cps = totalCounts / remainSeconds;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "in readTheData", e);
                }
            }
        }).start();
    }

    public void runMeasureMode(){
        new Thread(new Runnable() {
            public void run() {
                try {
                    startRecording();
                    short[] audioData = new short[internalBufferSize];
                    int impulseWidthOffset = 0;
                    while(isRun){
                        audioRecord.read(audioData, 0, internalBufferSize);
                        // scip whole portion of samples if impulse width too long
                        if(impulseWidthOffset >= audioData.length){
                            impulseWidthOffset = impulseWidthOffset - audioData.length;
                            continue;
                        }
                        for(int i = 0; i < audioData.length;) {
                            if(impulseWidthOffset > 0){
                                i = impulseWidthOffset;
                                impulseWidthOffset = 0;
                            }
                            short audioLevel = audioData[i];
                            i++;
                            if(audioLevel > triggerLevel){
                                impulseData.add(System.currentTimeMillis());
                                // skip other samples by length of impulse width
                                // impulseWidthOffset it is a rest part of the offset
                                i = i + impulseWidthInSamples;
                                impulseWidthOffset = i - audioData.length;
                                if(impulseWidthOffset > 0){
                                    break;
                                } else {
                                    impulseWidthOffset = 0;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "in readTheData", e);
                }
            }
        }).start();
    }

    public void stop(){
        isRun = false;
        if (audioRecord != null){
            audioRecord.stop();
        }
    }

    public void setTriggerLevel(int level){
        triggerLevel = (short) ((Short.MAX_VALUE * level) / 100);
    }

    public void setImpulseWidth(Integer width) {
        // width is
        impulseWidthInSamples = width * timeResolutionInSamples;
    }

    public float getCps(){
        return cps;
    }

    public LinkedList<Long> getImpulseData(){
        return new LinkedList(impulseData);
    }

    public void clearImpulseData(){
        impulseData.clear();
    }
}
