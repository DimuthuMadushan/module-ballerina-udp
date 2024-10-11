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

package io.ballerina.stdlib.udp;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * {@link UdpFactory} creates {@link UdpClient} and UdpListener.
 */
public class UdpFactory {

    private static volatile UdpFactory udpFactory = new UdpFactory();;
    private EventLoopGroup group;

    private UdpFactory() {
        group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
    }

    public static UdpFactory getInstance() {
        return udpFactory;
    }

    public UdpClient createUdpClient(InetSocketAddress localAddress, InetSocketAddress remoteAddress,
                                     CompletableFuture<Object> balFuture) {
        return new UdpClient(localAddress, remoteAddress, getInstance().group, balFuture);
    }

    public UdpClient createUdpClient(InetSocketAddress localAddress, CompletableFuture<Object> balFuture) {
        return new UdpClient(localAddress, getInstance().group, balFuture);
    }

    public UdpListener createUdpListener(InetSocketAddress localAddress, InetSocketAddress remoteAddress,
                                         CompletableFuture<Object> balFuture, UdpService udpService) {
        return new UdpListener(localAddress, remoteAddress, getInstance().group, balFuture, udpService);
    }
}
