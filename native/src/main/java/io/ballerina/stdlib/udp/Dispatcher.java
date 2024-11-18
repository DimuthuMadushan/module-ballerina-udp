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

import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.concurrent.StrandMetadata;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.Parameter;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Dispatch async methods.
 */
public final class Dispatcher {

    private Dispatcher() {}

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private static void invokeOnBytes(UdpService udpService, DatagramPacket datagramPacket, Channel channel,
                                      Type[] parameterTypes) {
        try {
            Object[] params = getOnBytesSignature(datagramPacket, channel, parameterTypes);
            invokeAsyncCall(udpService, datagramPacket, channel, Constants.ON_BYTES, params);
        } catch (BError e) {
            Dispatcher.invokeOnError(udpService, e.getMessage());
        }
    }

    private static void invokeOnDatagram(UdpService udpService, DatagramPacket datagramPacket, Channel channel,
                                         Type[] parameterTypes) {
        try {
            Object[] params = getOnDatagramSignature(datagramPacket, channel, parameterTypes);
            invokeAsyncCall(udpService, datagramPacket, channel, Constants.ON_DATAGRAM, params);
        } catch (BError e) {
            Dispatcher.invokeOnError(udpService, e.getMessage());
        }
    }

    public static void invokeOnError(UdpService udpService, String message) {
        try {
            ObjectType objectType =
                    (ObjectType) TypeUtils.getReferredType(TypeUtils.getType(udpService.getService()));
            MethodType methodType = Arrays.stream(objectType.getMethods()).
                    filter(m -> m.getName().equals(Constants.ON_ERROR)).findFirst().orElse(null);
            if (methodType != null) {
                Object[] params = getOnErrorSignature(message);
                invokeAsyncCall(udpService, null, null, Constants.ON_ERROR, params);
            }
        } catch (Throwable t) {
            log.error("Error while executing onError function", t);
        }
    }

    private static void invokeAsyncCall(UdpService udpService, DatagramPacket datagramPacket, Channel channel,
                                        String methodName, Object[] params) {
        Thread.startVirtualThread(() -> {
            BObject service = udpService.getService();
            Runtime runtime = udpService.getRuntime();
            ObjectType objectType = (ObjectType) TypeUtils.getReferredType(TypeUtils.getType(service));
            StrandMetadata metadata = new StrandMetadata(
                    objectType.isIsolated() && objectType.isIsolated(methodName), null);
            Object result;
            try {
                result = runtime.callMethod(service, methodName, metadata, params);
                handleResult(udpService, datagramPacket, channel, result);
            } catch (BError error) {
                handleError(error);
            } catch (Throwable throwable) {
                handleError(ErrorCreator.createError(throwable));
            }
        });
    }

    private static Object[] getOnBytesSignature(DatagramPacket datagramPacket, Channel channel, Type[] parameterTypes) {
        byte[] byteContent = new byte[datagramPacket.content().readableBytes()];
        datagramPacket.content().readBytes(byteContent);

        Object[] bValues = new Object[parameterTypes.length];
        int index = 0;
        for (Type param : parameterTypes) {
            int paramTag = param.getTag();
            switch (paramTag) {
                case TypeTags.INTERSECTION_TAG:
                    bValues[index++] = ValueCreator.createReadonlyArrayValue(byteContent);
                    break;
                case TypeTags.OBJECT_TYPE_TAG:
                    bValues[index++] = createClient(datagramPacket, channel);
                    break;
                default:
                    break;
            }
        }
        return bValues;
    }

    private static Object[] getOnDatagramSignature(DatagramPacket datagramPacket, Channel channel,
                                                   Type[] parameterTypes) {
        Object[] bValues = new Object[parameterTypes.length];
        int index = 0;
        for (Type param : parameterTypes) {
            int paramTag = param.getTag();
            switch (paramTag) {
                case TypeTags.INTERSECTION_TAG:
                    bValues[index++] = Utils.createReadOnlyDatagramWithSenderAddress(datagramPacket);
                    break;
                case TypeTags.OBJECT_TYPE_TAG:
                    bValues[index++] = createClient(datagramPacket, channel);
                    break;
                default:
                    break;
            }
        }
        return bValues;
    }

    private static Object[] getOnErrorSignature(String message) {
        return new Object[]{Utils.createUdpError(message)};
    }

    private static BObject createClient(DatagramPacket datagramPacket, Channel channel) {
        final BObject caller = ValueCreator.createObjectValue(Utils.getUdpPackage(), Constants.CALLER);
        caller.set(StringUtils.fromString(Constants.CALLER_REMOTE_PORT), datagramPacket.sender().getPort());
        caller.set(StringUtils.fromString(Constants.CALLER_REMOTE_HOST),
                StringUtils.fromString(datagramPacket.sender().getHostName()));
        caller.addNativeData(Constants.CHANNEL, channel);
        return caller;
    }

    public static void invokeRead(UdpService udpService, DatagramPacket datagramPacket, Channel channel) {
        ObjectType objectType =
                (ObjectType) TypeUtils.getReferredType(TypeUtils.getType(udpService.getService()));

        for (MethodType method : objectType.getMethods()) {
            switch (method.getName()) {
                case Constants.ON_BYTES:
                    Dispatcher.invokeOnBytes(udpService, datagramPacket, channel,
                            getParameterTypes(method.getType().getParameters()));
                    break;
                case Constants.ON_DATAGRAM:
                    Dispatcher.invokeOnDatagram(udpService, datagramPacket, channel,
                            getParameterTypes(method.getType().getParameters()));
                    break;
                default:
                    break;
            }
        }
    }

    private static Type[] getParameterTypes(Parameter[] parameters) {
        Type[] parameterTypes = new Type[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            parameterTypes[i] = parameters[i].type;
        }
        return parameterTypes;
    }

    private static void handleResult(UdpService udpService, DatagramPacket datagramP, Channel channel, Object object) {
        if (object instanceof BArray) {
            // call writeBytes if the service returns byte[]
            byte[] byteContent = ((BArray) object).getBytes();
            UdpListener.send(udpService, new DatagramPacket(Unpooled.wrappedBuffer(byteContent),
                    datagramP.sender()), channel);
        } else if (object instanceof BMap) {
            // call sendDatagram if the service returns Datagram
            BMap<BString, Object> datagram = (BMap<BString, Object>) object;
            String host = datagram.getStringValue(StringUtils.fromString(Constants.DATAGRAM_REMOTE_HOST)).getValue();
            int port = datagram.getIntValue(StringUtils.fromString(Constants.DATAGRAM_REMOTE_PORT)).intValue();
            BArray data = datagram.getArrayValue(StringUtils.fromString(Constants.DATAGRAM_DATA));
            byte[] byteContent = data.getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(Unpooled.wrappedBuffer(byteContent),
                    new InetSocketAddress(host, port));
            UdpListener.send(udpService, datagramPacket, channel);
        } else if (object instanceof BError) {
            ((BError) object).printStackTrace();
        }
        log.debug("Method successfully dispatched.");
    }


    public static void handleError(BError bError) {
        bError.printStackTrace();
    }
}
