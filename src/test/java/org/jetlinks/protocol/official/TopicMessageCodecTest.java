package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetlinks.core.message.ChildDeviceMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
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
    public void doTest() {
        testChild(ObjectMappers.JSON_MAPPER);
        testChild(ObjectMappers.CBOR_MAPPER);
    }
}