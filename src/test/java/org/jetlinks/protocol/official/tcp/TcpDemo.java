package org.jetlinks.protocol.official.tcp;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.jetlinks.protocol.official.binary.ServerReplyRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

public class TcpDemo {
    NetSocket netSocket;

    Integer port = 8800;
    String host = "127.0.0.1";
    String key = null;
    String deviceId = "6666";
    Integer msgID = 1;
    String deLimit = "&&&&";
    Integer msgLength = 50;


    NetSocket socket;

    @BeforeEach
    public void init() throws InterruptedException {
        TcpClient tcpClientTest = new TcpClient();
        Vertx.vertx().deployVerticle(tcpClientTest);
        Thread.currentThread().sleep(3000);
    }

    /*
    *   设备上报
    *   站拆包处理 ： 固定长度
    *   在对应TCP网络组件 选择沾拆包规则，长度值50
    */
    @Test
    void testPropertyReport() throws InterruptedException {
        // 先进行设备上线
        online(true,false);
        Thread.currentThread().sleep(2000);

        float temp = (float) (Math.random() * 1000);
        ReportPropertyMessage message = new ReportPropertyMessage();
        message.setDeviceId(deviceId);
        message.setMessageId("testReport");
        //温度
        message.setProperties(Collections.singletonMap("tem", temp));
        System.out.println("向服务器发送一条消息  一次发送3个完整消息  上报3个温度");
        //构建消息 发送一条消息 上报3个温度
        Buffer bufferOne = createMessage(message,false,1,true);
        for (int i = 0; i < 2; i++) {
            message.setProperties(Collections.singletonMap("tem", (float) (Math.random() * 1000)));
            bufferOne.appendBuffer(createMessage(message,false,1,true));
        }
        //向服务器发送一条消息  一次发送3个完整消息  上报3个温度
        socket.write(bufferOne);
        System.out.println("---------------------上报温度完成，本次成功上报三个温度---------------------");

        System.out.println("向服务器发送2条不完整消息 上报3个温度：");
        // 构建 2条消息 上报3个温度
        float tem = (float) (Math.random() * 1000);
        message.setProperties(Collections.singletonMap("tem",tem));
        Buffer bufferTwo = createMessage(message,false,1,false);
        socket.write(bufferTwo);
        System.out.println("发送第一条消息");
        Buffer bufferThree = Buffer.buffer();
        for (int i = 0; i < msgLength-bufferTwo.length(); i++) {
            Byte b = 0;
            bufferThree.appendByte(b);
        }

        tem = (float) (Math.random() * 1000);
        message.setProperties(Collections.singletonMap("tem", tem));
        bufferThree.appendBuffer(createMessage(message,false,1,true));

        tem = (float) (Math.random() * 1000);
        message.setProperties(Collections.singletonMap("tem", tem));
        bufferThree.appendBuffer(createMessage(message,false,1,true));
        socket.write(bufferThree);

        System.out.println("第2条消息成功发送，上报完成");
        Thread.currentThread().sleep(3000);
        // 关闭设备
        socket.close();
    }

    /*
    *  设备上线
    *  lenthType ： 是否固定长度
    *  isDelimit ： 是否分隔符
    */
    void online(Boolean lenthType,Boolean isDelimit) {
        DeviceOnlineMessage message = new DeviceOnlineMessage();
        message.addHeader(BinaryDeviceOnlineMessage.loginToken, "secureKey");
        message.setDeviceId(deviceId);
        Buffer buffers = createMessage(message,false,1,lenthType);
        socket.write(buffers);
        if (isDelimit){
            socket.write(deLimit);
        }
    }

    /*
     * message      :   消息
     * isReply      :   是否回复类型消息
     * msgID        :   消息id
     * lengthType   :   是否固定长度消息
     *
     */

    Buffer createMessage(DeviceMessage message, Boolean isReply, int msgID,Boolean lengthType) {
        Buffer buffer;
        if (isReply) {
            buffer = Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, msgID, Unpooled.buffer())));
        } else {
            buffer = Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer())));
        }
        if (lengthType) {
            int length = buffer.length();
            if (length <= msgLength) {
                for (int i = 0; i < 50 - length; i++) {
                    Byte b = 0;
                    buffer.appendByte(b);
                }
            } else {
                System.out.println("-------------------消息长度超过固定长度-------------------");
                return null;
            }
        }

        return buffer;
    }


    //连接服务器
    class TcpClient extends AbstractVerticle {
        public void start() {
            // 连接服务器
            vertx.createNetClient().connect(port, host, conn -> {
                System.out.println(conn);
                if (conn.succeeded()) {
                    socket = conn.result();
                    socket.handler(buffer -> {
                        ByteBuf byteBuf = buffer.getByteBuf();
                        Map<Integer, DeviceMessage> map = ServerReplyRead.readServer(byteBuf);
                        DeviceMessage read = null;
                        for (Integer key : map.keySet()) {
                            msgID = key;
                            read = map.get(key);
                        }
                        System.out.println("服务器消息:");
                        System.out.println(read == null ? buffer.toString() : read.toString());
                    });
                } else {
                    System.out.println("连接服务器异常");
                }
            });
        }
    }


}
