package org.jetlinks.protocol.official.tcp;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Scanner;

public class TcpDemo {
    NetSocket socket;
    JSONObject object = new JSONObject();

    @BeforeEach
    //连接所需参数
    public void init() {
        // 设备id
        object.put("deviceId", "6666");
        // ip
        object.put("host", "127.0.0.1");
        // 端口
        object.put("port", 8800);
        // key
        object.put("key", "secureKey");
        // 沾拆包类型  0-不处理 1-分隔符 2-固定长度
        object.put("type", 0);
        // 固定消息长度
        object.put("msgLength", 50);
        // 分隔符
        object.put("deLimit", "&&&");
    }

    /**
     *   1.采用 TCP协议 固定长度方式 上报温度
     *   2.在网络组件中配置沾拆包规则，选择固定长度，长度设为50
     *   3.程序运行后： 1) 等待控制打印提示后 输入任意字符 进行设备上线
     *                2) 当服务器打印服务器消息后，在设备列表中，对应设备显示已上线状态
     *                3) 再次输入任意字符，会向服务器发送一条长消息，包含3个上报温度的消息，可在设备日志中查看3个消息
     */

    @Test
    void testPropertyReport() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("输入任意字符开始连接服务器。。。。。。");
        Scanner sc = new Scanner(System.in);
        int count = 0;
        while (sc.hasNext()) {
            String str = sc.nextLine();
            //进行设备连接验证
            if (count == 0) {
                socket = tcpClientTest.resNetSocket();
                DeviceOnlineMessage message = new DeviceOnlineMessage();
                message.addHeader(BinaryDeviceOnlineMessage.loginToken, "secureKey");
                message.setDeviceId(object.getString("deviceId"));
                ByteBuf buf = TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()));
                Buffer buffers = Buffer.buffer(buf);
                int length = buffers.length();
                if (length <= object.getInteger("msgLength")) {
                    for (int i = 0; i < object.getInteger("msgLength") - length; i++) {
                        Byte b = 0;
                        buffers.appendByte(b);
                    }
                } else {
                    System.out.println("------------消息长度超过设置长度------------");
                    socket.close();
                }
                socket.write(buffers);
                count++;
                System.out.println("---------------------设备连接成功---------------------");
            }else {
                socket = tcpClientTest.resNetSocket();

                //构建3条消息   上报3个温度
                Buffer bufferOne = createMessage();
                Buffer bufferTwo = createMessage();
                Buffer bufferThree = createMessage();
                bufferOne.appendBuffer(bufferTwo).appendBuffer(bufferThree);
                //向服务器发送消息
                socket.write(bufferOne);
                System.out.println("---------------------上报温度完成，本次成功上报三个温度---------------------");
                System.out.println("---------------------请到设备日志查看本次上报记录---------------------");
            }
            System.out.println("---------------------输入任意字符上报3个温度---------------------");

        }
    }

    Buffer createMessage() {
        float temp = (float) (Math.random() * 1000);
        // 构建消息
        ReportPropertyMessage message = new ReportPropertyMessage();
        message.setDeviceId(object.getString("deviceId"));
        message.setMessageId("testReport");
        //温度默认标识 temperature
        message.setProperties(Collections.singletonMap("tem", temp));

        ByteBuf buf = TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()));


        Buffer buffers = Buffer.buffer(buf);
        int length = buffers.length();
        if (length <= object.getInteger("msgLength")) {
            for (int i = 0; i < object.getInteger("msgLength") - length; i++) {
                Byte b = 0;
                buffers.appendByte(b);
            }
        } else {
            System.out.println("------------消息长度超过设置长度------------");
            socket.close();
        }
        return buffers;
    }
}
