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

import android.net.Network;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class Ping implements Runnable {
    public static final int DEFAULT_COUNT = 8;
    public static final long TIMED_OUT_MS = -1;
    private static final String TAG = Ping.class.getSimpleName();

    private static final int IPTOS_LOWDELAY = 0x10;

    private static final int ECHO_PORT = 7;
    //POLLIN isn't populated correctly in test stubs
    protected static final short POLLIN = (short)(OsConstants.POLLIN==0?1:OsConstants.POLLIN);
    private static final int MSG_DONTWAIT = 0x40;
    private final InetAddress mDest;
    private final PingListener mListener;

    private int mTimeoutMs = 4000;
    private int mDelayMs = 1000;
    private int mCount = DEFAULT_COUNT;
    private EchoPacketBuilder mEchoPacketBuilder;
    private Network mNetwork;

    public interface PingListener {
        /**
         * Callback for ping
         * @param timeMs time in ms for ping to return or @see Ping.TIMED_OUT_MS in case of timeout
         * @param index index of the current ping
         */
        void onPing(long timeMs, int index);

        /**
         * Ping critical failure
         * @param e
         * @param count
         */
        void onPingException(Exception e, int count);
    }

    /**
     *
     * @param dest Can be of type <code>Inet6Address</code> or <code>Inet4Address</code>
     * @param listener
     */
    public Ping(final InetAddress dest, final PingListener listener) {
        mDest = dest;
        if (listener == null) {
            throw new NullPointerException();
        }
        mListener = listener;
        final byte type = dest instanceof Inet6Address ? EchoPacketBuilder.TYPE_ICMP_V6 : EchoPacketBuilder.TYPE_ICMP_V4;
        setEchoPacketBuilder(new EchoPacketBuilder(type, "abcdefghijklmnopqrstuvwabcdefghi".getBytes()));
    }

    public void setTimeoutMs(final int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must not be negative: " + timeoutMs);
        }
        mTimeoutMs = timeoutMs;
    }

    public int getTimeoutMs() {
        return mTimeoutMs;
    }

    public int getDelayMs() {
        return mDelayMs;
    }

    public void setDelayMs(final int delayMs) {
        mDelayMs = delayMs;
    }

    public int getCount() {
        return mCount;
    }

    public void setCount(final int count) {
        mCount = count;
    }

    public Network getNetwork() {
        return mNetwork;
    }

    public void setNetwork(final Network network) {
        mNetwork = network;
    }

    public void setEchoPacketBuilder(final EchoPacketBuilder echoPacketBuilder) {
        mEchoPacketBuilder = echoPacketBuilder;
    }

    /**
     * Ping an IP address for N times
     * @return long[count] where -1 is timeout
     * @throws ErrnoException
     * @throws IOException
     */
    @Override
    public void run() {
        final int inet, proto;
        if (mDest instanceof Inet6Address) {
            inet = OsConstants.AF_INET6;
            proto = OsConstants.IPPROTO_ICMPV6;
        } else {
            inet = OsConstants.AF_INET;
            proto = OsConstants.IPPROTO_ICMP;
        }
        try {
            final FileDescriptor fd = socket(inet, proto);
            if (fd.valid()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mNetwork != null) {
                        mNetwork.bindSocket(fd);
                    }
                    setLowDelay(fd);

                    final StructPollfd structPollfd = new StructPollfd();
                    structPollfd.fd = fd;
                    structPollfd.events = POLLIN;
                    final StructPollfd[] structPollfds = {structPollfd};
                    for (int i = 0; i < mCount; i++) {
                        final ByteBuffer byteBuffer = mEchoPacketBuilder.build();
                        final byte[] buffer = new byte[byteBuffer.limit()];

                        try {
                            // Note: it appears that the OS updates the Checksum, Identifier, and Sequence number.  The payload appears to be untouched.
                            // These changes are not reflected in the buffer, but in the returning packet.
                            final long start = System.currentTimeMillis();
                            int rc = sendto(fd, byteBuffer);
                            if (rc >= 0) {
                                rc = poll(structPollfds);
                                final long time = calcLatency(start, System.currentTimeMillis());
                                if (rc >= 0) {
                                    if (structPollfd.revents == POLLIN) {
                                        structPollfd.revents = 0;
                                        rc = recvfrom(fd, buffer);
                                        if (rc < 0) {
                                            Log.d(TAG, "recvfrom() return failure: " + rc);
                                        }
                                        mListener.onPing(time, i);
                                    } else {
                                        mListener.onPing(TIMED_OUT_MS, i);
                                    }
                                } else {
                                    mListener.onPingException(new IOException("poll() failed"), i);
                                    break;
                                }
                            } else {
                                mListener.onPingException(new IOException("sendto() failed"), i);
                                break;
                            }
                        } catch (ErrnoException e) {
                            mListener.onPingException(e, i);
                            break;
                        }
                        sleep();
                    }
                } finally {
                    close(fd);
                }
            } else {
                mListener.onPingException(new IOException("Invalid FD " + fd.toString()), 0);
            }
        } catch (ErrnoException | IOException e) {
            mListener.onPingException(e, 0);
        }
    }

    /*
     * Testability methods
     */

    protected long calcLatency(final long startTimestamp, final long endTimestamp) {
        return endTimestamp - startTimestamp;
    }
    protected FileDescriptor socket(final int inet, final int proto) throws ErrnoException {
        return Os.socket(inet, OsConstants.SOCK_DGRAM, proto);
    }

    protected void setLowDelay(final FileDescriptor fd) throws ErrnoException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TOS, IPTOS_LOWDELAY);
        } else {
            try {
                final Method method = Os.class.getMethod("setsockoptInt", FileDescriptor.class, int.class, int.class, int.class);
                method.invoke(null, fd, OsConstants.IPPROTO_IP, OsConstants.IP_TOS, IPTOS_LOWDELAY);

            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                Log.e(TAG, "Could not setsockOptInt()", e);
            }
        }
    }

    protected int sendto(final FileDescriptor fd, final ByteBuffer byteBuffer) throws ErrnoException, SocketException {
        return Os.sendto(fd, byteBuffer, 0, mDest, ECHO_PORT);
    }

    protected int poll(final StructPollfd[] structPollfds) throws ErrnoException {
        return Os.poll(structPollfds, mTimeoutMs);
    }

    protected int recvfrom(final FileDescriptor fd, final byte[] buffer) throws ErrnoException, SocketException {
        return Os.recvfrom(fd, buffer, 0, buffer.length, MSG_DONTWAIT, null);
    }

    protected void close(final FileDescriptor fd) throws ErrnoException {
        Os.close(fd);
    }

    protected void sleep() {
        try {
            Thread.sleep(mDelayMs);
        } catch (InterruptedException e) {
            //Intentionally blank
        }
    }
}
