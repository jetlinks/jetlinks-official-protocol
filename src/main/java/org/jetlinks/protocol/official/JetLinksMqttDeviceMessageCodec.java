package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.device.DeviceConfigKey;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

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
 *          子设备下线消息: /{productId}/{deviceId}/child/{childDeviceId}/disconnected
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
public class JetLinksMqttDeviceMessageCodec extends JetlinksTopicMessageCodec implements DeviceMessageCodec {

    private Transport transport;


    public JetLinksMqttDeviceMessageCodec(Transport transport) {
        this.transport = transport;
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
            if (message instanceof DeviceMessage) {
                DeviceMessage deviceMessage = ((DeviceMessage) message);

                EncodedTopic convertResult = encode(deviceMessage.getDeviceId(), deviceMessage);
                if (convertResult == null) {
                    return Mono.empty();
                }
                return context.getDevice()
                        .getConfig(DeviceConfigKey.productId)
                        .defaultIfEmpty("null")
                        .map(productId -> SimpleMqttMessage.builder()
                                .clientId(deviceMessage.getDeviceId())
                                .topic("/" .concat(productId).concat(convertResult.topic))
                                .payloadType(MessagePayloadType.JSON)
                                .payload(Unpooled.wrappedBuffer(JSON.toJSONBytes(convertResult.payload)))
                                .build());
            } else {
                return Mono.empty();
            }
        });
    }

    @Nonnull
    @Override
    public Mono<Message> decode(@Nonnull MessageDecodeContext context) {
        return Mono.fromSupplier(() -> {
            MqttMessage message = (MqttMessage) context.getMessage();
            String topic = message.getTopic();
            String jsonData = message.getPayload().toString(StandardCharsets.UTF_8);

            JSONObject object = JSON.parseObject(jsonData, JSONObject.class);
            if (object == null) {
                throw new UnsupportedOperationException("cannot parse payload:{}" + jsonData);
            }
            return decode(topic, object).getMessage();
        });
    }

}
