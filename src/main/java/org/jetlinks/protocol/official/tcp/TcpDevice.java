package org.jetlinks.protocol.official.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.SneakyThrows;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class TcpDevice {
    @SneakyThrows
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        int start = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
        String[] hosts = args.length > 2 ? args[2].split(",") : new String[]{"0.0.0.0"};

        Flux.range(start, count)
            .flatMap(i -> Mono
                    .create(sink -> {
                        NetClientOptions conf = new NetClientOptions().setTcpKeepAlive(true);
                        conf.setLocalAddress(hosts[i % hosts.length]);
                        vertx
                                .createNetClient(conf)
                                .connect(8802, "localhost")
                                .onFailure(err -> {
                                    System.out.println(err.getMessage());
                                    sink.success();
                                })
                                .onSuccess(socket -> {
                                    socket
                                            .closeHandler((s) -> {
                                                System.out.println("tcp-off-" + i + ":" + socket.localAddress() + "closed");
                                                sink.success();
                                            })
                                            .exceptionHandler(er -> {
                                                System.out.println("tcp-off-" + i + ":" + socket.localAddress() + " " + er.getMessage());
                                                sink.success();
                                            })
                                            .handler(buffer -> {
                                                sink.success("tcp-off-" + i + ":" + socket.localAddress());

                                                ByteBuf buf = buffer.getByteBuf();
                                                buf.readInt();
                                                BinaryMessageType
                                                        .read(buf,
                                                              null,
                                                              (downstream, seq) -> {
                                                                  handleDownStream(downstream, seq, socket);
                                                                  return null;
                                                              });

                                            });

                                    DeviceOnlineMessage message = new DeviceOnlineMessage();
                                    message.addHeader(BinaryDeviceOnlineMessage.loginToken, "test");
                                    message.setDeviceId("tcp-off-" + i);

                                    socket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))));

                                });
                    })
            )
            .count()
            .subscribe(System.out::println);

        System.in.read();
    }

    protected static void handleDownStream(DeviceMessage downstream, int seq, NetSocket socket) {
        System.out.println(downstream);

        if (downstream instanceof ReadPropertyMessage) {
            socket.write(
                    Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(
                            ((ReadPropertyMessage) downstream)
                                    .newReply()
                                    .success(Collections.singletonMap(
                                            ((ReadPropertyMessage) downstream)
                                                    .getProperties()
                                                    .get(0),
                                            ThreadLocalRandom
                                                    .current()
                                                    .nextFloat() * 100
                                    ))
                            , seq, Unpooled.buffer())))
            );
        }
    }
}
