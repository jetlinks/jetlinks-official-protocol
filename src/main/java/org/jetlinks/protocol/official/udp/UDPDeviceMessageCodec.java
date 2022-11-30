package org.jetlinks.protocol.official.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.protocol.official.binary.*;
import org.reactivestreams.Publisher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class UDPDeviceMessageCodec implements DeviceMessageCodec {

    public static final String CONFIG_KEY_SECURE_KEY = "secureKey";

    public static final DefaultConfigMetadata udpConfig = new DefaultConfigMetadata(
            "UDP认证配置"
            , "")
            .add(CONFIG_KEY_SECURE_KEY, "secureKey", "密钥", new PasswordType());


    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.UDP;
    }

    @NonNull
    @Override
    public Publisher<? extends Message> decode(@NonNull MessageDecodeContext context) {

        ByteBuf payload = context.getMessage().getPayload();

        //todo 认证类型, 0 token,1 sign
        byte authType = payload.readByte();

        //前面是token
        String token = (String) DataType.STRING.read(payload);

        //接下来是消息
        DeviceMessage message = BinaryMessageType.read(payload);

        return context
                .getDevice(message.getDeviceId())
                .flatMap(device -> device
                        .getConfig(CONFIG_KEY_SECURE_KEY)
                        .flatMap(config -> {
                            if (Objects.equals(config.asString(), token)) {
                                return ack(message, AckCode.ok, context)
                                        .thenReturn(message);
                            }
                            return Mono.empty();
                        }))
                .switchIfEmpty(Mono.defer(() -> ack(message, AckCode.noAuth, context)));
    }

    public static ByteBuf wrapByteByf(ByteBuf payload) {

        return payload;
    }

    private <T> Mono<T> ack(DeviceMessage source, AckCode code, MessageDecodeContext context) {
        AcknowledgeDeviceMessage message = new AcknowledgeDeviceMessage();
        message.addHeader(BinaryAcknowledgeDeviceMessage.codeHeader, code.name());
        message.setDeviceId(source.getDeviceId());
        message.setMessageId(source.getMessageId());
        message.setCode(code.name());
        message.setSuccess(code == AckCode.ok);

        source.getHeader(BinaryMessageType.HEADER_MSG_SEQ)
              .ifPresent(seq -> message.addHeader(BinaryMessageType.HEADER_MSG_SEQ, seq));

        return ((FromDeviceMessageContext) context)
                .getSession()
                .send(doEncode(message, ""))
                .then(Mono.fromRunnable(() -> {
                    if (source instanceof DeviceOnlineMessage && code != AckCode.ok) {
                        ((FromDeviceMessageContext) context).getSession().close();
                    }
                }));
    }

    @NonNull
    @Override
    public Publisher<? extends EncodedMessage> encode(@NonNull MessageEncodeContext context) {
        DeviceMessage deviceMessage = ((DeviceMessage) context.getMessage());
        if (deviceMessage instanceof DisconnectDeviceMessage) {
            return Mono.empty();
        }

        return context
                .getDevice(deviceMessage.getDeviceId())
                .flatMap(device -> device
                        .getConfig(CONFIG_KEY_SECURE_KEY)
                        .map(config -> doEncode(deviceMessage, config.asString())));
    }

    private EncodedMessage doEncode(DeviceMessage message, String token) {
        ByteBuf buf = Unpooled.buffer();
        //todo 认证类型, 0 token,1 sign
        buf.writeByte(0);
        //token
        DataType.STRING.write(buf, token);
        //指令
        return EncodedMessage.simple(wrapByteByf(BinaryMessageType.write(message, buf)));

    }

}
