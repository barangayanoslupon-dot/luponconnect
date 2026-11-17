package com.adasoft.jeg;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JEG's ears and voice on Android.
 * - Listens all the time (restarts itself), sends "result" {text, final} and "rms" {rms} (loudness).
 * - Speaks with Android's text-to-speech and tells JS when it is done.
 * Nothing is recorded or saved.
 */
@CapacitorPlugin(
    name = "JegSpeech",
    permissions = { @Permission(alias = "mic", strings = { Manifest.permission.RECORD_AUDIO }) }
)
public class JegSpeechPlugin extends Plugin {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable listenRunnable = this::listen;
    private SpeechRecognizer recognizer;
    private boolean running = false;
    private boolean beepMuted = false;
    private String language = "en-US";
    private long lastRms = 0;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Map<String, PluginCall> pending = new HashMap<>();
    private int utterId = 0;

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) return;
            tts.setLanguage(Locale.US);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { finish(id); }
                @Override public void onError(String id) { finish(id); }
                @Override public void onStop(String id, boolean interrupted) { finish(id); }
            });
        });
    }

    // ---------------- listening ----------------

    @PluginMethod
    public void start(PluginCall call) {
        language = call.getString("language", "en-US");
        if (getPermissionState("mic") != PermissionState.GRANTED) {
            requestPermissionForAlias("mic", call, "micPermission");
            return;
        }
        begin();
        call.resolve();
    }

    @PermissionCallback
    private void micPermission(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) {
            begin();
            call.resolve();
        } else {
            call.reject("Microphone permission denied");
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        running = false;
        main.post(this::shutdownRecognizer);
        call.resolve();
    }

    private void begin() {
        if (running) return;
        running = true;
        main.post(listenRunnable);
    }

    private void listen() {
        if (!running) return;
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            JSObject o = new JSObject();
            o.put("error", "not_available");
            notifyListeners("error", o);
            running = false;
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(listener);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getContext().getPackageName());
        muteBeep(true);
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            restart(800);
        }
    }

    private void restart(long delayMs) {
        main.removeCallbacks(listenRunnable);
        if (running) main.postDelayed(listenRunnable, delayMs);
    }

    private void destroyRecognizerOnly() {
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
    }

    private void shutdownRecognizer() {
        main.removeCallbacks(listenRunnable);
        destroyRecognizerOnly();
        muteBeep(false);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onRmsChanged(float rms) {
            long now = System.currentTimeMillis();
            if (now - lastRms < 100) return;
            lastRms = now;
            JSObject o = new JSObject();
            o.put("rms", rms);
            notifyListeners("rms", o);
        }

        @Override
        public void onError(int error) {
            if (!running) return;
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizerOnly();
                restart(600);
            } else {
                restart(250); // no match / silence timeout: just listen again
            }
        }

        @Override
        public void onResults(Bundle results) {
            send(results, true);
            restart(150);
        }

        @Override
        public void onPartialResults(Bundle partial) {
            send(partial, false);
        }
    };

    private void send(Bundle b, boolean isFinal) {
        if (b == null) return;
        ArrayList<String> m = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (m == null || m.isEmpty() || m.get(0) == null || m.get(0).isEmpty()) return;
        JSObject o = new JSObject();
        o.put("text", m.get(0));
        o.put("final", isFinal);
        notifyListeners("result", o);
    }

    /** Hides the "ding" Android plays every time listening restarts. */
    private void muteBeep(boolean mute) {
        if (beepMuted == mute) return;
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int dir = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, dir, 0);
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, dir, 0);
            beepMuted = mute;
        } catch (Exception ignored) { }
    }

    // ---------------- speaking ----------------

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        if (!ttsReady || text == null || text.isEmpty()) {
            call.resolve();
            return;
        }
        // a new line replaces the one being spoken
        synchronized (pending) {
            for (PluginCall c : pending.values()) c.resolve();
            pending.clear();
        }
        tts.setPitch(call.getFloat("pitch", 1.4f));
        tts.setSpeechRate(call.getFloat("rate", 1.0f));
        tts.setLanguage(Locale.forLanguageTag(call.getString("language", "en-US")));
        String id = "jeg" + (++utterId);
        synchronized (pending) { pending.put(id, call); }
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id);
    }

    @PluginMethod
    public void stopSpeaking(PluginCall call) {
        if (tts != null) tts.stop();
        synchronized (pending) {
            for (PluginCall c : pending.values()) c.resolve();
            pending.clear();
        }
        call.resolve();
    }

    private void finish(String id) {
        PluginCall c;
        synchronized (pending) { c = pending.remove(id); }
        if (c != null) c.resolve();
    }

    // ---------------- app lifecycle ----------------

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        main.post(this::shutdownRecognizer); // keeps "running" so it comes back on resume
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        if (running) restart(300);
    }

    @Override
    protected void handleOnDestroy() {
        running = false;
        shutdownRecognizer();
        if (tts != null) tts.shutdown();
        super.handleOnDestroy();
    }
}
