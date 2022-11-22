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

public enum ServerReplyRead {
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

    private static final ServerReplyRead[] VALUES = values();

    ServerReplyRead(Class<? extends DeviceMessage> forDevice,
                    Supplier<? extends BinaryMessage<?>> forTcp) {
        this.forDevice = forDevice;
        this.forTcp = (Supplier) forTcp;
    }


    public static Map<Integer,DeviceMessage> readServer(ByteBuf data) {

        long i = data.readByte();
        i = data.readByte();
        i = data.readByte();
        i = data.readByte();


        //第0个字节是消息类型
        ServerReplyRead type = VALUES[data.readByte()];
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
