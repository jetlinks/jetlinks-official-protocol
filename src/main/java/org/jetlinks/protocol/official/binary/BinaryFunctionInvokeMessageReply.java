package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.WritePropertyMessageReply;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
public class BinaryFunctionInvokeMessageReply extends BinaryReplyMessage<FunctionInvokeMessageReply> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.functionReply;
    }

    @Override
    protected FunctionInvokeMessageReply newMessage() {
        return new FunctionInvokeMessageReply();
    }

    @Override
    protected void doReadSuccess(FunctionInvokeMessageReply msg, ByteBuf buf) {
        msg.setFunctionId((String) DataType.readFrom(buf));
        msg.setOutput(DataType.readFrom(buf));
    }

    @Override
    protected void doWriteSuccess(FunctionInvokeMessageReply msg, ByteBuf buf) {
        DataType.writeTo(getMessage().getFunctionId(), buf);
        DataType.writeTo(msg.getOutput(), buf);
    }


}
