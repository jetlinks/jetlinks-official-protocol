package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.message.property.WritePropertyMessage;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
public class BinaryWritePropertyMessage implements BinaryMessage<WritePropertyMessage> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.writeProperty;
    }

    private WritePropertyMessage message;

    @Override
    public void read(ByteBuf buf) {
        message = new WritePropertyMessage();
        @SuppressWarnings("all")
        Map<String, Object> map = (Map<String, Object>) DataType.OBJECT.read(buf);
        message.setProperties(map);
    }

    @Override
    public void write(ByteBuf buf) {
        DataType.OBJECT.write(buf, message.getProperties());
    }

    @Override
    public void setMessage(WritePropertyMessage message) {
        this.message = message;
    }

    @Override
    public WritePropertyMessage getMessage() {
        return message;
    }

}
