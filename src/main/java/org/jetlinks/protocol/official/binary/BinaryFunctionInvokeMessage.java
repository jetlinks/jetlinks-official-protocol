package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionParameter;

import java.util.Map;
import java.util.stream.Collectors;

public class BinaryFunctionInvokeMessage implements BinaryMessage<FunctionInvokeMessage> {
    private FunctionInvokeMessage message;

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.function;
    }

    @Override
    public void read(ByteBuf buf) {
        message = new FunctionInvokeMessage();
        message.setFunctionId((String) DataType.STRING.read(buf));

        @SuppressWarnings("all")
        Map<String, Object> params = (Map<String, Object>) DataType.OBJECT.read(buf);
        message.setInputs(
                params
                        .entrySet()
                        .stream()
                        .map(e -> new FunctionParameter(e.getKey(), e.getValue()))
                        .collect(Collectors.toList())
        );

    }

    @Override
    public void write(ByteBuf buf) {
        DataType.STRING.write(buf,message.getFunctionId());
        DataType.OBJECT.write(buf,message.inputsToMap());
    }

    @Override
    public void setMessage(FunctionInvokeMessage message) {
        this.message = message;
    }

    @Override
    public FunctionInvokeMessage getMessage() {
        return message;
    }
}
