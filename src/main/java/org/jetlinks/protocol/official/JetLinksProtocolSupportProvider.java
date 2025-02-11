package org.jetlinks.protocol.official;

import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.route.HttpRoute;
import org.jetlinks.core.route.WebsocketRoute;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.http.JetLinksHttpDeviceMessageCodec;
import org.jetlinks.protocol.official.mqtt.JetLinksMqttDeviceMessageCodec;
import org.jetlinks.protocol.official.tcp.TcpDeviceMessageCodec;
import org.jetlinks.protocol.official.udp.UDPDeviceMessageCodec;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JetLinksProtocolSupportProvider implements ProtocolSupportProvider {

    @Override
    public Mono<CompositeProtocolSupport> create(ServiceContext context) {
        return Mono.defer(() -> {
            CompositeProtocolSupport support = new CompositeProtocolSupport();

            support.setId("jetlinks.v3.0");
            support.setName("JetLinks V3.0");
            support.setDescription("JetLinks Protocol Version 3.0");
            //MQTT
            {

                support.addRoutes(DefaultTransport.MQTT, Arrays
                    .stream(TopicMessageCodec.values())
                    .map(TopicMessageCodec::getRoute)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
                );
                support.addRoutes(DefaultTransport.MQTT, Arrays
                    .stream(FunctionalTopicHandlers.values())
                    .map(FunctionalTopicHandlers::getRoute)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
                );

                support.addConfigMetadata(DefaultTransport.MQTT, JetLinksMqttDeviceMessageCodec.mqttConfig);

                support.setDocument(DefaultTransport.MQTT,
                                    "document-mqtt.md",
                                    JetLinksProtocolSupportProvider.class.getClassLoader());
                JetLinksMqttDeviceMessageCodec codec = new JetLinksMqttDeviceMessageCodec(context, DefaultTransport.MQTT);
                //认证逻辑
                support.addAuthenticator(DefaultTransport.MQTT, codec);
                //编解码
                support.addMessageCodecSupport(codec);

            }

            support.addRoutes(DefaultTransport.HTTP, Stream
                .of(TopicMessageCodec.reportProperty,
                    TopicMessageCodec.event,
                    TopicMessageCodec.online,
                    TopicMessageCodec.offline)
                .map(TopicMessageCodec::getRoute)
                .filter(route -> route != null && route.isUpstream())
                .map(route -> HttpRoute
                    .builder()
                    .address(route.getTopic())
                    .group(route.getGroup())
                    .contentType(MediaType.APPLICATION_JSON)
                    .method(HttpMethod.POST)
                    .description(route.getDescription())
                    .example(route.getExample())
                    .build())
                .collect(Collectors.toList())
            );

            support.setDocument(DefaultTransport.HTTP,
                                "document-http.md",
                                JetLinksProtocolSupportProvider.class.getClassLoader());

            support.setMetadataCodec(new JetLinksDeviceMetadataCodec());


            //TCP
            support.addConfigMetadata(DefaultTransport.TCP, TcpDeviceMessageCodec.tcpConfig);
            support.addMessageCodecSupport(new TcpDeviceMessageCodec(context));

            support.setDocument(DefaultTransport.TCP,
                                "document-tcp.md",
                                JetLinksProtocolSupportProvider.class.getClassLoader());

            //UDP
            support.addConfigMetadata(DefaultTransport.UDP, UDPDeviceMessageCodec.udpConfig);
            support.addMessageCodecSupport(new UDPDeviceMessageCodec(context));

            //HTTP
            support.addConfigMetadata(DefaultTransport.HTTP, JetLinksHttpDeviceMessageCodec.httpConfig);
            support.addMessageCodecSupport(new JetLinksHttpDeviceMessageCodec());

            //Websocket
            JetLinksHttpDeviceMessageCodec codec = new JetLinksHttpDeviceMessageCodec(context, DefaultTransport.WebSocket);
            support.addMessageCodecSupport(codec);
            support.addAuthenticator(DefaultTransport.WebSocket, codec);

            support.addRoutes(
                DefaultTransport.WebSocket,
                Collections.singleton(
                    WebsocketRoute
                        .builder()
                        .path("/{productId:产品ID}/{productId:设备ID}/socket")
                        .description("通过Websocket接入平台")
                        .build()
                ));

            //CoAP
            support.addConfigMetadata(DefaultTransport.CoAP, JetLinksCoapDeviceMessageCodec.coapConfig);
            support.addMessageCodecSupport(new JetLinksCoapDeviceMessageCodec());


            return Mono.just(support);
        });
    }
}
