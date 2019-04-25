package tz.co.nezatech.apps.myussd;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import tz.co.nezatech.apps.myussd.service.USSDService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PHONE_CALL = 100;
    private final String TAG = MainActivity.class.getSimpleName();

    TextInputEditText code;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(this, USSDService.class));

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        code = findViewById(R.id.ussdCode);

        prefs = getSharedPreferences(getApplicationContext().getString(R.string.config_settings), Context.MODE_PRIVATE);
        String val = prefs.getString(USSDService.PREFS_KEY_USSD_CODE, "*888*01#");
        code.setText(val);
    }

    void save() {
        SharedPreferences prefs = getSharedPreferences(getApplicationContext().getString(R.string.config_settings), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(USSDService.PREFS_KEY_USSD_CODE, code.getText().toString());
        editor.commit();
        Log.d(TAG, "Configurations saved!");
    }

}
