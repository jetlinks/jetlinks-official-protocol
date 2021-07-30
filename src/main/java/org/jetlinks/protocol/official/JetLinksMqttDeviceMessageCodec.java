package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.device.DeviceConfigKey;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DisconnectDeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.server.session.DeviceSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Map;

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
@Slf4j
public class JetLinksMqttDeviceMessageCodec implements DeviceMessageCodec {

    private final Transport transport;

    private final ObjectMapper mapper;

    public JetLinksMqttDeviceMessageCodec(Transport transport) {
        this.transport = transport;
        this.mapper = ObjectMappers.JSON_MAPPER;
    }

    public JetLinksMqttDeviceMessageCodec() {
        this(DefaultTransport.MQTT);
    }

    @Override
    public Transport getSupportTransport() {
        return transport;
    }

    @Nonnull
    public Mono<MqttMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.defer(() -> {
            Message message = context.getMessage();

            if (message instanceof DisconnectDeviceMessage) {
                return ((ToDeviceMessageContext) context)
                        .disconnect()
                        .then(Mono.empty());
            }

            if (message instanceof DeviceMessage) {
                DeviceMessage deviceMessage = ((DeviceMessage) message);

                TopicPayload convertResult = TopicMessageCodec.encode(mapper, deviceMessage);
                if (convertResult == null) {
                    return Mono.empty();
                }
                return Mono
                        .justOrEmpty(deviceMessage.getHeader("productId").map(String::valueOf))
                        .switchIfEmpty(context.getDevice(deviceMessage.getDeviceId())
                                              .flatMap(device -> device.getSelfConfig(DeviceConfigKey.productId))
                        )
                        .defaultIfEmpty("null")
                        .map(productId -> SimpleMqttMessage
                                .builder()
                                .clientId(deviceMessage.getDeviceId())
                                .topic("/".concat(productId).concat(convertResult.getTopic()))
                                .payloadType(MessagePayloadType.JSON)
                                .payload(Unpooled.wrappedBuffer(convertResult.getPayload()))
                                .build());
            } else {
                return Mono.empty();
            }
        });
    }

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        MqttMessage message = (MqttMessage) context.getMessage();
        byte[] payload = message.payloadAsBytes();

        return TopicMessageCodec
                .decode(mapper, TopicMessageCodec.removeProductPath(message.getTopic()), payload)
                //如果不能直接解码，可能是其他设备功能
                .switchIfEmpty(FunctionalTopicHandlers
                                       .handle(context.getDevice(),
                                               message.getTopic().split("/"),
                                               payload,
                                               mapper,
                                               reply -> doReply(context, reply)))
                ;

    }

    private Mono<Void> doReply(MessageCodecContext context, TopicPayload reply) {

        if (context instanceof FromDeviceMessageContext) {
            return ((FromDeviceMessageContext) context)
                    .getSession()
                    .send(SimpleMqttMessage
                                  .builder()
                                  .topic(reply.getTopic())
                                  .payload(reply.getPayload())
                                  .build())
                    .then();
        } else if (context instanceof ToDeviceMessageContext) {
            return ((ToDeviceMessageContext) context)
                    .sendToDevice(SimpleMqttMessage
                                          .builder()
                                          .topic(reply.getTopic())
                                          .payload(reply.getPayload())
                                          .build())
                    .then();
        }
        return Mono.empty();

    }

}
