/*
 * Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.udp.nativelistener;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.udp.Constants;
import io.ballerina.stdlib.udp.UdpListener;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static io.ballerina.stdlib.udp.Utils.getResult;

/**
 * Native function implementations of the UDP Caller.
 */
public final class Caller {

    private Caller() {}

    public static Object sendBytes(Environment env, BObject caller, BArray data) {
        return env.yieldAndRun(() -> {
            CompletableFuture<Object> balFuture = new CompletableFuture<>();
            byte[] byteContent = data.getBytes();
            String remoteHost = caller.getStringValue(StringUtils.fromString(Constants.CALLER_REMOTE_HOST))
                    .getValue();
            int remotePort = ((Integer) caller.get(StringUtils.fromString(Constants.CALLER_REMOTE_PORT)));
            InetSocketAddress remoteAddress = new InetSocketAddress(remoteHost, remotePort);
            DatagramPacket datagram = new DatagramPacket(Unpooled.wrappedBuffer(byteContent), remoteAddress);
            Channel channel = (Channel) caller.getNativeData(Constants.CHANNEL);

            UdpListener.send(datagram, channel, balFuture);
            return getResult(balFuture);
        });
    }

    public static Object sendDatagram(Environment env, BObject caller, BMap<BString, Object> datagram) {
        return env.yieldAndRun(() -> {
            CompletableFuture<Object> balFuture = new CompletableFuture<>();
            String host = datagram.getStringValue(StringUtils.fromString(Constants.DATAGRAM_REMOTE_HOST)).getValue();
            int port = datagram.getIntValue(StringUtils.fromString(Constants.DATAGRAM_REMOTE_PORT)).intValue();
            BArray data = datagram.getArrayValue(StringUtils.fromString(Constants.DATAGRAM_DATA));
            byte[] byteContent = data.getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(Unpooled.wrappedBuffer(byteContent),
                    new InetSocketAddress(host, port));

            Channel channel = (Channel) caller.getNativeData(Constants.CHANNEL);
            UdpListener.send(datagramPacket, channel, balFuture);
            return getResult(balFuture);
        });
    }
}
