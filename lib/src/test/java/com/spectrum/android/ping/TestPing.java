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

import android.system.ErrnoException;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TestPing {
    @Test
    public void testPing() {
        final MockPingListener listener = new MockPingListener();
        final MockPing mockPing = new MockPing(null, listener, 3);
        mockPing.setCount(6);
        mockPing.run();
        Assert.assertEquals(mockPing.getCount(), listener.pingCount);
        Assert.assertEquals(mockPing.timeouts.length, listener.timeoutCount);
    }

    @Test
    public void testPingOptions() {
        final MockPingListener listener = new MockPingListener();
        final MockPing mockPing = new MockPing(null, listener, 3);
        mockPing.run();
        try {
            mockPing.setTimeoutMs(-1);
            Assert.fail();
        } catch (IllegalArgumentException e) {

        }
        mockPing.setTimeoutMs(999);
        Assert.assertEquals(999, mockPing.getTimeoutMs());

        //TODO: Improve this
        mockPing.setNetwork(null);
        Assert.assertNull(mockPing.getNetwork());

        mockPing.setDelayMs(888);
        Assert.assertEquals(888, mockPing.getDelayMs());
    }

    @Test
    public void testListenerRequired() {

        try {
            new MockPing(null, null);
            Assert.fail();
        } catch (NullPointerException e) {

        }
    }


    @Test
    public void testCreateSocketFailure() {
        final MockPingListener listener = new MockPingListener();
        final ErrnoException fail = new ErrnoException("socket()", 1);
        new MockPing(null, listener) {
            @Override
            protected FileDescriptor socket(final int inet, final int proto) throws ErrnoException {
                throw fail;
            }
        }.run();
        Assert.assertEquals(fail, listener.exception);
    }


    @Test
    public void testInvalidFd() {
        final MockPingListener listener = new MockPingListener();
        new MockPing(null, listener) {
            @Override
            protected FileDescriptor socket(final int inet, final int proto) {
                try (final FileInputStream in = new FileInputStream(File.createTempFile("testfd", ".bin"))){
                    return in.getFD();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }.run();
        Assert.assertTrue(listener.exception instanceof IOException);
    }

    @Test
    public void testErrnoException() {
        final MockPingListener listener = new MockPingListener();
        final ErrnoException fail = new ErrnoException("sendto()", 1);
        new MockPing(null, listener) {
            @Override
            protected int sendto(final FileDescriptor fd, final ByteBuffer byteBuffer) throws ErrnoException {
                throw fail;
            }
        }.run();
        Assert.assertEquals(fail, listener.exception);
    }


    class MockPingListener implements Ping.PingListener {
        int pingCount;
        int timeoutCount;

        Exception exception;

        @Override
        public void onPing(final long timeMs, final int count) {
            pingCount++;
            if (timeMs == Ping.TIMED_OUT_MS) {
                timeoutCount++;
            }
        }

        @Override
        public void onPingException(final Exception e, final int count) {
            exception = e;
        }
    }


}
