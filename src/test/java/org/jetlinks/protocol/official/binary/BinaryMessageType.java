package org.jetlinks.protocol.official.binary;

import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import lombok.SneakyThrows;
import org.jetlinks.core.device.DeviceThingType;
import org.jetlinks.core.message.AcknowledgeDeviceMessage;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public enum BinaryMessageType {
    keepalive(null, null),

    online(DeviceOnlineMessage.class, BinaryDeviceOnlineMessage::new),

    ack(AcknowledgeDeviceMessage.class, BinaryAcknowledgeDeviceMessage::new),

    reportProperty(ReportPropertyMessage.class, BinaryReportPropertyMessage::new),

    readProperty(ReadPropertyMessage.class, BinaryReadPropertyMessage::new),

    readPropertyReply(ReadPropertyMessageReply.class, BinaryReadPropertyMessageReply::new),

    writeProperty(WritePropertyMessage.class, BinaryWritePropertyMessage::new),

    writePropertyReply(WritePropertyMessageReply.class, BinaryWritePropertyMessageReply::new),

    function(FunctionInvokeMessage.class, BinaryFunctionInvokeMessage::new),

    functionReply(FunctionInvokeMessageReply.class, BinaryFunctionInvokeMessageReply::new);

    private final Class<? extends DeviceMessage> forDevice;

    private final Supplier<BinaryMessage<DeviceMessage>> forTcp;

    private static final BinaryMessageType[] VALUES = values();

    @SuppressWarnings("all")
    BinaryMessageType(Class<? extends DeviceMessage> forDevice,
                      Supplier<? extends BinaryMessage<?>> forTcp) {
        this.forDevice = forDevice;
        this.forTcp = (Supplier) forTcp;
    }

    private static final Map<String, MsgIdHolder> cache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .<String, MsgIdHolder>build()
            .asMap();

    private static class MsgIdHolder {
        private int msgId = 0;
        private final Map<Integer, String> cached = CacheBuilder
                .newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .<Integer, String>build()
                .asMap();

        public int next(String id) {
            if (id == null) {
                return -1;
            }
            do {
                if (msgId++ < 0) {
                    msgId = 0;
                }
            } while (cached.putIfAbsent(msgId, id) != null);

            return msgId;
        }

        public String getAndRemove(int id) {
            if (id < 0) {
                return null;
            }
            return cached.remove(id);
        }

    }

    @SneakyThrows
    private static MsgIdHolder takeHolder(String deviceId) {
        return cache.computeIfAbsent(deviceId, (ignore) -> new MsgIdHolder());
    }

    public static ByteBuf write(DeviceMessage message, ByteBuf data) {
        int msgId = takeHolder(message.getDeviceId()).next(message.getMessageId());
        return write(message, msgId, data);
    }

    public static ByteBuf write(BinaryMessageType type, ByteBuf data) {
        // 第0个字节是消息类型
        data.writeByte(type.ordinal());
        // 0-4字节 时间戳
        data.writeLong(System.currentTimeMillis());

        return data;
    }

    public static ByteBuf write(DeviceMessage message, int msgId, ByteBuf data) {
        BinaryMessageType type = lookup(message);
        // 第0个字节是消息类型
        data.writeByte(type.ordinal());
        // 0-4字节 时间戳
        data.writeLong(message.getTimestamp());

        // 5-6字节 消息序号
        data.writeShort(msgId);

        // 7... 字节 设备ID
        DataType.writeTo(message.getDeviceId(), data);

        // 创建消息对象
        BinaryMessage<DeviceMessage> tcp = type.forTcp.get();

        tcp.setMessage(message);

        //写出数据到ByteBuf
        tcp.write(data);
        return data;
    }

    public static DeviceMessage read(ByteBuf data) {
        return read(data, null);
    }

    public static <T> T read(ByteBuf data,
                             String deviceIdMaybe,
                             BiFunction<DeviceMessage, Integer, T> handler) {
        //第0个字节是消息类型
        BinaryMessageType type = VALUES[data.readByte()];
        if (type.forTcp == null) {
            return null;
        }
        // 1-4字节 时间戳
        long timestamp = data.readLong();
        // 5-6字节 消息序号
        int msgId = data.readUnsignedShort();
        // 7... 字节 设备ID
        String deviceId = (String) DataType.readFrom(data);
        if (deviceId == null) {
            deviceId = deviceIdMaybe;
        }

        // 创建消息对象
        BinaryMessage<DeviceMessage> tcp = type.forTcp.get();

        //从ByteBuf读取
        tcp.read(data);

        DeviceMessage message = tcp.getMessage();
        message.thingId(DeviceThingType.device, deviceId);
        message.timestamp(timestamp);

        return handler.apply(message, msgId);
    }

    public static DeviceMessage read(ByteBuf data, String deviceIdMaybe) {
        return read(data, deviceIdMaybe, (message, msgId) -> {
            String messageId = null;
            if (message.getDeviceId() != null) {
                //获取实际平台下发的消息ID
                MsgIdHolder holder = cache.get(message.getDeviceId());
                if (holder != null) {
                    messageId = holder.getAndRemove(msgId);
                }
            }

            if (messageId == null) {
                messageId = String.valueOf(msgId);
            }
            message.messageId(messageId);
            return message;
        });
    }

    public static BinaryMessageType lookup(DeviceMessage message) {
        for (BinaryMessageType value : VALUES) {
            if (value.forDevice != null && value.forDevice.isInstance(message)) {
                return value;
            }
        }
        throw new UnsupportedOperationException("unsupported device message " + message.getMessageType());
    }


    public static Map<Integer,DeviceMessage> readServer(ByteBuf data) {

        long i = data.readByte();
        i = data.readByte();
        i = data.readByte();
        i = data.readByte();


        //第0个字节是消息类型
        BinaryMessageType type = VALUES[data.readByte()];
        if (type.forTcp == null) {
            return null;
        }
        // 1-4字节 时间戳
        long timestamp = data.readLong();
        // 5-6字节 消息序号
        int msgId = data.readUnsignedShort();
        // 7... 字节 设备ID
        String deviceId = (String) DataType.readFrom(data);
        if (deviceId == null) {
            deviceId = "1";
        }

        // 创建消息对象
        BinaryMessage<DeviceMessage> tcp = type.forTcp.get();


        //从ByteBuf读取
        tcp.read(data);


        DeviceMessage message = tcp.getMessage();
        message.thingId(DeviceThingType.device, deviceId);
        message.timestamp(timestamp);
        Map<Integer,DeviceMessage> map = new HashMap<>();
        map.put(msgId,message);


        return map;
    }


}
