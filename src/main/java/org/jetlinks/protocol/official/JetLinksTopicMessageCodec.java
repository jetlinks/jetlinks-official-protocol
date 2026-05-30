package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.collector.*;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.firmware.*;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.module.DeviceModuleMessage;
import org.jetlinks.core.message.property.*;
import org.jetlinks.core.message.state.DeviceStateCheckMessage;
import org.jetlinks.core.message.state.DeviceStateCheckMessageReply;
import org.jetlinks.core.route.MqttRoute;
import org.jetlinks.core.utils.TopicUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public enum JetLinksTopicMessageCodec {
    //上报属性数据
    reportProperty("/properties/report",
                   ReportPropertyMessage.class,
                   route -> route
                       .upstream(true)
                       .downstream(false)
                       .group("属性上报")
                       .description("上报物模型属性数据")
                       .example("{\"properties\":{\"属性ID\":\"属性值\"}}")),
    //读取属性
    readProperty("/properties/read",
                 ReadPropertyMessage.class,
                 route -> route
                     .upstream(false)
                     .downstream(true)
                     .group("读取属性")
                     .description("平台下发读取物模型属性数据指令")
                     .example("{\"messageId\":\"消息ID,回复时需要一致.\",\"properties\":[\"属性ID\"]}")),
    //读取属性回复
    readPropertyReply("/properties/read/reply",
                      ReadPropertyMessageReply.class,
                      route -> route
                          .upstream(true)
                          .downstream(false)
                          .group("读取属性")
                          .description("对平台下发的读取属性指令进行响应")
                          .example("{\"messageId\":\"消息ID,与读取指令中的ID一致.\",\"properties\":{\"属性ID\":\"属性值\"}}")),
    //修改属性
    writeProperty("/properties/write",
                  WritePropertyMessage.class,
                  route -> route
                      .upstream(false)
                      .downstream(true)
                      .group("修改属性")
                      .description("平台下发修改物模型属性数据指令")
                      .example("{\"messageId\":\"消息ID,回复时需要一致.\",\"properties\":{\"属性ID\":\"属性值\"}}")),
    //修改属性回复
    writePropertyReply("/properties/write/reply",
                       WritePropertyMessageReply.class,
                       route -> route
                           .upstream(true)
                           .downstream(false)
                           .group("修改属性")
                           .description("对平台下发的修改属性指令进行响应")
                           .example("{\"messageId\":\"消息ID,与修改指令中的ID一致.\",\"properties\":{\"属性ID\":\"属性值\"}}")),
    //事件上报
    event("/event/*",
          EventMessage.class,
          route -> route
              .upstream(true)
              .downstream(false)
              .group("事件上报")
              .description("上报物模型事件数据")
              .example("{\"data\":{\"key\":\"value\"}}")) {
        @Override
        protected void transMqttTopic(String[] topic) {
            topic[topic.length - 1] = "{eventId:事件ID}";
        }

        @Override
        DeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            String event = topic[topic.length - 1];

            EventMessage message = (EventMessage) super.doDecode(mapper, topic, payload);
            message.setEvent(event);
            return message;
        }

        @Override
        void refactorTopic(String[] topics, DeviceMessage message) {
            super.refactorTopic(topics, message);
            EventMessage event = ((EventMessage) message);
            topics[topics.length - 1] = String.valueOf(event.getEvent());
        }
    },

    //调用功能
    functionInvoke("/function/invoke",
                   FunctionInvokeMessage.class,
                   route -> route
                       .upstream(false)
                       .downstream(true)
                       .group("调用功能")
                       .description("平台下发功能调用指令")
                       .example("{\"messageId\":\"消息ID,回复时需要一致.\"," +
                                    "\"functionId\":\"功能标识\"," +
                                    "\"inputs\":[{\"name\":\"参数名\",\"value\":\"参数值\"}]}")),
    //调用功能回复
    functionInvokeReply("/function/invoke/reply",
                        FunctionInvokeMessageReply.class,
                        route -> route
                            .upstream(true)
                            .downstream(false)
                            .group("调用功能")
                            .description("设备响应平台下发的功能调用指令")
                            .example("{\"messageId\":\"消息ID,与下发指令中的messageId一致.\"," +
                                         "\"output\":\"输出结果,格式与物模型中定义的类型一致\"")),
    //子设备消息
    child("/child/*/**",
          ChildDeviceMessage.class,
          route -> route
              .upstream(true)
              .downstream(true)
              .group("子设备消息")
              .description("网关上报或者平台下发子设备消息")) {
        @Override
        protected void transMqttTopic(String[] topic) {
            topic[topic.length - 1] = "{#:子设备相应操作的topic}";
            topic[topic.length - 2] = "{childDeviceId:子设备ID}";
        }

        @Override
        public DeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            // /child/{childDeviceId}/{topic...}
            DeviceMessage childMsg = decodeWrappedInner(mapper, topic, payload, 2);
            if (childMsg != null) {
                ChildDeviceMessage msg = new ChildDeviceMessage();
                msg.setDeviceId(topic[1]);
                msg.setChildDeviceMessage(childMsg);
                msg.setTimestamp(childMsg.getTimestamp());
                msg.setMessageId(childMsg.getMessageId());
                return msg;
            }
            return null;
        }

        @Override
        protected TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
            ChildDeviceMessage deviceMessage = ((ChildDeviceMessage) message);

            DeviceMessage childMessage = (DeviceMessage) deviceMessage.getChildDeviceMessage();

            TopicPayload payload = JetLinksTopicMessageCodec.encode(mapper, childMessage);
            String[] childTopic = payload.getTopic().split("/");
            // childTopic[0] is ""
            // childTopic: ["", "properties", "report"]

            // topics: ["", "child", "{childDeviceId}", "{topic...}"]
            // 我们需要构建 ["", "child", childDeviceId, "properties", "report"]

            String[] topic = new String[2 + childTopic.length - 1];
            topic[0] = "";
            topic[1] = "child";
            topic[2] = childMessage.getDeviceId(); // 默认使用子设备ID
            System.arraycopy(childTopic, 1, topic, 3, childTopic.length - 1);

            // 如果ChildDeviceMessage中有设置子设备ID, 则覆盖之
            if(deviceMessage.getChildDeviceId() != null){
                topic[2] = deviceMessage.getChildDeviceId();
            }

            payload.setTopic(String.join("/", topic));
            return payload;
        }
    },
    //设备模块消息
    module("/module/*/**",
           DeviceModuleMessage.class,
           route -> route
               .upstream(true)
               .downstream(true)
               .group("设备模块消息")
               .description("设备模块上报或者平台下发模块消息")) {
        @Override
        protected void transMqttTopic(String[] topic) {
            topic[topic.length - 1] = "{#:模块相应操作的topic}";
            topic[topic.length - 2] = "{module:模块编码}";
        }

        @Override
        public DeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            if (topic.length < 4) {
                return null;
            }
            DeviceMessage inner = decodeWrappedInner(mapper, topic, payload, 3);
            if (inner != null) {
                DeviceModuleMessage msg = new DeviceModuleMessage();
                msg.module(topic[2]);
                msg.message(inner);
                msg.setTimestamp(inner.getTimestamp());
                msg.setMessageId(inner.getMessageId());
                return msg;
            }
            return null;
        }

        @Override
        protected TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
            DeviceModuleMessage deviceMessage = ((DeviceModuleMessage) message);
            if (!(deviceMessage.getMessage() instanceof DeviceMessage)) {
                throw new UnsupportedOperationException("unsupported module message:" + deviceMessage.getMessage());
            }
            DeviceMessage inner = (DeviceMessage) deviceMessage.getMessage();
            TopicPayload payload = JetLinksTopicMessageCodec.encode(mapper, inner);
            String[] innerTopic = payload.getTopic().split("/");

            String[] topic = new String[innerTopic.length + 2];
            topic[0] = "";
            topic[1] = "module";
            topic[2] = deviceMessage.getModule();
            System.arraycopy(innerTopic, 1, topic, 3, innerTopic.length - 1);

            payload.setTopic(String.join("/", topic));
            return payload;
        }
    }, //子设备消息回复
    childReply("/child-reply/*/**",
               ChildDeviceMessageReply.class,
               route -> route
                   .upstream(true)
                   .downstream(true)
                   .group("子设备消息")
                   .description("网关回复平台下发给子设备的指令结果")) {
        @Override
        protected void transMqttTopic(String[] topic) {
            topic[topic.length - 1] = "{#:子设备相应操作的topic}";
            topic[topic.length - 2] = "{childDeviceId:子设备ID}";
        }

        @Override
        public DeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            DeviceMessage childMsg = decodeWrappedInner(mapper, topic, payload, 2);
            if (childMsg != null) {
                ChildDeviceMessageReply msg = new ChildDeviceMessageReply();
                msg.setDeviceId(topic[1]);
                msg.setChildDeviceMessage(childMsg);
                msg.setTimestamp(childMsg.getTimestamp());
                msg.setMessageId(childMsg.getMessageId());
                return msg;
            }
            return null;
        }

        @Override
        protected TopicPayload doEncode(ObjectMapper mapper, String[] topics, DeviceMessage message) {
            ChildDeviceMessageReply deviceMessage = ((ChildDeviceMessageReply) message);

            DeviceMessage childMessage = (DeviceMessage) deviceMessage.getChildDeviceMessage();

            TopicPayload payload = JetLinksTopicMessageCodec.encode(mapper, childMessage);
            String[] childTopic = payload.getTopic().split("/");

            String[] topic = new String[2 + childTopic.length - 1];
            topic[0] = "";
            topic[1] = "child-reply";
            topic[2] = childMessage.getDeviceId();
            System.arraycopy(childTopic, 1, topic, 3, childTopic.length - 1);

            if(deviceMessage.getChildDeviceId() != null){
                topic[2] = deviceMessage.getChildDeviceId();
            }

            payload.setTopic(String.join("/", topic));
            return payload;
        }
    },
    //更新标签
    updateTag("/tags",
              UpdateTagMessage.class,
              route -> route.upstream(true)
                            .downstream(false)
                            .group("更新标签")
                            .description("更新标签数据")
                            .example("{\"tags\":{\"key\",\"value\"}}")),
