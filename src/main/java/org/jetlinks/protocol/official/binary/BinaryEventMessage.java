package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.event.EventMessage;


/**
 * @since 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
public class BinaryEventMessage implements BinaryMessage<EventMessage> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.event;
    }

    private EventMessage message;

    @Override
    public void read(ByteBuf buf) {
        message = new EventMessage();
        message.setEvent((String) DataType.STRING.read(buf));
        message.setData(DataType.OBJECT.read(buf));
    }

    @Override
    public void write(ByteBuf buf) {
        DataType.STRING.write(buf,message.getEvent());
        DataType.OBJECT.write(buf, message.getData());
    }

    @Override
    public void setMessage(EventMessage message) {
        this.message = message;
    }

    @Override
    public EventMessage getMessage() {
        return message;
    }

}