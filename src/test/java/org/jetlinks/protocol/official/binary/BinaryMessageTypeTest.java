package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.*;
import org.junit.Assert;
import org.junit.Test;
import reactor.test.StepVerifier;

import java.util.Collections;

public class BinaryMessageTypeTest {


    @Test
    public void testOnline() {

        DeviceOnlineMessage message = new DeviceOnlineMessage();
        message.setDeviceId("1000");
        message.addHeader(BinaryDeviceOnlineMessage.loginToken, "admin");

        ByteBuf byteBuf = BinaryMessageType.write(message, Unpooled.buffer());

        System.out.println(ByteBufUtil.prettyHexDump(byteBuf));

        System.out.println(ByteBufUtil.prettyHexDump(Unpooled
                                                             .buffer()
                                                             .writeInt(byteBuf.readableBytes())
                                                             .writeBytes(byteBuf)));
    }

    @Test
    public void testReport() {
        ReportPropertyMessage message = new ReportPropertyMessage();
        message.setDeviceId("test");
        message.setMessageId("test123");
        message.setProperties(Collections.singletonMap("temp", 32.88));

        doTest(message);
    }

    @Test
    public void testRead() {
        ReadPropertyMessage message = new ReadPropertyMessage();
        message.setDeviceId("test");
        message.setMessageId("test123");
        message.setProperties(Collections.singletonList("temp"));
        doTest(message);

        ReadPropertyMessageReply reply = new ReadPropertyMessageReply();
        reply.setDeviceId("test");
        reply.setMessageId("test123");
        reply.setProperties(Collections.singletonMap("temp", 32.88));
        doTest(reply);

    }

    @Test
    public void testWrite() {
        WritePropertyMessage message = new WritePropertyMessage();
        message.setDeviceId("test");
        message.setMessageId("test123");
        message.setProperties(Collections.singletonMap("temp", 32.88));
        doTest(message);

        WritePropertyMessageReply reply = new WritePropertyMessageReply();
        reply.setDeviceId("test");
        reply.setMessageId("test123");
        reply.setProperties(Collections.singletonMap("temp", 32.88));
        doTest(reply);

    }

    @Test
    public void testFunction() {
        FunctionInvokeMessage message = new FunctionInvokeMessage();
        message.setFunctionId("123");
        message.setDeviceId("test");
        message.setMessageId("test123");
        message.addInput("test", 1);
        doTest(message);

        FunctionInvokeMessageReply reply = new FunctionInvokeMessageReply();
        reply.setDeviceId("test");
        reply.setMessageId("test123");
        reply.setOutput(123);
        doTest(reply);

    }

    public void doTest(DeviceMessage message) {

        ByteBuf data = BinaryMessageType.write(message, Unpooled.buffer());

        System.out.println(ByteBufUtil.prettyHexDump(data));
        DeviceMessage read = BinaryMessageType.read(data);
        if (null != read.getHeaders()) {
            read.getHeaders().forEach(message::addHeader);
        }
        System.out.println(read);
        Assert.assertEquals(read.toString(), message.toString());
    }

}