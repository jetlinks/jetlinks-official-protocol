package org.jetlinks.protocol.official.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.Principal;
import org.jetlinks.core.principal.TokenCredential;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.binary.*;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingDevicePrincipal;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
import org.reactivestreams.Publisher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class UDPDeviceMessageCodec extends BlockingDeviceMessageCodec {
    public static final String identityType = "j_udp";

    public static final ConfigKey<String> CONFIG_KEY_SECURE_KEY = ConfigKey.of("secureKey");

    public static final DefaultConfigMetadata udpConfig = new DefaultConfigMetadata(
        "UDP认证配置"
        , "")
        .add(CONFIG_KEY_SECURE_KEY.getKey(), "secureKey", "密钥", new PasswordType());


    public UDPDeviceMessageCodec(ServiceContext context) {
        super(context, DefaultTransport.UDP);
    }

    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        ByteBuf payload = context.getData().getPayload();

        // 前面是token
        String token = (String) DataType.STRING.read(payload);

        // 接下来是消息
        DeviceMessage message = BinaryMessageType.read(payload);

        // 走平台的身份认证
        BlockingDevicePrincipal principal = context.resolveDevice(
            Principal.create(
                Identity.create(identityType, message.getDeviceId()),
                TokenCredential.create(token)
            )
        );

        if (principal == null || !principal.isVerified()) {
            ack(message, AckCode.noAuth, context);
            return;
        }

        context.sendToPlatformLater(message);
        ack(message, AckCode.ok, context);
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        BlockingDeviceOperator device = context.getDevice();

        String key = device.getConfigNow(CONFIG_KEY_SECURE_KEY);

        context.sendToDeviceLater(doEncode(context.getMessage(), key));
    }

    public static ByteBuf wrapByteByf(ByteBuf payload) {

        return payload;
    }

    private void ack(DeviceMessage source, AckCode code, BlockingMessageDecodeContext context) {

        AcknowledgeDeviceMessage message = new AcknowledgeDeviceMessage();
        message.addHeader(BinaryAcknowledgeDeviceMessage.codeHeader, code.name());
        message.setDeviceId(source.getDeviceId());
        message.setMessageId(source.getMessageId());
        message.setCode(code.name());
        message.setSuccess(code == AckCode.ok);

        source.getHeader(BinaryMessageType.HEADER_MSG_SEQ)
              .ifPresent(seq -> message.addHeader(BinaryMessageType.HEADER_MSG_SEQ, seq));

        context.sendToDeviceLater(doEncode(message, ""));

        if (source instanceof DeviceOnlineMessage && code != AckCode.ok) {

            context.disconnectLater();
        }


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
