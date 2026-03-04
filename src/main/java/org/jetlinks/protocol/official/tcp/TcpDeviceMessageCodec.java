package org.jetlinks.protocol.official.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.Principal;
import org.jetlinks.core.principal.TokenCredential;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.binary.AckCode;
import org.jetlinks.protocol.official.binary.BinaryAcknowledgeDeviceMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingDevicePrincipal;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class TcpDeviceMessageCodec extends BlockingDeviceMessageCodec {
    public static final String identityType = "j_tcp";

    public static final ConfigKey<String> CONFIG_KEY_SECURE_KEY = ConfigKey.of("secureKey");

    public static final DefaultConfigMetadata tcpConfig = new DefaultConfigMetadata(
        "TCP认证配置"
        , "")
        .add(CONFIG_KEY_SECURE_KEY.getKey(), "secureKey", "密钥", new PasswordType());

    public TcpDeviceMessageCodec(ServiceContext context) {
        super(context, DefaultTransport.TCP);
    }

    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        ByteBuf payload = context.getData().getPayload();
        //read index
        payload.readInt();
        //使用内置的logger,便于平台收集和管理日志.
        Logger logger = context.logger();

        if (logger.isDebugEnabled()) {
            logger.debug("收到设备TCP报文: {}", ByteBufUtil.hexDump(payload));
        }

        BlockingDeviceOperator device = context.getDevice();
        if (device == null) {
            handleLogin(payload, context);
        } else {
            //直接解码并发送给平台
            context.sendToPlatformLater(BinaryMessageType.read(payload, device.getDeviceId()));
        }

    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        context.sendToDeviceLater(
            EncodedMessage.simple(
                wrapByteByf(
                    BinaryMessageType.write(context.getMessage(), Unpooled.buffer())
                )
            )
        );
    }

    private void handleLogin(ByteBuf payload, BlockingMessageDecodeContext context) {
        DeviceMessage message = BinaryMessageType.read(payload);
        if (message instanceof DeviceOnlineMessage) {

            String token = message
                .getHeader(BinaryDeviceOnlineMessage.loginToken)
                .orElse(null);

            String deviceId = message.getDeviceId();

            // 使用平台的身份认进行认证
            BlockingDevicePrincipal principal = context.resolveDevice(
                Principal.create(
                    Identity.create(identityType, deviceId),
                    TokenCredential.create(token)
                )
            );

            if (principal == null) {
                logger(deviceId).warn("设备不存在或未激活");
                ack(message, AckCode.noAuth, context);
                return;
            }

            if (principal.isVerified()) {
                message.thingId("device", principal.getDevice().getDeviceId());
                //发送上线消息给平台
                context.sendToPlatformLater(message);
                //应答设备
                ack(message, AckCode.ok, context);
                return;
            }

            //应答未授权
            ack(message, AckCode.noAuth, context);
        } else {
            //应答未授权
            ack(message, AckCode.noAuth, context);
        }
    }

    public static ByteBuf wrapByteByf(ByteBuf payload) {
        return Unpooled.wrappedBuffer(
            Unpooled.buffer().writeInt(payload.writerIndex()),
            payload);
    }

    private void ack(DeviceMessage source, AckCode code, BlockingMessageDecodeContext context) {
        if (source == null) {
            return;
        }
        AcknowledgeDeviceMessage message = new AcknowledgeDeviceMessage();
        message.addHeader(BinaryAcknowledgeDeviceMessage.codeHeader, code.name());
        message.setDeviceId(source.getDeviceId());
        message.setMessageId(source.getMessageId());
        message.setCode(code.name());
        message.setSuccess(code == AckCode.ok);

        source.getHeader(BinaryMessageType.HEADER_MSG_SEQ)
              .ifPresent(seq -> message.addHeader(BinaryMessageType.HEADER_MSG_SEQ, seq));

        context.sendToDeviceLater(EncodedMessage.simple(
            wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))
        ));

        if (source instanceof DeviceOnlineMessage && code != AckCode.ok) {
            context.disconnectLater();
        }

    }


}
