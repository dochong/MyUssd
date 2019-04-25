package tz.co.nezatech.apps.myussd.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.opencsv.CSVReader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import tz.co.nezatech.apps.myussd.R;

public class USSDService extends AccessibilityService {
    public static final String PREFS_KEY_USSD_CODE = "UssdCode";
    public static final String PREFS_KEY_USSD_CONFIGURATIONS = "UssdConfigurations";

    private String ussdCode = "*888*01#";
    public static String TAG = USSDService.class.getSimpleName();

    private Map<String, String> smPath = new LinkedHashMap<>();
    private Map<String, String> screenCapture = new LinkedHashMap<>();
    private List<Map<String, String>> paths = new LinkedList<>();
    static final int USSD_TRIGGER_INTERVAL_MS = 1000 * 60 * 1;//1 minute

    private String prevKey = null;
    SharedPreferences prefs;

    void loadMenus() {
        try {
            paths.clear();
            Resources res = getResources();
            InputStream is = res.openRawResource(R.raw.menus);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            CSVReader reader = new CSVReader(bufferedReader);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                Log.d(TAG, "Row: " + Arrays.asList(nextLine));
                final Map<String, String> row = new LinkedHashMap<>();
                Arrays.asList(nextLine).forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        String[] split = s.split(":");
                        row.put(split[0], split[1]);
                    }
                });
                paths.add(row);
            }

            if (!paths.isEmpty()) {
                smPath = paths.get(random(0, paths.size()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading menus: " + e.getMessage());
        }
    }

    private int random(int from, int to) {
        Random r = new Random();
        return r.nextInt(to - from) + from;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent");

        AccessibilityNodeInfo source = event.getSource();
        /* if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !event.getClassName().equals("android.app.AlertDialog")) { // android.app.AlertDialog is the standard but not for all phones  */
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !String.valueOf(event.getClassName()).contains("AlertDialog")) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && (source == null || !source.getClassName().equals("android.widget.TextView"))) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && TextUtils.isEmpty(source.getText())) {
            return;
        }

        List<CharSequence> eventText;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            eventText = event.getText();
        } else {
            eventText = Collections.singletonList(source.getText());
        }

        String text = processUSSDText(eventText);

        if (TextUtils.isEmpty(text)) return;

        // Close dialog
        performGlobalAction(GLOBAL_ACTION_BACK); // This works on 4.1+ only

        Log.d(TAG, text);
        // Handle USSD response here
        AccessibilityNodeInfo nodeInfo = event.getSource();
        handleUssdResponse(nodeInfo, text);
    }

    private void handleUssdResponse(AccessibilityNodeInfo nodeInfo, String text) {
        String currKey = null;
        if (prevKey == null) {
            currKey = smPath.keySet().iterator().next();
        } else {
            Iterator<String> iterator = smPath.keySet().iterator();
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (next.equalsIgnoreCase(prevKey) && iterator.hasNext()) {
                    currKey = iterator.next();
                    break;
                }
            }
        }

        if (currKey == null) {
            Log.d(TAG, "No more menus");
            completed();
        } else {
            try {
                AccessibilityNodeInfo nodeInput = nodeInfo.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                Bundle bundle = new Bundle();
                CharSequence input = smPath.get(currKey);
                Log.d(TAG, "Input: " + input);
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input);
                nodeInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
                nodeInput.refresh();
            } catch (Exception e) {
                Log.e(TAG, "Exception: " + e);
            }

            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("Send");
            for (AccessibilityNodeInfo node : list) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            prevKey = currKey;
            screenCapture.put(currKey, text);
        }

    }

    private void completed() {
        Log.d(TAG, "ScreenCapture: " + screenCapture);
        prevKey = null;
        screenCapture.clear();
        if (!paths.isEmpty()) {
            smPath = paths.get(random(0, paths.size()));
        }
    }

    private String processUSSDText(List<CharSequence> eventText) {
        for (CharSequence s : eventText) {
            String text = String.valueOf(s);
            return text;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "OnUnbind");
        try {
            handler.removeCallbacks(runnableCode);
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        try {
            handler.removeCallbacks(runnableCode);
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
        super.onDestroy();
    }

    final Handler handler = new Handler();
    Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            String ussdCode = prefs.getString(USSDService.PREFS_KEY_USSD_CODE, "*888*01#");

            Log.d(TAG, "Dialing Ussd code: " + ussdCode);
            dialUssd(ussdCode);
            handler.postDelayed(this, USSD_TRIGGER_INTERVAL_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(getString(R.string.config_settings), Context.MODE_PRIVATE);
        Log.d(TAG, "onServiceConnected");
        AccessibilityServiceInfo info;
        info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.packageNames = new String[]{"com.android.phone"};
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);

        //Load menus
        loadMenus();

        //start periodic Ussd trigger
        handler.postDelayed(runnableCode, USSD_TRIGGER_INTERVAL_MS);
    }

    private void dialUssd(String code) {
        Intent callIntent = new Intent(Intent.ACTION_CALL, ussdToCallableUri(code));
        startActivity(callIntent);
    }

    private Uri ussdToCallableUri(String ussd) {
        String uriString = "";
        if (!ussd.startsWith("tel:")) {
            uriString += "tel:";
        }
        for (char c : ussd.toCharArray()) {
            if (c == '#') {
                uriString += Uri.encode("#");
            } else {
                uriString += c;
            }
        }
        return Uri.parse(uriString);
    }
}