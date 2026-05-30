package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetlinks.core.message.ChildDeviceMessage;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.module.DeviceModuleMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.core.message.property.ReadPropertyMessageReply;
import org.jetlinks.core.message.property.WritePropertyMessage;
import org.jetlinks.core.message.property.WritePropertyMessageReply;
import org.jetlinks.core.route.Route;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.Assert.*;

public class TopicMessageCodecTest {


    public void testChild(ObjectMapper objectMapper) {
        ChildDeviceMessage message = new ChildDeviceMessage();
        message.setDeviceId("test");
        ReportPropertyMessage msg = new ReportPropertyMessage();
        msg.setDeviceId("childId");
        message.setChildDeviceMessage(msg);
        message.setTimestamp(msg.getTimestamp());


        TopicPayload payload = TopicMessageCodec.child.doEncode(objectMapper, message);
        System.out.println(payload.getPayload().length);
        assertEquals("/test/child/childId/properties/report", payload.getTopic());

        Mono.justOrEmpty(
                TopicMessageCodec
                    .decode(objectMapper, payload.getTopic(), payload.getPayload())
            )
                .as(StepVerifier::create)
                .expectNextMatches(deviceMessage -> {
                    System.out.println(message);
                    System.out.println(deviceMessage);
                    return deviceMessage.toJson().equals(message.toJson());
                })
                .verifyComplete();

    }

    @Test
    public void testRoute() {
        for (TopicMessageCodec value : TopicMessageCodec.values()) {
            Route route = value.getRoute();
            if (null != route)
                System.out.println(route.getAddress());
        }
    }

    @Test
    public void doTest() {
        testChild(ObjectMappers.JSON_MAPPER);
        testChild(ObjectMappers.CBOR_MAPPER);
    }

    @Test
    public void testEvent() {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setEvent("test");
        eventMessage.setDeviceId("test-device");
        eventMessage.setData("123");

        TopicPayload payload = TopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, eventMessage);
        assertEquals(payload.getTopic(), "/test-device/event/test");


        DeviceMessage msg = TopicMessageCodec
                .decode(ObjectMappers.JSON_MAPPER, payload.getTopic(), payload.getPayload());
        assertEquals(msg.toJson(), eventMessage.toJson());
    }

    @Test
    public void testModuleReadWriteAndFunction() {
        ReadPropertyMessage read = new ReadPropertyMessage();
        read.deviceId("test-device");
        read.messageId("msg-read");
        read.addProperties("temperature");

        DeviceModuleMessage readModule = moduleMessage("board", read, "msg-read");
        TopicPayload readPayload = JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, readModule);
        assertEquals("/module/board/properties/read", readPayload.getTopic());
        DeviceModuleMessage decodedRead = (DeviceModuleMessage) JetLinksTopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, readPayload.getTopic(), readPayload.getPayload());
        assertEquals("board", decodedRead.getModule());
        assertEquals("msg-read", decodedRead.getMessageId());
        assertTrue(decodedRead.getMessage() instanceof ReadPropertyMessage);
        assertEquals("msg-read", ((ReadPropertyMessage) decodedRead.getMessage()).getMessageId());

        WritePropertyMessage write = new WritePropertyMessage();
        write.deviceId("test-device");
        write.messageId("msg-write");
        write.setProperties(Map.of("threshold", 80));
        TopicPayload writePayload = JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, moduleMessage("board", write, "msg-write"));
        assertEquals("/module/board/properties/write", writePayload.getTopic());
        DeviceModuleMessage decodedWrite = (DeviceModuleMessage) JetLinksTopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, writePayload.getTopic(), writePayload.getPayload());
        assertTrue(decodedWrite.getMessage() instanceof WritePropertyMessage);
        assertEquals(80, ((Number) ((WritePropertyMessage) decodedWrite.getMessage()).getProperties().get("threshold")).intValue());

        FunctionInvokeMessage function = new FunctionInvokeMessage();
        function.deviceId("test-device");
        function.messageId("msg-fn");
        function.functionId("restart");
        function.addInput("mode", "soft");
        TopicPayload functionPayload = JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, moduleMessage("board", function, "msg-fn"));
        assertEquals("/module/board/function/invoke", functionPayload.getTopic());
        DeviceModuleMessage decodedFunction = (DeviceModuleMessage) JetLinksTopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, functionPayload.getTopic(), functionPayload.getPayload());
        assertTrue(decodedFunction.getMessage() instanceof FunctionInvokeMessage);
        assertEquals("restart", ((FunctionInvokeMessage) decodedFunction.getMessage()).getFunctionId());
    }

    @Test
    public void testModuleRepliesKeepMessageId() {
        ReadPropertyMessageReply readReply = ReadPropertyMessageReply.create();
        readReply.messageId("msg-read");
        readReply.success(Map.of("temperature", 36));
        TopicPayload readPayload = JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, moduleMessage("board", readReply, "msg-read"));
        assertEquals("/module/board/properties/read/reply", readPayload.getTopic());
        DeviceModuleMessage decodedRead = (DeviceModuleMessage) JetLinksTopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, readPayload.getTopic(), readPayload.getPayload());
        assertEquals("msg-read", decodedRead.getMessageId());
        assertTrue(decodedRead.getMessage() instanceof ReadPropertyMessageReply);
        assertEquals("msg-read", ((ReadPropertyMessageReply) decodedRead.getMessage()).getMessageId());

        WritePropertyMessageReply writeReply = new WritePropertyMessageReply();
        writeReply.messageId("msg-write");
        writeReply.success();
        assertEquals("/module/board/properties/write/reply", JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, moduleMessage("board", writeReply, "msg-write")).getTopic());

        FunctionInvokeMessageReply functionReply = new FunctionInvokeMessageReply();
        functionReply.messageId("msg-fn");
        functionReply.setFunctionId("restart");
        functionReply.success();
        assertEquals("/module/board/function/invoke/reply", JetLinksTopicMessageCodec.encode(ObjectMappers.JSON_MAPPER, moduleMessage("board", functionReply, "msg-fn")).getTopic());
    }

    private DeviceModuleMessage moduleMessage(String module, DeviceMessage inner, String messageId) {
        DeviceModuleMessage message = new DeviceModuleMessage();
        message.deviceId("test-device");
        message.module(module);
        message.messageId(messageId);
        message.message(inner);
        return message;
    }

}