package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.property.ReadPropertyMessageReply;
import org.jetlinks.core.message.property.ReportPropertyMessage;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
public class BinaryReadPropertyMessageReply extends BinaryReplyMessage<ReadPropertyMessageReply> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.readPropertyReply;
    }

    @Override
    protected ReadPropertyMessageReply newMessage() {
        return new ReadPropertyMessageReply();
    }

    @Override
    protected void doWriteSuccess(ReadPropertyMessageReply msg, ByteBuf buf) {
        DataType.OBJECT.write(buf, msg.getProperties());
    }

    @Override
    protected void doReadSuccess(ReadPropertyMessageReply msg, ByteBuf buf) {
        @SuppressWarnings("all")
        Map<String, Object> map = (Map<String, Object>) DataType.OBJECT.read(buf);
        msg.setProperties(map);

    }


}
