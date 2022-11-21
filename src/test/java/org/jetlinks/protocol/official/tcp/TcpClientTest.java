package org.jetlinks.protocol.official.tcp;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.function.FunctionInvokeMessageReply;
import org.jetlinks.core.message.property.ReadPropertyMessageReply;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.message.property.WritePropertyMessageReply;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Scanner;

public class TcpClientTest {
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


    /*
      沾拆包脚本，不处理类型 type = 0
      parser.fixed(4).handler(function(buffer, parser) {
            var len = buffer.getShort(2);
            parser.fixed(len).result(buffer);
        }).handler(function(buffer, parser) {
            parser.result(buffer)
                    .complete();
        });
    */



    @Test
    void testOnline() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("-----------------设备已上线-----------------");
        System.out.println("-----------------输入close关闭设备-----------------");
        Scanner sc = new Scanner(System.in);
        while (sc.hasNext()) {
            String close = sc.nextLine();
            if (close.equals("close")) {
                socket = tcpClientTest.resNetSocket();
                closeDevice();
                break;
            }
        }
    }

    @Test
    void testPropertyReport() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("-----------------设备已上线-----------------");
        Scanner sc = new Scanner(System.in);
        System.out.println("-----------------输入要上报的温度（float） 输入close 关闭设备连接-----------------");
        while (sc.hasNext()) {
            socket = tcpClientTest.resNetSocket();
            String str = sc.nextLine();
            if (str.equals("close")) {
                closeDevice();
                break;
            } else {
                if (str.matches("^[-]?[0-9]+\\.?[0-9]+$")) {
                    Float tem = Float.valueOf(str);
                    ReportPropertyMessage message = new ReportPropertyMessage();
                    message.setDeviceId(object.getString("deviceId"));
                    message.setMessageId("testReport");
                    //温度默认标识 temperature
                    message.setProperties(Collections.singletonMap("tem", tem));
                    writeMessage(message, false, 0);
                    System.out.println("本次上报温度 : " + tem);
                } else {
                    System.out.println("-----------------请检查后重新输入参数-----------------");
                }
            }
            System.out.println("-----------------输入要上报的温度（float） 输入close 关闭设备连接-----------------");
        }
    }

    @Test
    void testReadReply() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("-----------------设备已上线-----------------");
        Scanner sc = new Scanner(System.in);
        System.out.println("-----------------输入回复读取 温度  ,输入close 关闭设备连接-----------------");
        while (sc.hasNext()) {
            socket = tcpClientTest.resNetSocket();
            String str = sc.nextLine();
            if (str.equals("close")) {
                closeDevice();
                break;
            } else {
                if (str.matches("^[-]?[0-9]+\\.?[0-9]+$")) {
                    Float tem = Float.valueOf(str);
                    ReadPropertyMessageReply reply = new ReadPropertyMessageReply();
                    reply.setDeviceId(object.getString("deviceId"));
                    reply.setMessageId(str);
                    //温度默认标识 temperature
                    reply.setProperties(Collections.singletonMap("tem", tem));
                    Integer msgID = tcpClientTest.resMessageId() == null ? 1 : tcpClientTest.resMessageId();
                    writeMessage(reply, true, msgID);
                    System.out.println("本次上报温度 : " + tem);
                } else {
                    System.out.println("-----------------请检查后重新输入参数-----------------");
                }
            }
            System.out.println("-----------------依次输入 读取回复消息ID 温度  ,输入close 关闭设备连接-----------------");
        }
    }

    @Test
    void testWriteReply() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("-----------------设备已上线-----------------");
        Scanner sc = new Scanner(System.in);
        System.out.println("-----------------请输入温度  ,输入close 关闭设备连接-----------------");
        while (sc.hasNext()) {
            socket = tcpClientTest.resNetSocket();
            String str = sc.nextLine();
            if (str.equals("close")) {
                closeDevice();
                break;
            } else {
                if (str.matches("^[-]?[0-9]+\\.?[0-9]+$")) {
                    Float tem = Float.valueOf(str);
                    WritePropertyMessageReply reply = new WritePropertyMessageReply();
                    reply.setDeviceId(object.getString("deviceId"));
                    reply.setMessageId(str);
                    //温度默认标识 temperature
                    reply.setProperties(Collections.singletonMap("tem", tem));
                    reply.setSuccess(true);
                    reply.success();

                    Integer msgID = tcpClientTest.resMessageId();
                    writeMessage(reply, true, msgID);
//                    socket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(reply, msgID, Unpooled.buffer()))));
                    System.out.println("本次设置温度 : " + tem);
                } else {
                    System.out.println("-----------------请检查后重新输入参数-----------------");
                }
            }
            System.out.println("-----------------依次输入 回复消息ID 温度  ,输入close 关闭设备连接-----------------");
        }
    }

    @Test
    void testInvokeReply() {
        TcpClient tcpClientTest = new TcpClient(object);
        Vertx.vertx().deployVerticle(tcpClientTest);
        System.out.println("-----------------设备已上线-----------------");
        Scanner sc = new Scanner(System.in);
        System.out.println("-----------------请输入 调用回复消息ID ,输入close 关闭设备连接-----------------");
        while (sc.hasNext()) {
            socket = tcpClientTest.resNetSocket();
            String str = sc.nextLine();
            if (str.equals("close")) {
                closeDevice();
                break;
            } else {
                FunctionInvokeMessageReply reply = new FunctionInvokeMessageReply();
                reply.setDeviceId(object.getString("deviceId"));
                reply.setMessageId(str);
                reply.setOutput("success");
                // 功能标识默认 name
                reply.setFunctionId("fun1");
                Integer msgID = tcpClientTest.resMessageId();
                writeMessage(reply, true, msgID);
//                socket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(reply, Unpooled.buffer()))));
                System.out.println("-----------------调用功能成功-----------------");

            }
            System.out.println("-----------------请输入任意字符回复,输入close 关闭设备连接-----------------");
        }
    }


    void closeDevice() {
        System.out.println("-----------正在关闭设备-----------");
        socket.close();
        System.out.println("-----------关闭闭设备成功-----------");
    }

    void writeMessage(DeviceMessage message, Boolean isReply, int msgID) {
        Buffer buffer;
        if (isReply) {
            buffer = Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, msgID, Unpooled.buffer())));
        } else {
            buffer = Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer())));
        }
        if (object.getInteger("type") == 2) {
            System.out.println(buffer.length());
            int msgLength = object.getInteger("msgLength");
            int length = buffer.length();
            if (length <= msgLength) {
                for (int i = 0; i < 50 - length; i++) {
                    Byte b = 0;
                    buffer.appendByte(b);
                }
            } else {
                System.out.println("-------------------消息长度超过固定长度-------------------");
                return;
            }
        }
        socket.write(buffer);
        if (object.getInteger("type") == 1){
            socket.write(object.getString("deLimit"));
        }
    }
}
