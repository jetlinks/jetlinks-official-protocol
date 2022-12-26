package org.jetlinks.protocol.official.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.protocol.official.binary.AckCode;
import org.jetlinks.protocol.official.binary.BinaryAcknowledgeDeviceMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class TcpDeviceMessageCodec implements DeviceMessageCodec {

    public static final String CONFIG_KEY_SECURE_KEY = "secureKey";

    public static final DefaultConfigMetadata tcpConfig = new DefaultConfigMetadata(
            "TCP认证配置"
            , "")
            .add(CONFIG_KEY_SECURE_KEY, "secureKey", "密钥", new PasswordType());


    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.TCP;
    }

    @NonNull
    @Override
    public Publisher<? extends Message> decode(@NonNull MessageDecodeContext context) {

        ByteBuf payload = context.getMessage().getPayload();
        //read index
        payload.readInt();

        //处理tcp连接后的首次消息
        if (context.getDevice() == null) {
            return handleLogin(payload, context);
        }
        return Mono.justOrEmpty(BinaryMessageType.read(payload, context.getDevice().getDeviceId()));
    }

    private Mono<DeviceMessage> handleLogin(ByteBuf payload, MessageDecodeContext context) {
        DeviceMessage message = BinaryMessageType.read(payload);
        if (message instanceof DeviceOnlineMessage) {
            String token = message
                    .getHeader(BinaryDeviceOnlineMessage.loginToken)
                    .orElse(null);

            String deviceId = message.getDeviceId();
            return context
                    .getDevice(deviceId)
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

        } else {
            return ack(message, AckCode.noAuth, context);
        }
    }

    public static ByteBuf wrapByteByf(ByteBuf payload) {

        return Unpooled.wrappedBuffer(
                Unpooled.buffer().writeInt(payload.writerIndex()),
                payload);
    }

    private <T> Mono<T> ack(DeviceMessage source, AckCode code, MessageDecodeContext context) {
        if(source==null){
            return Mono.empty();
        }
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
                .send(EncodedMessage.simple(
                        wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))
                ))
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
        return Mono.just(EncodedMessage.simple(
                wrapByteByf(
                        BinaryMessageType.write(deviceMessage, Unpooled.buffer())
                )
        ));
    }


}
