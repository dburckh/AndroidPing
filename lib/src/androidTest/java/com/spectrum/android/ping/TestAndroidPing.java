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

import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import androidx.test.filters.SmallTest;


/**
 * Test ping functionality
 * May require being on a cell connection if you don't have an IPv6 IP on your WiFi.
 */
@SmallTest
public class TestAndroidPing {
    private static final String HOST = "google.com";

    private void testPingInetAddress(final InetAddress inetAddress) {
        final TestPingListener pingListener = new TestPingListener();
        final Ping ping = new Ping(inetAddress, pingListener);
        ping.run();
        if (pingListener.failures > Ping.DEFAULT_COUNT / 2) {
            throw new RuntimeException("Too Many Failures: " + pingListener.failures);
        }

    }

    private static <T extends InetAddress> T getInetAddress(final String host, final Class<T> c) throws UnknownHostException {
        final InetAddress[] inetAddresses = InetAddress.getAllByName(host);

        for (final InetAddress inetAddress : inetAddresses) {
            if (inetAddress.getClass() == c) {
                return (T)inetAddress;
            }
        }
        return null;
    }

    @Test
    public void testPingV6() throws IOException {
        final Inet6Address inet6Address = getInetAddress(HOST, Inet6Address.class);
        if (inet6Address == null) {
            throw new IOException("No IPV6 address found for " + HOST);
        }
        testPingInetAddress(inet6Address);
    }

    @Test
    public void testPingV4() throws IOException {
        final Inet4Address inet4Address = getInetAddress(HOST, Inet4Address.class);
        if (inet4Address == null) {
            throw new IOException("No IPV4 address found for " + HOST);
        }
        testPingInetAddress(inet4Address);
    }


    static class TestPingListener implements Ping.PingListener {
        int failures;

        @Override
        public void onPing(long timeMs, int count) {
            if (timeMs == Ping.TIMED_OUT_MS) {
                failures++;
            }
        }

        @Override
        public void onPingException(Exception e, int count) {
            throw new RuntimeException(e);
        }
    }
}
