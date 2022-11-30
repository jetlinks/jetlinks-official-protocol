package org.jetlinks.protocol.official;

import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.route.HttpRoute;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.http.JetLinksHttpDeviceMessageCodec;
import org.jetlinks.protocol.official.tcp.TcpDeviceMessageCodec;
import org.jetlinks.protocol.official.udp.UDPDeviceMessageCodec;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JetLinksProtocolSupportProvider implements ProtocolSupportProvider {

    private static final DefaultConfigMetadata mqttConfig = new DefaultConfigMetadata(
            "MQTT认证配置"
            , "MQTT认证时需要的配置,mqtt用户名,密码算法:\n" +
                    "username=secureId|timestamp\n" +
                    "password=md5(secureId|timestamp|secureKey)\n" +
                    "\n" +
                    "timestamp为时间戳,与服务时间不能相差5分钟")
            .add("secureId", "secureId", "密钥ID", new StringType())
            .add("secureKey", "secureKey", "密钥KEY", new PasswordType());

    @Override
    public Mono<CompositeProtocolSupport> create(ServiceContext context) {
        return Mono.defer(() -> {
            CompositeProtocolSupport support = new CompositeProtocolSupport();

            support.setId("jetlinks.v3.0");
            support.setName("JetLinks V3.0");
            support.setDescription("JetLinks Protocol Version 3.0");

            support.addRoutes(DefaultTransport.MQTT, Arrays
                    .stream(TopicMessageCodec.values())
                    .map(TopicMessageCodec::getRoute)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
            );
            support.setDocument(DefaultTransport.MQTT,
                                "document-mqtt.md",
                                JetLinksProtocolSupportProvider.class.getClassLoader());

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


            support.addAuthenticator(DefaultTransport.MQTT, new JetLinksAuthenticator());

            support.setMetadataCodec(new JetLinksDeviceMetadataCodec());

            support.addConfigMetadata(DefaultTransport.MQTT, mqttConfig);


            //TCP
            support.addConfigMetadata(DefaultTransport.TCP, TcpDeviceMessageCodec.tcpConfig);
            support.addMessageCodecSupport(new TcpDeviceMessageCodec());
            support.setDocument(DefaultTransport.TCP,
                                "document-tcp.md",
                                JetLinksProtocolSupportProvider.class.getClassLoader());

            //UDP
            support.addConfigMetadata(DefaultTransport.UDP, UDPDeviceMessageCodec.udpConfig);
            support.addMessageCodecSupport(new UDPDeviceMessageCodec());

            //MQTT
            support.addMessageCodecSupport(new JetLinksMqttDeviceMessageCodec());

            //HTTP
            support.addConfigMetadata(DefaultTransport.HTTP, JetLinksHttpDeviceMessageCodec.httpConfig);
            support.addMessageCodecSupport(new JetLinksHttpDeviceMessageCodec());

            //CoAP
            support.addConfigMetadata(DefaultTransport.CoAP, JetLinksCoapDeviceMessageCodec.coapConfig);
            support.addMessageCodecSupport(new JetLinksCoapDeviceMessageCodec());

            return Mono.just(support);
        });
    }
}
