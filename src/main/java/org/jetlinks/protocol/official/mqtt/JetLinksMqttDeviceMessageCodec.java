package org.jetlinks.protocol.official.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetlinks.core.Value;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.device.*;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DisconnectDeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.DeviceConfigScope;
import org.jetlinks.core.metadata.types.EnumType;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.principal.CredentialType;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.PasswordCredential;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.core.utils.TopicUtils;
import org.jetlinks.protocol.official.*;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 *     下行Topic:
 *          读取设备属性: /{productId}/{deviceId}/properties/read
 *          修改设备属性: /{productId}/{deviceId}/properties/write
 *          调用设备功能: /{productId}/{deviceId}/function/invoke
 *
 *          //网关设备
 *          读取子设备属性: /{productId}/{deviceId}/child/{childDeviceId}/properties/read
 *          修改子设备属性: /{productId}/{deviceId}/child/{childDeviceId}/properties/write
 *          调用子设备功能: /{productId}/{deviceId}/child/{childDeviceId}/function/invoke
 *
 *      上行Topic:
 *          读取属性回复: /{productId}/{deviceId}/properties/read/reply
 *          修改属性回复: /{productId}/{deviceId}/properties/write/reply
 *          调用设备功能: /{productId}/{deviceId}/function/invoke/reply
 *          上报设备事件: /{productId}/{deviceId}/event/{eventId}
 *          上报设备属性: /{productId}/{deviceId}/properties/report
 *          上报设备派生物模型: /{productId}/{deviceId}/metadata/derived
 *
 *          //网关设备
 *          子设备上线消息: /{productId}/{deviceId}/child/{childDeviceId}/connected
 *          子设备下线消息: /{productId}/{deviceId}/child/{childDeviceId}/disconnect
 *          读取子设备属性回复: /{productId}/{deviceId}/child/{childDeviceId}/properties/read/reply
 *          修改子设备属性回复: /{productId}/{deviceId}/child/{childDeviceId}/properties/write/reply
 *          调用子设备功能回复: /{productId}/{deviceId}/child/{childDeviceId}/function/invoke/reply
 *          上报子设备事件: /{productId}/{deviceId}/child/{childDeviceId}/event/{eventId}
 *          上报子设备派生物模型: /{productId}/{deviceId}/child/{childDeviceId}/metadata/derived
 *
 * </pre>
 * 基于jet links 的消息编解码器
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class JetLinksMqttDeviceMessageCodec extends BlockingDeviceMessageCodec implements Authenticator {

    public static final DefaultConfigMetadata mqttConfig = new DefaultConfigMetadata(
        "MQTT配置"
        , "")
        .add("mqttClientId", "clientId", "ClientId", new StringType())
        .scope(DeviceConfigScope.device);


    private final ObjectMapper mapper;

    public JetLinksMqttDeviceMessageCodec(ServiceContext context, Transport transport) {
        super(context, transport);
        this.mapper = ObjectMappers.JSON_MAPPER;
    }

    @Override
    protected boolean isInNonBlocking() {
        // 返回false,不使用单独的调度器来执行,减少线程切换的性能开销.
        // 因为在upstream和downstream中,都只使用了非阻塞方法(***Later),不会阻塞线程.
        return false;
    }

    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        String topic = ((MqttMessage) context.getData()).getTopic();

        byte[] payload = context.getData().payloadAsBytes();

        String[] topics = TopicMessageCodec.removeProductPath(topic);
        if (logger().isDebugEnabled()) {
            logger().debug("去除topic中的产品ID，原始topic：{}，输出topic：{}", topic, String.join("/", topics));
        }

        String deviceId = topics[1];
        if (logger(deviceId).isDebugEnabled()) {
            logger(deviceId).debug("获取设备ID，deviceId: {}", deviceId);
        }
        DeviceMessage msg = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.decode, _span -> {
                    // 原始报文
                    _span.setAttribute(DeviceTracer.SpanKey.input, new String(payload, StandardCharsets.UTF_8));
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据上报");

                    DeviceMessage _msg = TopicMessageCodec.decode(mapper, topics, payload, _span, logger(deviceId));

                    // 非平台消息,如 同步时间等topic.
                    if (_msg == null) {
                        logger(deviceId).warn("TopicMessageCodec解析结果为空，尝试作为功能性topic解析");
                        _msg = FunctionalTopicHandlers
                                .handle(
                                        context.getDevice(),
                                        TopicUtils.split(topic),
                                        payload,
                                        mapper,
                                        reply -> context
                                                .sendToDeviceLater(
                                                        SimpleMqttMessage
                                                                .builder()
                                                                .topic(reply.getTopic())
                                                                .payload(Unpooled.wrappedBuffer(reply.getPayload()))
                                                                .qosLevel(1)
                                                                .build()
                                                ));
                    }

                    if (_msg != null) {
                        // 输出报文
                        _span.setAttribute(DeviceTracer.SpanKey.output, _msg.toJson().toString());
                    }
                    return _msg;
                });

        //发送给平台
        if (msg != null) {
            logger(deviceId).info("解码完成, 消息内容：{}", msg.toJson());
            context.sendToPlatformLater(msg);
        } else {
            logger(deviceId).warn("解码结果消息为空");
        }
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        DeviceMessage deviceMessage = context.getMessage();
        String deviceId = deviceMessage.getDeviceId();
        //直接断开连接
        if (deviceMessage instanceof DisconnectDeviceMessage) {
            tracer(deviceId)
                    .traceBlocking(DeviceTracer.OperationName.encode, span -> {
                        span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                        span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");
                        span.setAttribute(DeviceTracer.SpanKey.input, deviceMessage.toJson().toJSONString());
                        span.setAttribute(DeviceTracer.SpanKey.tag, "平台主动断开连接");
                        context.disconnect();
                        return null;
                    });
            return;
        }

        TopicPayload convertResult = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.encode, _span -> {
                    // 原始消息
                    _span.setAttribute(DeviceTracer.SpanKey.input, deviceMessage.toJson().toString());
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");
                    return TopicMessageCodec.encode(mapper, deviceMessage, _span, logger(deviceId));
                });

        EncodedMessage encodedMessage = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.encode, _span -> {
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");

                    //获取产品ID
                    String productId = deviceMessage
                            .getHeader("productId")
                            .map(String::valueOf)
                            .orElseGet(() -> context.getDevice().getSelfConfigNow(DeviceConfigKey.productId));
                    if (logger(deviceId).isDebugEnabled()) {
                        logger(deviceId).debug("从消息header或设备缓存中获取产品ID：{}", productId);
                    }

                    EncodedMessage msg = SimpleMqttMessage
                            .builder()
                            //  /{产品ID} + 原始SQL
                            .topic("/".concat(productId).concat(convertResult.getTopic()))
                            .payload(Unpooled.wrappedBuffer(convertResult.getPayload()))
                            .qosLevel(1)
                            .build();
                    //  输出报文
                    _span.setAttribute(DeviceTracer.SpanKey.output, msg.payloadAsString());
                    return msg;
                });

        context.sendToDeviceLater(encodedMessage);

    }


    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        MqttAuthenticationRequest mqtt = ((MqttAuthenticationRequest) request);

        return registry
            .getDevice(mqtt.getClientId())
            .flatMap(device -> authenticate(request, device));
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator deviceOperation) {
        return Mono
                .defer(() -> {
                    if (request instanceof MqttAuthenticationRequest) {
                        MqttAuthenticationRequest mqtt = ((MqttAuthenticationRequest) request);
                        if (logger(deviceOperation.getDeviceId()).isDebugEnabled()) {
                            logger(deviceOperation.getDeviceId()).debug(
                                    "开始获取设备凭证，clientId：{}", mqtt.getClientId()
                            );
                        }
                        return deviceOperation
                                // 获取设备凭证
                                .getCredential(
                                        Identity.create(DefaultTransport.MQTT.getId(), mqtt.getClientId()),
                                        CredentialType.password
                                )
                                .map(cert -> {
                                    if (cert.isWrapperFor(PasswordCredential.class)) {
                                        if (logger(deviceOperation.getDeviceId()).isDebugEnabled()) {
                                            logger(deviceOperation.getDeviceId()).debug(
                                                    "校验用户名密码。username：{}，password：{}",
                                                    mqtt.getUsername(), mqtt.getPassword()
                                            );
                                        }

                                        PasswordCredential unwrap = cert.unwrap(PasswordCredential.class);
                                        // 简单比对.
                                        if (Objects.equals(unwrap.getUsername(), mqtt.getUsername())
                                                && Arrays.equals(unwrap.getPassword(), mqtt
                                                .getPassword()
                                                .toCharArray())) {
                                            return AuthenticationResponse.success(deviceOperation.getDeviceId());
                                        } else {
                                            return AuthenticationResponse.error(401, "用户名密码错误");
                                        }
                                    }
                                    return AuthenticationResponse.error(500, "身份配置错误");
                                });
                    }
                    return Mono.just(AuthenticationResponse.error(400, "不支持的授权类型:" + request));
                })
                .as(tracer(deviceOperation.getDeviceId())
                            .traceMono(DeviceTracer.OperationName.auth, (ctx, _span) -> {
                                _span.setAttribute(DeviceTracer.SpanKey.message, "设备身份用户名密码认证");
                                _span.setAttribute(DeviceTracer.SpanKey.tag, "MQTT直连");
                            }));

    }

}
