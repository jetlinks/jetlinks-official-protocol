package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.route.MqttRoute;
import org.jetlinks.core.utils.TopicUtils;
import org.jetlinks.protocol.official.functional.TimeSyncRequest;
import org.jetlinks.protocol.official.functional.TimeSyncResponse;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 功能性的topic,不和平台交互
 */
public enum FunctionalTopicHandlers {

    //同步时间
    timeSync("/*/*/time-sync") {
        @Override
        public MqttRoute getRoute() {
            return MqttRoute
                .builder("/{productId:产品ID}/{deviceId:设备ID}/time-sync")
                .upstream(true)
                .group("时间同步")
                .example("{\"messageId\":\"1\"}")
                .qos(1)
                .build();
        }

        @SneakyThrows
        @SuppressWarnings("all")
        DeviceMessage doHandle(DeviceOperator device,
                               String[] topic,
                               byte[] payload,
                               ObjectMapper mapper,
                               Consumer<TopicPayload> sender) {
            TopicPayload topicPayload = new TopicPayload();
            topicPayload.setTopic(String.join("/", topic) + "/reply");
            TimeSyncRequest msg = mapper.readValue(payload, TimeSyncRequest.class);
            TimeSyncResponse response = TimeSyncResponse.of(msg.getMessageId(), System.currentTimeMillis());
            topicPayload.setPayload(mapper.writeValueAsBytes(response));
            //直接回复给设备
            sender.accept(topicPayload);
            return null;
        }
    },
    //同步时间
    timeSyncReply("/*/*/time-sync/reply") {
        @Override
        public MqttRoute getRoute() {
            return MqttRoute
                .builder("/{productId:产品ID}/{deviceId:设备ID}/time-sync/reply")
                .downstream(true)
                .group("时间同步")
                .example("{\"messageId\":\"1\",\"timestamp\":123456789}")
                .qos(1)
                .build();
        }

        @SneakyThrows
        @SuppressWarnings("all")
        DeviceMessage doHandle(DeviceOperator device,
                               String[] topic,
                               byte[] payload,
                               ObjectMapper mapper,
                               Consumer<TopicPayload> sender) {
            return null;
        }
    };;

    FunctionalTopicHandlers(String topic) {
        this.pattern = topic.split("/");
    }

    public abstract MqttRoute getRoute();

    private final String[] pattern;

    abstract DeviceMessage doHandle(DeviceOperator device,
                                    String[] topic,
                                    byte[] payload,
                                    ObjectMapper mapper,
                                    Consumer<TopicPayload> sender);


    public static DeviceMessage handle(DeviceOperator device,
                                       String[] topic,
                                       byte[] payload,
                                       ObjectMapper mapper,
                                       Consumer<TopicPayload> sender) {
        FunctionalTopicHandlers handler = fromTopic(topic).orElse(null);
        if (handler != null) {
            return handler.doHandle(device, topic, payload, mapper, sender);
        }

        return null;
    }

    static Optional<FunctionalTopicHandlers> fromTopic(String[] topic) {
        for (FunctionalTopicHandlers value : values()) {
            if (TopicUtils.match(value.pattern, topic)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
