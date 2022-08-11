package org.jetlinks.protocol.official.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import lombok.SneakyThrows;
import org.jetlinks.core.message.AcknowledgeDeviceMessage;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DeviceOnlineMessage;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.core.message.property.WritePropertyMessage;
import org.jetlinks.protocol.official.binary.BinaryDeviceOnlineMessage;
import org.jetlinks.protocol.official.binary.BinaryMessageType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
                                             RecordParser parser = RecordParser.newFixed(4);
                                             AtomicReference<Buffer> buffer = new AtomicReference<>();
                                             parser.handler(buf -> {
                                                 buffer.accumulateAndGet(buf, (a, b) -> {
                                                     if (a == null) {
                                                         parser.fixedSizeMode(buf.getInt(0));
                                                         return b;
                                                     }
                                                     parser.fixedSizeMode(4);

                                                     sink.success("tcp-off-" + i + ":" + socket.localAddress());

                                                     BinaryMessageType
                                                             .read(b.getByteBuf(),
                                                                   null,
                                                                   (downstream, seq) -> {
                                                                       handleDownStream(downstream, seq, socket);
                                                                       return null;
                                                                   });
                                                     return null;
                                                 });
                                             });
                                             socket
                                                     .closeHandler((s) -> {
                                                         System.out.println("tcp-off-" + i + ":" + socket.localAddress() + "closed");
                                                         sink.success();
                                                     })
                                                     .exceptionHandler(er -> {
                                                         System.out.println("tcp-off-" + i + ":" + socket.localAddress() + " " + er.getMessage());
                                                         sink.success();
                                                     })
                                                     .handler(parser);

                                             DeviceOnlineMessage message = new DeviceOnlineMessage();
                                             message.addHeader(BinaryDeviceOnlineMessage.loginToken, "test");
                                             message.setDeviceId("tcp-off-" + i);

                                             socket.write(Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(message, Unpooled.buffer()))));

                                         });
                             }),
                     1024
            )
            .count()
            .subscribe(System.out::println);

        System.in.read();
    }

    protected static void handleDownStream(DeviceMessage downstream, int seq, NetSocket socket) {

        if (!(downstream instanceof AcknowledgeDeviceMessage)) {
            //  System.out.println(downstream);
        }

        DeviceMessage reply = null;
        if (downstream instanceof ReadPropertyMessage) {
            reply = ((ReadPropertyMessage) downstream)
                    .newReply()
                    .success(Collections.singletonMap(
                            "temp0",
                            ThreadLocalRandom
                                    .current()
                                    .nextFloat() * 100
                    ));

        } else if (downstream instanceof WritePropertyMessage) {
            reply = ((WritePropertyMessage) downstream)
                    .newReply()
                    .success(((WritePropertyMessage) downstream).getProperties());

        }
        if (reply != null) {
            socket.write(
                    Buffer.buffer(TcpDeviceMessageCodec.wrapByteByf(BinaryMessageType.write(
                            reply
                            , seq, Unpooled.buffer())))
            );
        }
    }
}
