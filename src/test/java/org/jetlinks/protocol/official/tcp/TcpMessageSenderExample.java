package org.jetlinks.protocol.official.tcp;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.event.EventMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.core.message.property.ReadPropertyMessageReply;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


public class TcpMessageSenderExample {


    private String host = "localhost";

    private int port = 8808;

    private NetSocket netSocket;

    private String secureKey = "test00011122233344";

    private String deviceId = "deviceId";

    private boolean executed;

    long timeOut = 15000;

    //消息处理类型。 0:不处理；1：固定分隔符；
    private int messageHandleType = 0;

    //messageHandleType为1时使用
    private String delimiter = "&&";

    @Before
    public void init() throws InterruptedException {
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setMaxEventLoopExecuteTime(30);
        vertxOptions.setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS);
        Vertx.vertx(vertxOptions)
             .createNetClient()
             .connect(port, host, result -> {
                 if (result.succeeded()) {
                     System.out.println("客户端连接成功");
                 }
                 netSocket = result.result();
                 //发送设备上线消息
                 sendOnlineMessage();
             });
        while (netSocket == null) {
            Thread.sleep(100);
        }
    }

    /**
     * 发送一条简单的属性上报消息。此时【网络组件】粘拆包规则应设置为：不处理
     */
    @Test
    public void sendPropertyReportMessage() {
        String property = "temperature";
        String propertyValue = "35.6";
        ReportPropertyMessage message = new ReportPropertyMessage();
        message.setDeviceId(deviceId);
        message.setProperties(Collections.singletonMap(property, propertyValue));
        sendMessage(TcpDeviceMessageCodec.wrapByteByf(
                BinaryMessageType.write(message, Unpooled.buffer()).writeBytes(Unpooled.wrappedBuffer(delimiter.getBytes(StandardCharsets.UTF_8)))));
    }

    /**
     * 发送以固定分隔符切分处理的属性上报消息。此时【网络组件】粘拆包规则应设置为：固定分隔符。
     * @see delimiter
     *
     * 上报属性[temperature]数据为35, 36, 37的三条数据。使用分隔符隔开后一次性发送。平台生成三条设备数据
     */
    @Test
    public void sendDelimitedHandlePropertyReportMessage() {
        String property = "temperature";
        int[] temperaturesValue = new int[]{35, 36, 37};
        ByteBuf byteBuf = Unpooled.buffer();
        for (int i = 0; i < temperaturesValue.length; i++) {
            ReportPropertyMessage message = new ReportPropertyMessage();
            message.setDeviceId(deviceId);
            message.setProperties(Collections.singletonMap(property, temperaturesValue[i]));
            byteBuf = byteBuf.writeBytes(
                    TcpDeviceMessageCodec.wrapByteByf(
                            BinaryMessageType.write(message, Unpooled.buffer()).writeBytes(Unpooled.wrappedBuffer(delimiter.getBytes(StandardCharsets.UTF_8)))
                    )
            );
        }
        sendMessage(byteBuf);
    }



    /**
     * 发送以固定长度处理的属性上报消息。此时【网络组件】粘拆包规则应设置为：固定长度。 且长度值设置为：50。
     * 上报属性[temperature]数据为35, 36, 37的三条数据。使用分隔符隔开后一次性发送。平台生成三条设备数据
     * <br />
     * <b>注意：修改设备Id或认证的secureKey会导致消息数据长度发生变化。当各种类的消息长度不同时，会导致消息数据解析失败。<br />
     * 如：上线消息长度为100，上线消息长度为50，此时无论怎么设置【网络组件】固定长度值，均会导致业务异常。
     * 要么获取的上线消息不完整，设备无法上线，要么设备上线后，解析不出完整的上线消息。
     * <b />
     */
    @Test
    public void sendFixedLengthHandlePropertyReportMessage() {
        String property = "temperature";
        int[] temperaturesValue = new int[]{35, 36, 37};
        ByteBuf byteBuf = Unpooled.buffer();
        for (int i = 0; i < temperaturesValue.length; i++) {
            ReportPropertyMessage message = new ReportPropertyMessage();
            message.setDeviceId(deviceId);
            message.setProperties(Collections.singletonMap(property, temperaturesValue[i]));
            byteBuf = byteBuf.writeBytes(
                    TcpDeviceMessageCodec.wrapByteByf(
                            BinaryMessageType.write(message, Unpooled.buffer())
                    )
            );
        }
        System.out.println(Buffer.buffer(byteBuf).length());
        sendMessage(byteBuf);
    }


    /**
     * 发送以脚本处理的属性上报消息。此时【网络组件】粘拆包规则应设置为：自定义脚本。
     * 上报属性[temperature]数据为35, 36, 37的三条数据。使用分隔符隔开后一次性发送。平台生成三条设备数据
     *
     * <br />
     * 当前逻辑对应的脚本如下
     *
     * <pre>{@code
     * //先读取4个字节
     * parser.fixed(4)
     *     //第一次读取数据
     *     .handler(function (buffer, parser) {
     *     //4字节转为int，表示接下来要读取的包长度
     *         var len = buffer.getInt(0);
     *         parser
     *         .fixed(len)//设置接下来要读取的字节长度
     *         .result(buffer);//将已读取的4字节设置到结果中
     *     })
     *      //第二次读取数据
     *     .handler(function (buffer, parser) {
     *         parser
     *         .result(buffer)//设置结果
     *         .complete();//完成本次读取，输出结果，开始下一次读取
     *     });
     * }
     * </pre>
     */
    @Test
    public void sendScriptHandlePropertyReportMessage() {
        /**
         * 在TcpDeviceMessageCodec.wrapByteByf()中已设置第一次读取数据的长度。所以直接使用即可
         *
         * @see TcpDeviceMessageCodec#wrapByteByf(ByteBuf)
         */
        sendPropertyReportMessage();
    }


    /**
     * 回复设备属性读取，此时【网络组件】粘拆包规则应设置为：不处理。其它规则参考属性上报
     */
    @Test
    public void replyReadPropertyMessage() {
        System.out.println("请先在平台中操作读取设备属性，然后按提示回复");
        netSocket.handler(handle->{
            ByteBuf byteBuf = handle.getByteBuf();
            byteBuf.readInt();
            DeviceMessage message = BinaryMessageType.read(byteBuf);
            if (message instanceof ReadPropertyMessage){
                System.out.println("如Scanner输入不可用，请在idea菜单->help->Edit Custom Vm Options 输入项底部添加【-Deditable.java.test.console=true】 并重启idea即可");
                Scanner sc = new Scanner(System.in);
                ReadPropertyMessage msg = (ReadPropertyMessage)message;
                Map<String, Object> properties =
                msg.getProperties()
                   .stream()
                   .collect(Collectors.toMap(Function.identity(), property->{
                       System.out.println("请输入回复给平台的["+ property +"]属性值：");
                       return sc.next();
                   }));
                ReadPropertyMessageReply messageReply = new ReadPropertyMessageReply();
                messageReply.setDeviceId(msg.getDeviceId());
                messageReply.setProperties(properties);
                ByteBuf data = TcpDeviceMessageCodec.wrapByteByf(
                        BinaryMessageType
                                .write(messageReply, Integer.parseInt(msg.getMessageId()), Unpooled.buffer()));
                sendMessage(data);
                sc.close();

            }
        });
    }

    /**
     * 回复设备功能执行，此时【网络组件】粘拆包规则应设置为：不处理。其它规则参考属性上报
     */
    @Test
    public void replyFunctionInvokeMessage() {
        System.out.println("请先在平台中【设备功能】执行功能，然后按提示回复");
        netSocket.handler(handle->{
            ByteBuf byteBuf = handle.getByteBuf();
            byteBuf.readInt();
            DeviceMessage message = BinaryMessageType.read(byteBuf);
            if (message instanceof FunctionInvokeMessage){
                System.out.println("如Scanner输入不可用，请在idea菜单->help->Edit Custom Vm Options 输入项底部添加【-Deditable.java.test.console=true】 并重启idea即可");
                Scanner sc = new Scanner(System.in);
                FunctionInvokeMessage msg = (FunctionInvokeMessage)message;
                System.out.println("请输入回复给平台的["+ msg.getFunctionId() +"]功能执行结果：");
                String next = sc.next();

                FunctionInvokeMessageReply messageReply = new FunctionInvokeMessageReply();
                messageReply.setDeviceId(msg.getDeviceId());
                messageReply.setOutput(next);
                ByteBuf data = TcpDeviceMessageCodec.wrapByteByf(
                        BinaryMessageType
                                .write(messageReply, Integer.parseInt(msg.getMessageId()), Unpooled.buffer()));
                sendMessage(data);
                sc.close();
            }
        });
    }


    /**
     * 事件上报，此时【网络组件】粘拆包规则应设置为：不处理。其它规则参考属性上报
     */
    @Test
    public void reportEventMessage() {
        String event = "eventId";
        EventMessage message = new EventMessage();
        message.setDeviceId(deviceId);
        message.setEvent(event);
        message.setData(Collections.singletonMap("name","test"));
        sendMessage(TcpDeviceMessageCodec.wrapByteByf(
                BinaryMessageType.write(message, Unpooled.buffer()).writeBytes(Unpooled.wrappedBuffer(delimiter.getBytes(StandardCharsets.UTF_8)))));
    }



    void sendOnlineMessage() {
        DeviceOnlineMessage message = new DeviceOnlineMessage();
        message.setDeviceId(deviceId);
        message.addHeader(BinaryDeviceOnlineMessage.loginToken, secureKey);
        netSocket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))), result -> {
            if (result.succeeded()) {
                //executed = true;
            }
        });
    }


    void sendMessage(ByteBuf byteBuf) {
        //固定分隔符处理
        if (messageHandleType == 1){
            byteBuf.writeBytes(delimiter.getBytes(StandardCharsets.UTF_8));
        }
        netSocket.write(Buffer.buffer(byteBuf), result -> {
            if (result.succeeded()) {
                executed = true;
                System.out.println("消息发送成功");
            }
        });
    }

    @After
    public void after() throws InterruptedException {
        int time = 0;
        while (!executed && time < timeOut) {
            time += 100;
            Thread.sleep(100);
        }
        Thread.sleep(5000);
        if (netSocket != null) {
            netSocket.close();
        }
    }

}
