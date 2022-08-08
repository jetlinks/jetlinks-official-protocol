package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.HeaderKey;

/**
 *
 */
public class BinaryDeviceOnlineMessage implements BinaryMessage<DeviceOnlineMessage> {

    public static final HeaderKey<String> loginToken = HeaderKey.of("token", null);

    private DeviceOnlineMessage message;

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.online;
    }

    @Override
    public void read(ByteBuf buf) {
        message = new DeviceOnlineMessage();
        message.addHeader(loginToken, (String) DataType.STRING.read(buf));
    }

    @Override
    public void write(ByteBuf buf) {
        DataType.STRING
                .write(
                        buf, message.getHeader(loginToken).orElse("")
                );
    }

    @Override
    public void setMessage(DeviceOnlineMessage message) {
        this.message = message;
    }

    @Override
    public DeviceOnlineMessage getMessage() {
        return message;
    }

}
