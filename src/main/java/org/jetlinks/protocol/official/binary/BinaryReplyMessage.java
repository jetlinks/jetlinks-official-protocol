package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.message.DeviceMessageReply;

import java.util.Map;

public abstract class BinaryReplyMessage<T extends DeviceMessageReply> implements BinaryMessage<T> {

    private T message;

    protected abstract T newMessage();

    @Override
    public final void read(ByteBuf buf) {
        message = newMessage();
        boolean success = buf.readBoolean();
        if (success) {
            doReadSuccess(message, buf);
        } else {
            message.success(false);
            message.code(String.valueOf(DataType.readFrom(buf)));
            message.message(String.valueOf(DataType.readFrom(buf)));
        }
    }

    protected abstract void doReadSuccess(T msg, ByteBuf buf);

    protected abstract void doWriteSuccess(T msg, ByteBuf buf);

    @Override
    public final void write(ByteBuf buf) {
        buf.writeBoolean(message.isSuccess());

        if (message.isSuccess()) {
            doWriteSuccess(message, buf);
        } else {
            DataType.writeTo(message.getCode(), buf);
            DataType.writeTo(message.getMessage(), buf);
        }
    }

    @Override
    public void setMessage(T message) {
        this.message = message;
    }

    @Override
    public T getMessage() {
        return message;
    }
}
