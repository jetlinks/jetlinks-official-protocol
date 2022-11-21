package org.jetlinks.protocol.official.tcp;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;

import java.util.Map;

public class TcpClient extends AbstractVerticle {
    private static NetSocket netSocket;

    private static Integer port = null;
    private static String host = null;
    private static String key = null;
    private static String deviceId = null;
    private static Integer msgID = null;
    private static Integer type = 0;  // TCP 沾拆包类型， 0-不处理 1-分隔符 2-固定长度
    private static String deLimit = null;
    private static Integer msgLength = null;


    public TcpClient(JSONObject object) {
        port = object.getInteger("port");
        host = object.getString("host");
        key = object.getString("key");
        deviceId = object.getString("deviceId");
        type = object.getInteger("type");
        deLimit = object.get("deLimit") == null ? null : object.getString("deLimit");
        msgLength = object.getInteger("msgLength");
    }

    public void start() {
        DeviceOnlineMessage message = new DeviceOnlineMessage();
        message.addHeader(BinaryDeviceOnlineMessage.loginToken, key);
        message.setDeviceId(deviceId);

        // 连接服务器
        vertx.createNetClient().connect(port, host, conn -> {
            System.out.println(conn);
            if (conn.succeeded()) {
                netSocket = conn.result();
//                RecordParser.newDelimited("&&&&", netSocket).handler(buffer -> {
//                    String greeting = buffer.toString("UTF-8");
//                    System.out.println("Net client receiving: " + greeting);
//                });
                // 向服务器写数据
                ByteBuf buf = TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()));
                Buffer buffers = Buffer.buffer(buf);
                if (type == 2) {
                    System.out.println(buffers.length());
                    int length = buffers.length();
                    if (length <= msgLength) {
                        for (int i = 0; i < msgLength - length; i++) {
                            Byte b = 0;
                            buffers.appendByte(b);
                        }
                    }else {
                        System.out.println("------------消息长度超过设置长度------------");
                        netSocket.close();
                    }
                }
                netSocket.write(buffers);
                if (type == 1){
                    netSocket.write(deLimit);
                }
//                netSocket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))));
//                netSocket.write("&&&&");

                // 读取服务器的响应数据
                netSocket.handler(buffer -> {
                    ByteBuf byteBuf = buffer.getByteBuf();
                    Map<Integer, DeviceMessage> map = BinaryMessageType.readServer(byteBuf);
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

    public NetSocket resNetSocket() {
        return netSocket;
    }

    public Integer resMessageId() {
        return msgID;
    }

}
