package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetlinks.core.codec.defaults.TopicPayloadCodec;
import org.jetlinks.core.message.ChildDeviceMessage;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.route.Route;
import org.junit.Test;
import reactor.test.StepVerifier;

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

        TopicMessageCodec
                .decode(objectMapper, payload.getTopic(), payload.getPayload())
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
                .decode(ObjectMappers.JSON_MAPPER, payload.getTopic(), payload.getPayload())
                .blockLast();
        assertEquals(msg.toJson(), eventMessage.toJson());
    }
}