package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.utils.TopicUtils;
import org.jetlinks.protocol.official.functional.TimeSyncRequest;
import org.jetlinks.protocol.official.functional.TimeSyncResponse;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * 功能性的topic,不和平台交互
 */
public enum FunctionalTopicHandlers {

    //同步时间
    timeSync("/*/*/time-sync") {
        @SneakyThrows
        @SuppressWarnings("all")
        Mono<DeviceMessage> doHandle(DeviceOperator device,
                                          String[] topic,
                                          byte[] payload,
                                          ObjectMapper mapper,
                                          Function<TopicPayload, Mono<Void>> sender) {
            TopicPayload topicPayload = new TopicPayload();
            topicPayload.setTopic(String.join("/", topic) + "/reply");
            TimeSyncRequest msg = mapper.readValue(payload, TimeSyncRequest.class);
            TimeSyncResponse response = TimeSyncResponse.of(msg.getMessageId(), System.currentTimeMillis());
            topicPayload.setPayload(mapper.writeValueAsBytes(response));
            //直接回复给设备
            return sender
                    .apply(topicPayload)
                    .then(Mono.empty());
        }
    };

    FunctionalTopicHandlers(String topic) {
        this.pattern = topic.split("/");
    }

    private final String[] pattern;

    abstract Publisher<DeviceMessage> doHandle(DeviceOperator device,
                                               String[] topic,
                                               byte[] payload,
                                               ObjectMapper mapper,
                                               Function<TopicPayload, Mono<Void>> sender);


    public static Publisher<DeviceMessage> handle(DeviceOperator device,
                                                  String[] topic,
                                                  byte[] payload,
                                                  ObjectMapper mapper,
                                                  Function<TopicPayload, Mono<Void>> sender) {
        return Mono
                .justOrEmpty(fromTopic(topic))
                .flatMapMany(handler -> handler.doHandle(device, topic, payload, mapper, sender));
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
