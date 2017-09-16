package com.codepowered.phonedatamove;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

public class FirstActivity extends AppCompatActivity implements View.OnTouchListener {

    private Button btnIAmSender;
    private Button btnIAmReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        (btnIAmSender = (Button) findViewById(R.id.btnIAmSender)).setOnTouchListener(this);
        (btnIAmReceiver = (Button) findViewById(R.id.btnIAmReceiver)).setOnTouchListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_SMS},
                    0);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    finish();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view == btnIAmReceiver)
            receiver();
        else if (view == btnIAmSender)
            sender();
        return false;
    }

    private void sender() {
        finish();
        startActivity(new Intent(this, ConnectActivity.class));
    }

    private void receiver() {
        finish();
        startActivity(new Intent(this, ServerActivity.class));
    }
}
