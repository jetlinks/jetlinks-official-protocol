package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.message.DeviceMessage;
import reactor.core.publisher.Flux;

public interface BinaryMessage<T extends DeviceMessage> {

    BinaryMessageType getType();

    void read(ByteBuf buf);

    void write(ByteBuf buf);

    void setMessage(T message);

    T getMessage();

}
