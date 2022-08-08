package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;

import java.util.List;
import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
public class BinaryReadPropertyMessage implements BinaryMessage<ReadPropertyMessage> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.readProperty;
    }

    private ReadPropertyMessage message;

    @Override
    public void read(ByteBuf buf) {
        message = new ReadPropertyMessage();
        @SuppressWarnings("all")
        List<String> list = (List<String>)  DataType.ARRAY.read(buf);
        message.setProperties(list);
    }

    @Override
    public void write(ByteBuf buf) {
        DataType.ARRAY.write(buf, message.getProperties());
    }

    @Override
    public void setMessage(ReadPropertyMessage message) {
        this.message = message;
    }

    @Override
    public ReadPropertyMessage getMessage() {
        return message;
    }

}
