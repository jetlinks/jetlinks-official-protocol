package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.firmware.*;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.*;
import org.jetlinks.core.message.state.DeviceStateCheckMessage;
import org.jetlinks.core.message.state.DeviceStateCheckMessageReply;
import org.jetlinks.core.utils.TopicUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

public enum TopicMessageCodec {
    //上报属性数据
    reportProperty("/*/properties/report", ReportPropertyMessage.class),
    //事件上报
    event("/*/event/*", EventMessage.class),
    //读取属性
    readProperty("/*/properties/read", ReadPropertyMessage.class),
    //读取属性回复
    readPropertyReply("/*/properties/read/reply", ReadPropertyMessageReply.class),
    //修改属性
    writeProperty("/*/properties/write", WritePropertyMessage.class),
    //修改属性回复
    writePropertyReply("/*/properties/write/reply", WritePropertyMessageReply.class),
    //调用功能
    functionInvoke("/*/function/invoke", FunctionInvokeMessage.class),
    //调用功能回复
    functionInvokeReply("/*/function/invoke/reply", FunctionInvokeMessageReply.class),
    //子设备消息
    child("/*/child/*/**", ChildDeviceMessage.class) {
        @Override
        public Publisher<DeviceMessage> doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            String[] _topic = Arrays.copyOfRange(topic, 2, topic.length);
            _topic[0] = "";// topic以/开头所有第一位是空白
            return TopicMessageCodec
                    .decode(mapper, _topic, payload)
                    .map(childMsg -> {
                        ChildDeviceMessage msg = new ChildDeviceMessage();
                        msg.setDeviceId(topic[1]);
                        msg.setChildDeviceMessage(childMsg);
                        msg.setTimestamp(childMsg.getTimestamp());
                        msg.setMessageId(childMsg.getMessageId());
                        return msg;
                    });
        }

        @Override
        protected TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
            ChildDeviceMessage deviceMessage = ((ChildDeviceMessage) message);

            DeviceMessage childMessage = ((DeviceMessage) deviceMessage.getChildDeviceMessage());

            TopicPayload payload = TopicMessageCodec.encode(mapper, childMessage);
            String[] childTopic = payload.getTopic().split("/");
            String[] topic = new String[topics.length + childTopic.length - 3];
            //合并topic
            System.arraycopy(topics, 0, topic, 0, topics.length - 1);
            System.arraycopy(childTopic, 1, topic, topics.length - 2, childTopic.length - 1);