//    //注册
//    register("/register", DeviceRegisterMessage.class),
//    //注销
//    unregister("/unregister", DeviceUnRegisterMessage.class),
    //更新固件消息
    upgradeFirmware("/firmware/upgrade", UpgradeFirmwareMessage.class),
    //更新固件消息回复
    upgradeFirmwareReply("/firmware/upgrade/reply", UpgradeFirmwareMessageReply.class),
    //更新固件升级进度消息
    upgradeProcessFirmware("/firmware/upgrade/progress", UpgradeFirmwareProgressMessage.class),
    //拉取固件
    requestFirmware("/firmware/pull", RequestFirmwareMessage.class),
    //拉取固件更新回复
    requestFirmwareReply("/firmware/pull/reply", RequestFirmwareMessageReply.class),
    //上报固件版本
    reportFirmware("/firmware/report", ReportFirmwareMessage.class),
    //读取固件回复
    readFirmware("/firmware/read", ReadFirmwareMessage.class),
    //读取固件回复
    readFirmwareReply("/firmware/read/reply", ReadFirmwareMessageReply.class),
    //派生物模型上报
    derivedMetadata("/metadata/derived", DerivedMetadataMessage.class),
    //透传设备消息
    direct("/direct", DirectDeviceMessage.class) {
        @Override
        public DirectDeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
            DirectDeviceMessage message = new DirectDeviceMessage();
            message.setPayload(payload);
            return message;
        }
    },
    //断开连接消息
    disconnect("/disconnect", DisconnectDeviceMessage.class),
    //断开连接回复
    disconnectReply("/disconnect/reply", DisconnectDeviceMessageReply.class),
    //上线
    online("/online", DeviceOnlineMessage.class, builder -> builder
        .upstream(true)
        .group("状态管理")
        .description("设备上线")),
    //离线
    offline("/offline", DeviceOfflineMessage.class, builder -> builder
        .upstream(true)
        .group("状态管理")
        .description("设备离线")),
    //日志
    log("/log", DeviceLogMessage.class),
    //状态检查
    stateCheck("/state-check", DeviceStateCheckMessage.class),
    stateCheckReply("/state-check/reply", DeviceStateCheckMessageReply.class),

    //数采相关
    collector("/collector/report", ReportCollectorDataMessage.class
        , builder -> builder
        .upstream(true)
        .group("数采网关")
        .description("上报数采点位数据")),
    collectorRead("/collector/read",
                  ReadCollectorDataMessage.class,
                  builder -> builder
                      .downstream(true)
                      .group("数采网关")
                      .description("平台读取点位数据")),
    collectorReadReply("/collector/read/reply",
                       ReadCollectorDataMessageReply.class,
                       builder -> builder
                           .upstream(true)
                           .group("数采网关")
                           .description("平台读取点位数据结果回复")),
    collectorWrite("/collector/write", WriteCollectorDataMessage.class,
                   builder -> builder
                       .downstream(true)
                       .group("数采网关")
                       .description("平台修改点位数据")),
    collectorWriteReply("/collector/write/reply", WriteCollectorDataMessageReply.class,
                        builder -> builder
                            .upstream(true)
                            .group("数采网关")
                            .description("平台修改点位数据结果回复")),
    ;

    JetLinksTopicMessageCodec(String topic,
                              Class<? extends DeviceMessage> type,
                              Function<MqttRoute.Builder, MqttRoute.Builder> routeCustom) {
        this.pattern = topic.split("/");
        this.type = type;
        this.route = routeCustom.apply(toRoute()).build();
    }

    JetLinksTopicMessageCodec(String topic,
                              Class<? extends DeviceMessage> type) {
        this.pattern = topic.split("/");
        this.type = type;
        this.route = null;
    }

    private final String[] pattern;
    private final MqttRoute route;
    private final Class<? extends DeviceMessage> type;

    protected void transMqttTopic(String[] topic) {

    }

    @SneakyThrows
    private MqttRoute.Builder toRoute() {
        String[] topics = new String[pattern.length];
        System.arraycopy(pattern, 0, topics, 0, pattern.length);
        transMqttTopic(topics);
        StringJoiner joiner = new StringJoiner("/", "/", "");
        for (String topic : topics) {
            if(!topic.isEmpty()){
               joiner.add(topic);
            }
        }
        return MqttRoute
            .builder(joiner.toString())
            .qos(1);
    }

    public MqttRoute getRoute() {
        return route;
    }

    public static DeviceMessage decode(ObjectMapper mapper, String[] topics, byte[] payload) {
        JetLinksTopicMessageCodec codec = fromTopic(topics).orElse(null);

        if (codec != null) {
            return codec.doDecode(mapper, topics, payload);
        }

        return null;
    }

    public static DeviceMessage decode(ObjectMapper mapper, String topic, byte[] payload) {
        return decode(mapper, topic.split("/"), payload);
    }

    public static TopicPayload encode(ObjectMapper mapper, DeviceMessage message) {

        return fromMessage(message)
            .orElseThrow(() -> new UnsupportedOperationException("unsupported message:" + message.getMessageType()))
            .doEncode(mapper, message);
    }

    static Optional<JetLinksTopicMessageCodec> fromTopic(String[] topic) {
        for (JetLinksTopicMessageCodec value : values()) {
            if (TopicUtils.match(value.pattern, topic)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    static Optional<JetLinksTopicMessageCodec> fromMessage(DeviceMessage message) {
        for (JetLinksTopicMessageCodec value : values()) {
            if (value.type == message.getClass()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }


    private static DeviceMessage decodeWrappedInner(ObjectMapper mapper, String[] topic, byte[] payload, int innerStart) {
        String[] inner = Arrays.copyOfRange(topic, innerStart, topic.length);
        String[] topicToDecode = new String[inner.length + 1];
        topicToDecode[0] = "";
        System.arraycopy(inner, 0, topicToDecode, 1, inner.length);
        return JetLinksTopicMessageCodec.decode(mapper, topicToDecode, payload);
    }

    @SneakyThrows
    DeviceMessage doDecode(ObjectMapper mapper, String[] topic, byte[] payload) {
        return mapper.readValue(payload, type);
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
    }

}
