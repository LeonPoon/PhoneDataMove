package com.codepowered.phonedatamove;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonWriter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectActivity extends AppCompatActivity implements View.OnTouchListener {

    private Button btnConnect;
    private EditText txtConnectTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        (btnConnect = (Button) findViewById(R.id.btnConnect)).setOnTouchListener(this);
        txtConnectTo = (EditText) findViewById(R.id.txtConnectTo);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.i("ConnectActivity", "onTouch");
        final ProgressDialog d = new ProgressDialog(this);
        d.setTitle(getString(R.string.connecting));
        d.setCancelable(false);
        d.show();
        String text = txtConnectTo.getText().toString();
        new AsyncTask<String, String, Object>() {
            boolean ran = false;

            @Override
            protected Object doInBackground(String... strings) {
                try {
                    return doInBackgroundSafe(strings[0]);
                } catch (Exception e) {
                    Log.wtf("ConnectActivity", e);
                    return e;
                }
            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
            }

            @Override
            protected void onPostExecute(Object obj) {
                if (ran) // for some reason we get called more than once
                    return;
                ran = true;
                Log.i("ConnectActivity", "onPostExecute");
                d.dismiss();
                btnConnect.setText(obj.toString());
            }
        }.execute(text);
        return false;
    }

    private String doInBackgroundSafe(String uriText) throws IOException, JSONException {
        URL url = new URL(uriText);
        if (!"http".equals(url.getProtocol().toLowerCase()))
            throw new IllegalArgumentException("need http");
        String json = doInBackgroundSafe(url);


        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(true);

        OutputStream os = urlConnection.getOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(os);
        OutputStreamWriter osw = new OutputStreamWriter(out, "UTF-8");
        BufferedWriter writer = new BufferedWriter(osw);
        writer.write(json);
        writer.flush();
        osw.flush();
        out.flush();
        os.flush();
        os.close();

        urlConnection.connect();
        return Integer.toString(urlConnection.getResponseCode());
    }

    private String doInBackgroundSafe(URL url) throws JSONException {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        doInBackgroundSafe(url, Telephony.Sms.Inbox.CONTENT_URI, map);
        doInBackgroundSafe(url, Telephony.Sms.Sent.CONTENT_URI, map);
        JSONObject jsonObject = new JSONObject();

        for (Map.Entry<String, List<Map<String, Object>>> stringListEntry : map.entrySet()) {

            List<JSONObject> oL = new ArrayList<>();
            for (Map<String, Object> stringStringMap : stringListEntry.getValue()) {
                JSONObject jsonObject1 = new JSONObject();
                for (Map.Entry<String, Object> stringStringEntry : stringStringMap.entrySet()) {
                    jsonObject1.put(stringStringEntry.getKey(), stringStringEntry.getValue());
                }
                oL.add(jsonObject1);
            }

            JSONArray jsonArray = new JSONArray(oL);
            jsonObject.put(stringListEntry.getKey(), jsonArray);

        }

        return jsonObject.toString();
    }


    private void doInBackgroundSafe(URL url, Uri contentUri, Map<String, List<Map<String, Object>>> map) {
        Cursor cursor = getContentResolver().query(contentUri, null, null, null, null);
        try {
            doInBackgroundSafe(url, contentUri, map, cursor);
        } finally {
            cursor.close();
        }
    }


    private void doInBackgroundSafe(URL url, Uri contentUri, Map<String, List<Map<String, Object>>> map, Cursor cursor) {

        List<Map<String, Object>> list = new ArrayList<>();

        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                Map<String, Object> msgData = new HashMap<>();
                for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
                    int typeId = cursor.getType(idx);
                    Object o = null;
                    switch (typeId) {
                        case Cursor.FIELD_TYPE_BLOB:
                            o = cursor.getBlob(idx);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            o = cursor.getFloat(idx);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            o = cursor.getLong(idx);
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            o = cursor.getString(idx);
                            break;
                        default:
                            throw new IllegalArgumentException("" + typeId);
                    }
                    msgData.put(cursor.getColumnName(idx), o);
                }
                // use msgData
                list.add(msgData);
            } while (cursor.moveToNext());
        } else {
            // empty box, no SMS
        }

        map.put(contentUri.toString(), list);
    }
}