            refactorTopic(topic, message);
            payload.setTopic(String.join("/", topic));
            return payload;

        }
    }, //子设备消息回复
    childReply("/*/child-reply/*/**", ChildDeviceMessageReply.class) {
        @Override
        public Publisher<DeviceMessage> doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            String[] _topic = Arrays.copyOfRange(topic, 2, topic.length);
            _topic[0] = "";// topic以/开头所有第一位是空白
            return TopicMessageCodec
                    .decode(mapper, _topic, payload)
                    .map(childMsg -> {
                        ChildDeviceMessageReply msg = new ChildDeviceMessageReply();
                        msg.setDeviceId(topic[1]);
                        msg.setChildDeviceMessage(childMsg);
                        msg.setTimestamp(childMsg.getTimestamp());
                        msg.setMessageId(childMsg.getMessageId());
                        return msg;
                    });
        }

        @Override
        protected TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
            ChildDeviceMessageReply deviceMessage = ((ChildDeviceMessageReply) message);

            DeviceMessage childMessage = ((DeviceMessage) deviceMessage.getChildDeviceMessage());

            TopicPayload payload = TopicMessageCodec.encode(mapper, childMessage);
            String[] childTopic = payload.getTopic().split("/");
            String[] topic = new String[topics.length + childTopic.length - 3];
            //合并topic
            System.arraycopy(topics, 0, topic, 0, topics.length - 1);
            System.arraycopy(childTopic, 1, topic, topics.length - 2, childTopic.length - 1);

            refactorTopic(topic, message);
            payload.setTopic(String.join("/", topic));
            return payload;

        }
    },
    //更新标签
    updateTag("/*/tags", UpdateTagMessage.class),
    //注册
    register("/*/register", DeviceRegisterMessage.class),
    //注销
    unregister("/*/unregister", DeviceUnRegisterMessage.class),
    //更新固件消息
    upgradeFirmware("/*/firmware/upgrade", UpgradeFirmwareMessage.class),
    //更新固件升级进度消息
    upgradeProcessFirmware("/*/firmware/upgrade/progress", UpgradeFirmwareProgressMessage.class),
    //拉取固件
    requestFirmware("/*/firmware/pull", RequestFirmwareMessage.class),
    //拉取固件更新回复
    requestFirmwareReply("/*/firmware/pull/reply", RequestFirmwareMessageReply.class),
    //上报固件版本
    reportFirmware("/*/firmware/report", ReportFirmwareMessage.class),
    //读取固件回复
    readFirmware("/*/firmware/read", ReadFirmwareMessage.class),
    //读取固件回复
    readFirmwareReply("/*/firmware/read/reply", ReadFirmwareMessageReply.class),
    //派生物模型上报
    derivedMetadata("/*/metadata/derived", DerivedMetadataMessage.class),
    //透传设备消息
    direct("/*/direct", DirectDeviceMessage.class) {
        @Override
        public Publisher<DeviceMessage> doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            DirectDeviceMessage message = new DirectDeviceMessage();
            message.setDeviceId(topic[1]);
            message.setPayload(payload);
            return Mono.just(message);
        }
    },
    //断开连接消息
    disconnect("/*/disconnect", DisconnectDeviceMessage.class),
    //断开连接回复
    disconnectReply("/*/disconnect/reply", DisconnectDeviceMessageReply.class),
    //上线
    connect("/*/online", DeviceOnlineMessage.class),
    //离线
    offline("/*/offline", DeviceOfflineMessage.class),
    //日志
    log("/*/log", DeviceLogMessage.class),
    //状态检查
    stateCheck("/*/state-check", DeviceStateCheckMessage.class),
    stateCheckReply("/*/state-check/reply", DeviceStateCheckMessageReply.class),
    ;

    TopicMessageCodec(String topic, Class<? extends DeviceMessage> type) {
        this.pattern = topic.split("/");
        this.type = type;
    }

    private final String[] pattern;
    private final Class<? extends DeviceMessage> type;

    public static Flux<DeviceMessage> decode(ObjectMapper mapper, String[] topics, byte[] payload) {
        return Mono
                .justOrEmpty(fromTopic(topics))
                .flatMapMany(topicMessageCodec -> topicMessageCodec.doDecode(mapper, topics, payload));
    }

    public static Flux<DeviceMessage> decode(ObjectMapper mapper, String topic, byte[] payload) {
        return decode(mapper, topic.split("/"), payload);
    }

    public static TopicPayload encode(ObjectMapper mapper, DeviceMessage message) {

        return fromMessage(message)
                .orElseThrow(() -> new UnsupportedOperationException("unsupported message:" + message.getMessageType()))
                .doEncode(mapper, message);
    }

    static Optional<TopicMessageCodec> fromTopic(String[] topic) {
        for (TopicMessageCodec value : values()) {
            if (TopicUtils.match(value.pattern, topic)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    static Optional<TopicMessageCodec> fromMessage(DeviceMessage message) {
        for (TopicMessageCodec value : values()) {
            if (value.type == message.getClass()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    Publisher<DeviceMessage> doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
        return Mono
                .fromCallable(() -> {
                    DeviceMessage message = mapper.readValue(payload, type);
                    FastBeanCopier.copy(Collections.singletonMap("deviceId", topic[1]), message);

                    return message;
                });
    }

    @SneakyThrows
    TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
        refactorTopic(topics, message);
        return TopicPayload.of(String.join("/", topics), mapper.writeValueAsBytes(message));
    }

    @SneakyThrows
    TopicPayload doEncode(ObjectMapper mapper, DeviceMessage message) {
        String[] topics = Arrays.copyOf(pattern, pattern.length);
        return doEncode(mapper, topics, message);
    }

    void refactorTopic(String[] topics, DeviceMessage message) {
        topics[1] = message.getDeviceId();
    }

    /**
     * 移除topic中的产品信息,topic第一个层为产品ID，在解码时,不需要此信息,所以需要移除之.
     *
     * @param topic topic
     * @return 移除后的topic
     */
    public static String[] removeProductPath(String topic) {
        if (!topic.startsWith("/")) {
            topic = "/" + topic;
        }
        String[] topicArr = topic.split("/");
        String[] topics = Arrays.copyOfRange(topicArr, 1, topicArr.length);
        topics[0] = "";
        return topics;
    }

}
