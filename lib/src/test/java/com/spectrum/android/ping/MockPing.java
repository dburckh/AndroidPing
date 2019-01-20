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
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Happy day ping impl.
 */
public class MockPing extends Ping {
    private int mCount;
    private byte[] mBuffer;

    final int timeouts[];

    public MockPing(final InetAddress dest, final PingListener listener, int ... timeouts) {
        super(dest, listener);
        this.timeouts = timeouts;
    }

    @Override
    protected long calcLatency(final long startTimestamp, final long endTimestamp) {
        return mCount + 10;
    }

    @Override
    protected FileDescriptor socket(final int inet, final int proto) throws ErrnoException {
        mCount = 0;
        return FileDescriptor.in;
    }

    @Override
    protected void setLowDelay(final FileDescriptor fd) {
        //Intentionally blank
    }

    @Override
    protected int sendto(final FileDescriptor fd, final ByteBuffer byteBuffer) throws ErrnoException {
        mBuffer = byteBuffer.array();
        return mBuffer.length;
    }

    @Override
    protected int poll(final StructPollfd[] structPollfds) {
        final short revents;
        if (Arrays.binarySearch(timeouts, mCount) >= 0) {
            revents = 0;
        } else {
            revents = POLLIN;
        }
        structPollfds[0].revents = revents;
        mCount++;
        return 0;
    }

    @Override
    protected int recvfrom(final FileDescriptor fd, final byte[] buffer) {
        System.arraycopy(mBuffer, 0, buffer, 0, mBuffer.length);
        return mBuffer.length;
    }

    @Override
    protected void close(final FileDescriptor fd) {
        //Intentionally blank
    }

    @Override
    protected void sleep() {
        //Intentionally blank
    }
}
