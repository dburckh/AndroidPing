/*
 * Copyright (C) 2019 Charter Communications
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spectrum.android.ping;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class MainActivity extends Activity {
    private TextView mLog;

    public static Network getNetwork(final Context context, final int transport) {
        final ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        for (Network network : connManager.getAllNetworks()) {
            NetworkCapabilities networkCapabilities = connManager.getNetworkCapabilities(network);
            if (networkCapabilities != null &&
                    networkCapabilities.hasTransport(transport) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return network;
            }
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLog = findViewById(R.id.log);
        final EditText address = findViewById(R.id.address);
        final CheckBox wifi = findViewById(R.id.wifi);

        findViewById(R.id.ping).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                AsyncTask.SERIAL_EXECUTOR.execute(new PingRunnable(address.getText().toString(), wifi.isChecked()));
            }
        });

    }


    class PingRunnable implements Runnable {
        final private StringBuilder mSb = new StringBuilder();
        final private String mHost;
        final private boolean mWifi;
        final private Runnable textSetter = new Runnable() {
            @Override
            public void run() {
                mLog.setText(mSb.toString());
            }
        };

        public PingRunnable(final String host, final boolean wifi) {
            mHost = host;
            mWifi = wifi;
        }
        public void run() {
            try {
                final InetAddress dest = InetAddress.getByName(mHost);
                final Ping ping = new Ping(dest, new Ping.PingListener() {
                    @Override
                    public void onPing(final long timeMs, final int count) {
                        appendMessage("#" + count + " ms: " + timeMs + " ip: " + dest.getHostAddress(), null);
                    }

                    @Override
                    public void onPingException(final Exception e, final int count) {
                        appendMessage("#" + count  + " ip: " + dest.getHostAddress(), e);
                    }

                });

                if (mWifi) {
                    final Network network = getNetwork(getApplicationContext(), NetworkCapabilities.TRANSPORT_WIFI);
                    if (network == null) {
                        throw new UnknownHostException("Failed to find a WiFi Network");
                    }
                    ping.setNetwork(network);
                }
                ping.run();
            } catch(UnknownHostException e) {

                appendMessage("Unknown host", e);
            }
        }

        private void appendMessage(final String message, final Exception e) {
            Log.d("Ping", message, e);
            mSb.append(message);
            if (e != null) {
                mSb.append(" Error: ");
                mSb.append(e.getMessage());
            }
            mSb.append('\n');
            runOnUiThread(textSetter);
        }
    }
}
