package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.message.AcknowledgeDeviceMessage;
import org.jetlinks.core.message.HeaderKey;

public class BinaryAcknowledgeDeviceMessage implements BinaryMessage<AcknowledgeDeviceMessage> {

    public static final HeaderKey<String> codeHeader = HeaderKey.of("code", AckCode.ok.name());

    private AcknowledgeDeviceMessage message;

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.ack;
    }

    @Override
    public void read(ByteBuf buf) {
        message = new AcknowledgeDeviceMessage();
        AckCode code = AckCode.values()[buf.readUnsignedByte()];
        message.addHeader(codeHeader, code.name());
    }

    @Override
    public void write(ByteBuf buf) {
        AckCode code = AckCode.valueOf(this.message.getHeaderOrDefault(codeHeader));
        buf.writeByte(code.ordinal());
    }

    @Override
    public void setMessage(AcknowledgeDeviceMessage message) {
        this.message = message;
    }

    @Override
    public AcknowledgeDeviceMessage getMessage() {
        return message;
    }
}
