package org.jetlinks.protocol.official;

import org.apache.commons.collections4.MapUtils;
import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.device.DeviceFeatures;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.principal.CredentialType;
import org.jetlinks.core.principal.PrincipalMetadata;
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
import reactor.core.publisher.Flux;
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

            support.setId("jetlinks.v3.3");
            support.setName("JetLinks V3.3");
            support.setDescription("JetLinks Protocol Version 3.3");
            // 使用平台侧管理凭证
            support.addFeature(DeviceFeatures.supportPrincipal);
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

//                support.addConfigMetadata(DefaultTransport.MQTT, JetLinksMqttDeviceMessageCodec.mqttConfig);

                support.setDocument(DefaultTransport.MQTT,
                                    "document-mqtt.md",
                                    JetLinksProtocolSupportProvider.class.getClassLoader());
                JetLinksMqttDeviceMessageCodec codec = new JetLinksMqttDeviceMessageCodec(context, DefaultTransport.MQTT);
                //认证逻辑
                support.addAuthenticator(DefaultTransport.MQTT, codec);
                //编解码
                support.addMessageCodecSupport(codec);

                // 身份凭证解析器
                support.addPrincipalMetadataResolver(
                    DefaultTransport.MQTT,
                    device -> {
                        PrincipalMetadata metadata = new PrincipalMetadata();
                        metadata.setName("直连MQTT");
                        metadata.setDescription("直连平台的MQTT服务时所需的身份认证信息");
                        // 平台内置mqtt服务,接入固定为MQTT.
                        metadata.setType(DefaultTransport.MQTT.getId());
                        // 用户可以自己动态配置ClientId
                        // 为空表示以平台设备ID为准,因为有的协议需要自定义设备标识,不能使用平台id.
//                        metadata.setIdentifier(MapUtils.getString(device.getConfiguration(), "mqttClientId"));
                        // 密码方式认证
                        metadata.setCredentialType(CredentialType.password);
                        return Flux.just(metadata);
                    }
                );

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
            {
                // 身份凭证解析器
                support.addPrincipalMetadataResolver(
                    DefaultTransport.TCP,
                    device -> {
                        PrincipalMetadata metadata = new PrincipalMetadata();
                        metadata.setName("tcp");
                        // 平台内置mqtt服务,接入固定为MQTT.
                        metadata.setType(TcpDeviceMessageCodec.identityType);
                        // 不指定Identifier, 由平台生成.
                        // metadata.setIdentifier();
                        // token方式认证
                        metadata.setCredentialType(CredentialType.token);
                        return Flux.just(metadata);
                    }
                );
            }

            //UDP
            support.addConfigMetadata(DefaultTransport.UDP, UDPDeviceMessageCodec.udpConfig);
            support.addMessageCodecSupport(new UDPDeviceMessageCodec(context));

            {
                // 身份凭证解析器
                support.addPrincipalMetadataResolver(
                    DefaultTransport.UDP,
                    device -> {
                        PrincipalMetadata metadata = new PrincipalMetadata();
                        metadata.setName("udp");
                        // 平台内置mqtt服务,接入固定为MQTT.
                        metadata.setType(UDPDeviceMessageCodec.identityType);
                        // 不指定Identifier, 由平台生成.
                        // metadata.setIdentifier();
                        // token方式认证
                        metadata.setCredentialType(CredentialType.token);
                        return Flux.just(metadata);
                    }
                );
            }

            //HTTP
            support.addConfigMetadata(DefaultTransport.HTTP, JetLinksHttpDeviceMessageCodec.httpConfig);
            support.addMessageCodecSupport(new JetLinksHttpDeviceMessageCodec());

            // 身份凭证解析器
            support.addPrincipalMetadataResolver(
                DefaultTransport.HTTP,
                device -> {
                    PrincipalMetadata metadata = new PrincipalMetadata();
                    metadata.setName("HTTP");
                    // 平台内置mqtt服务,接入固定为MQTT.
                    metadata.setType(JetLinksHttpDeviceMessageCodec.identityType);
                    // 不指定Identifier, 由平台生成.
                    // metadata.setIdentifier();
                    // token方式认证
                    metadata.setCredentialType(CredentialType.token);
                    return Flux.just(metadata);
                }
            );

            //Websocket
            JetLinksHttpDeviceMessageCodec codec = new JetLinksHttpDeviceMessageCodec(context, DefaultTransport.WebSocket);
            support.addConfigMetadata(DefaultTransport.WebSocket, JetLinksHttpDeviceMessageCodec.webSocketConfig);
            support.addMessageCodecSupport(codec);
            support.addAuthenticator(DefaultTransport.WebSocket, codec);

            // 身份凭证解析器
            support.addPrincipalMetadataResolver(
                DefaultTransport.WebSocket,
                device -> {
                    PrincipalMetadata metadata = new PrincipalMetadata();
                    metadata.setName("websocket");
                    // 平台内置mqtt服务,接入固定为MQTT.
                    metadata.setType(JetLinksHttpDeviceMessageCodec.identityType);
                    // 不指定Identifier, 由平台生成.
                    // metadata.setIdentifier();
                    // token方式认证
                    metadata.setCredentialType(CredentialType.token);
                    return Flux.just(metadata);
                }
            );

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
