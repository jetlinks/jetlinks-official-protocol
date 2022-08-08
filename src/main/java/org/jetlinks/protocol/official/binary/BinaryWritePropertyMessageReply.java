package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.property.ReadPropertyMessageReply;
import org.jetlinks.core.message.property.WritePropertyMessageReply;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
public class BinaryWritePropertyMessageReply extends BinaryReplyMessage<WritePropertyMessageReply> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.readPropertyReply;
    }

    @Override
    protected WritePropertyMessageReply newMessage() {
        return new WritePropertyMessageReply();
    }

    @Override
    protected void doReadSuccess(WritePropertyMessageReply msg, ByteBuf buf) {
        @SuppressWarnings("all")
        Map<String, Object> map = (Map<String, Object>) DataType.OBJECT.read(buf);
        msg.setProperties(map);
    }

    @Override
    protected void doWriteSuccess(WritePropertyMessageReply msg, ByteBuf buf) {
        DataType.OBJECT.write(buf, msg.getProperties());
    }


}
