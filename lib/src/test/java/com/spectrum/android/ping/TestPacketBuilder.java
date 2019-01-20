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

import org.junit.Assert;
import org.junit.Test;

public class TestPacketBuilder {
    //ICMP packet borrowed from Wikipedia
    private static final String ICMP_PACKET="0800 4d35 0001 0026 6162 6364 " +
            "6566 6768 696a 6b6c 6d6e 6f70 7172 7374 " +
            "7576 7761 6263 6465 6667 6869 ";

    private static byte[] sPacket = new byte[ICMP_PACKET.length() / 5 * 2];

    static {
        int i=0;
        while (i<sPacket.length) {
            final int idx = i/2*5;
            final String hex1 = ICMP_PACKET.substring(idx, idx + 2);
            sPacket[i++] = (byte)Integer.parseInt(hex1, 16);
            final String hex2 = ICMP_PACKET.substring(idx + 2, idx + 4);
            sPacket[i++] = (byte)Integer.parseInt(hex2, 16);
        }
    }

    @Test
    public void testPacketBuilder() {

        final String payload = "abcdefghijklmnopqrstuvwabcdefghi";
        final EchoPacketBuilder pingPacketBuilder = new EchoPacketBuilder(EchoPacketBuilder.TYPE_ICMP_V4, payload.getBytes());
        pingPacketBuilder.setIdentifier((short)1);
        pingPacketBuilder.setSequenceNumber((short)0x26);
        pingPacketBuilder.setAutoIdentifier(false);
        Assert.assertArrayEquals(sPacket, pingPacketBuilder.build().array());
    }

    @Test
    public void testNullPayload() {
        final EchoPacketBuilder pingPacketBuilder = new EchoPacketBuilder(EchoPacketBuilder.TYPE_ICMP_V4, null);
        //8 is the header size
        Assert.assertEquals(8, pingPacketBuilder.build().limit());
    }

    @Test
    public void testOversizedPayload() {
        try {
            final EchoPacketBuilder pingPacketBuilder = new EchoPacketBuilder(EchoPacketBuilder.TYPE_ICMP_V4, new byte[EchoPacketBuilder.MAX_PAYLOAD + 1]);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            //Correct path
        }
    }
}
