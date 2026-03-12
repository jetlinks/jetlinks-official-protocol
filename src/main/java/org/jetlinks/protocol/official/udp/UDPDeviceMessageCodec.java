package org.jetlinks.protocol.official.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
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
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.protocol.official.binary.*;
import org.jetlinks.protocol.official.TopicMessageCodec;
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

        if (logger().isDebugEnabled()) {
            logger().debug("收到设备UDP报文: {}", ByteBufUtil.hexDump(payload));
        }

        // 前面是token
        String token = (String) DataType.STRING.read(payload);

        // 接下来是消息
        DeviceMessage message = BinaryMessageType.read(payload);
        String deviceId = message.getDeviceId();

        if (logger(deviceId).isDebugEnabled()) {
            logger(deviceId).debug("获取设备ID，deviceId: {}", deviceId);
        }

        // 走平台的身份认证
        BlockingDevicePrincipal principal = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.auth, _span -> {
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    _span.setAttribute(DeviceTracer.SpanKey.message, "设备身份token认证");
                    _span.setAttribute(DeviceTracer.SpanKey.tag, "UDP直连");
                    
                    if (logger(deviceId).isDebugEnabled()) {
                        logger(deviceId).debug("开始获取设备凭证，deviceId：{}，token：{}", deviceId, token);
                    }
                    
                    BlockingDevicePrincipal _principal = context.resolveDevice(
                        Principal.create(
                            Identity.create(identityType, deviceId),
                            TokenCredential.create(token)
                        )
                    );
                    
                    if (_principal != null && _principal.isVerified()) {
                        _span.setAttribute(DeviceTracer.SpanKey.output, "认证成功");
                    } else {
                        _span.setAttribute(DeviceTracer.SpanKey.output, "认证失败");
                    }
                    
                    return _principal;
                });

        if (principal == null || !principal.isVerified()) {
            logger(deviceId).warn("设备认证失败");
            ack(message, AckCode.noAuth, context);
            return;
        }

        DeviceMessage processedMessage = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.decode, _span -> {
                    // 原始报文
                    _span.setAttribute(DeviceTracer.SpanKey.input, ByteBufUtil.hexDump(payload));
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据上报");

                    TopicMessageCodec codec = TopicMessageCodec.lookup(message.getClass());
                    if (codec != null) {
                        _span.setAttribute(DeviceTracer.SpanKey.tag, codec.getRoute().getGroup());
                    }
                    // 输出报文
                    _span.setAttribute(DeviceTracer.SpanKey.output, message.toJson().toString());
                    return message;
                });

        if (processedMessage != null) {
            logger(deviceId).info("解码完成, 消息内容：{}", processedMessage.toJson());
            context.sendToPlatformLater(processedMessage);
        } else {
            logger(deviceId).warn("解码结果消息为空");
        }
        ack(processedMessage, AckCode.ok, context);
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        BlockingDeviceOperator device = context.getDevice();
        DeviceMessage deviceMessage = context.getMessage();
        String deviceId = deviceMessage.getDeviceId();

        String key = device.getConfigNow(CONFIG_KEY_SECURE_KEY);

        EncodedMessage encodedMessage = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.encode, _span -> {
                    // 原始消息
                    _span.setAttribute(DeviceTracer.SpanKey.input, deviceMessage.toJson().toString());
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");
                    
                    TopicMessageCodec codec = TopicMessageCodec.lookup(deviceMessage.getClass());
                    if (codec != null) {
                        _span.setAttribute(DeviceTracer.SpanKey.tag, codec.getRoute().getGroup());
                    }
                    
                    EncodedMessage msg = doEncode(deviceMessage, key);
                    
                    // 输出报文
                    _span.setAttribute(DeviceTracer.SpanKey.output, ByteBufUtil.hexDump(msg.getPayload()));
                    return msg;
                });

        context.sendToDeviceLater(encodedMessage);
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
