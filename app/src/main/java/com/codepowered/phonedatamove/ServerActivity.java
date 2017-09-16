package com.codepowered.phonedatamove;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Telephony;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class ServerActivity extends AppCompatActivity {

    private String ip;
    private EditText txtURL;
    private static final String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private String password;
    private int port;
    private URL url;
    private NanoHTTPD nanoHTTPD;
    private TextView txtRequest;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        ip = Utils.getIPAddress(true);
        Random rand = new Random();
        port = rand.nextInt(65536);
        password = "";
        for (int i = 0; i < 10; i++) {
            int x = rand.nextInt(s.length());
            password += s.substring(x, x + 1);
        }
        try {
            url = new URL("http", ip, port, password);
        } catch (MalformedURLException e) {
            Log.wtf("ServerActivity", e);
        }
        (txtURL = (EditText) findViewById(R.id.txtURL)).setText(url.toString());
        txtRequest = (TextView) findViewById(R.id.txtRequest);
    }

    static String convertStreamToString(java.io.InputStream is, int len) throws IOException {
        byte[] bytes = new byte[len];
        is.read(bytes);
        return new String(bytes, Charset.forName("UTF8"));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!Telephony.Sms.getDefaultSmsPackage(this).equals(getPackageName())) {
            // App is not default.
            // Show the "not currently set as the default SMS app" interface

            // Set up a button that allows the user to change the default SMS app
            Intent intent =
                    new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                    getPackageName());
            finish();
            startActivity(intent);

        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            nanoHTTPD = new NanoHTTPD(port) {
                @Override
                public Response serve(IHTTPSession session) {
                    if (!("/" + password).equals(session.getUri()))
                        return super.serve(session);
                    String theString = null;
                    try {
                        theString = convertStreamToString(session.getInputStream(), Integer.parseInt(session.getHeaders().get("content-length"))).trim();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    final JSONObject jsonObject;
                    try {
                        jsonObject = theString.length() > 0 ? new JSONObject(theString) : null;
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            requested(jsonObject);
                        }
                    });
                    return newFixedLengthResponse(theString);
                }
            };
            nanoHTTPD.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class X {
        private final String uri;
        String phoneNumber;
        String message;
        int readState;
        long time;
        long date_sent;

        X(String phoneNumber, String message, int readState, long time, long date_sent, String uri) {
            this.uri = uri;
            this.phoneNumber = phoneNumber;
            this.message = message;
            this.readState = readState;
            this.time = time;
            this.date_sent = date_sent;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            X x = (X) obj;
            return uri.equals(x.uri) && phoneNumber.equals(x.phoneNumber) && message.equals(x.message) && readState == x.readState && time == x.time && date_sent == x.date_sent;
        }
    }

    private synchronized void requested(final JSONObject jsonObject) {
        txtRequest.setText(jsonObject == null ? "" : jsonObject.toString());
        if (jsonObject != null) {


            new AsyncTask<JSONObject, Void, String>() {
                @Override
                protected String doInBackground(JSONObject... jsonObjects) {

                    final List<X> xs1 = new ArrayList<>();

                    for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                        String uri = it.next();
                        final List<X> xs = new ArrayList<>();

                        if (uri.equals(Telephony.Sms.Sent.CONTENT_URI.toString()) || uri.equals(Telephony.Sms.Inbox.CONTENT_URI.toString())) {
                            JSONArray jsonArray = null;
                            try {
                                jsonArray = (JSONArray) jsonObject.get(uri);
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject jo = (JSONObject) jsonArray.get(i);
                                    String address = jo.getString("address");
                                    String body = jo.getString("body");
                                    int read = jo.getInt("read");
                                    long date = jo.getLong("date");
                                    long date_sent = jo.getLong("date_sent");
                                    xs.add(new X(address, body, read, date, date_sent, uri));
                                }
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }

                        }

                        Cursor cursor = getContentResolver().query(Uri.parse(uri), null, null, null, null);
                        try {
                            prune(xs, cursor, uri);
                        } finally {
                            cursor.close();
                        }

                        xs1.addAll(xs);
                    }

                    for (X x : xs1) {
                        saveSms(x, x.uri);
                    }
                    return "";
                }
            }.execute(jsonObject);
        }
    }

    private List<X> prune(List<X> xs, Cursor cursor, String uri) {

        List<X> list = new ArrayList<>();

        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                Map<String, Object> msgData = new HashMap<>();
                for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
                    int typeId = cursor.getType(idx);
                    Object o = null;
                    switch (typeId) {
                        case Cursor.FIELD_TYPE_BLOB:
                            Log.i("ConnectActivity", cursor.getColumnName(idx) + ": FIELD_TYPE_BLOB");
                            o = cursor.getBlob(idx);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            Log.i("ConnectActivity", cursor.getColumnName(idx) + ": FIELD_TYPE_FLOAT");
                            o = cursor.getFloat(idx);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            Log.i("ConnectActivity", cursor.getColumnName(idx) + ": FIELD_TYPE_INTEGER");
                            o = cursor.getLong(idx);
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            Log.i("ConnectActivity", cursor.getColumnName(idx) + ": FIELD_TYPE_NULL");
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            Log.i("ConnectActivity", cursor.getColumnName(idx) + ": FIELD_TYPE_STRING");
                            o = cursor.getString(idx);
                            break;
                        default:
                            throw new IllegalArgumentException("" + typeId);
                    }
                    msgData.put(cursor.getColumnName(idx), o);
                }
                // use msgData
                String address = (String) msgData.get("address");
                String body = (String) msgData.get("body");
                int read = (int) (long) msgData.get("read");
                long date = (long) msgData.get("date");
                long date_sent = (long) msgData.get("date_sent");

                list.add(new X(address, body, read, date, date_sent, uri));
            } while (cursor.moveToNext());
        } else {
            // empty box, no SMS
        }

        xs.removeAll(list);

        return xs;
    }

    public boolean saveSms(X x, String uri) {
        boolean ret = false;
        try {
            ContentValues values = new ContentValues();
            values.put("address", x.phoneNumber);
            values.put("body", x.message);
            values.put("read", x.readState); //"0" for have not read sms and "1" for have read sms
            values.put("date", x.time);
            values.put("date_sent", x.date_sent);

            getContentResolver().insert(Uri.parse(uri), values);

            ret = true;
        } catch (Exception ex) {
            ex.printStackTrace();
            ret = false;
        }
        return ret;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nanoHTTPD.stop();
    }
}
