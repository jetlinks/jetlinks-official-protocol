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

    // ==========================================================================
    // Workaround: preload all protocol classes to avoid ClassNotFoundException in encode path
    //
    // ProtocolClassLoader lazy loads inner classes which can cause
    // ClassNotFoundException when encoding (e.g. TopicMessageCodec.doEncode
    // calling TopicPayload.of(...)).
    //
    // This static block scans all .class entries in the JAR and calls
    // Class.forName() to preload them into the ProtocolClassLoader cache.
    
    // ==========================================================================
    static {
        try {
            ClassLoader cl = JetLinksProtocolSupportProvider.class.getClassLoader();
            java.net.URL src = JetLinksProtocolSupportProvider.class
                .getProtectionDomain().getCodeSource().getLocation();
            int count = 0;
            try (java.util.jar.JarInputStream jis =
                     new java.util.jar.JarInputStream(src.openStream())) {
                java.util.jar.JarEntry e;
                while ((e = jis.getNextJarEntry()) != null) {
                    String name = e.getName();
                    if (name.endsWith(".class")
                        && name.startsWith("org/jetlinks/protocol/official/")) {
                        String cn = name.substring(0, name.length() - 6).replace('/', '.');
                        try {
                            Class.forName(cn, false, cl);
                            count++;
                        } catch (Throwable ignored) {
                            // best effort: individual class load failures are non-fatal
                        }
                    }
                }
            }
            System.out.println("preloaded " + count
                + " classes from official-protocol JAR via " + src);
        } catch (Throwable t) {
            System.err.println("preload failed: " + t);
        }
    }

    @Override
    public Mono<CompositeProtocolSupport> create(ServiceContext context) {
        return Mono.defer(() -> {
            CompositeProtocolSupport support = new CompositeProtocolSupport();

            support.setId("jetlinks.v3.2");
            support.setName("JetLinks V3.2");
            support.setDescription("JetLinks Protocol Version 3.2");
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
            support.addConfigMetadata(DefaultTransport.WebSocket, JetLinksHttpDeviceMessageCodec.webSocketConfig);
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
