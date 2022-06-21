package org.jetlinks.protocol.official.http;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.core.device.DeviceConfigKey;
import org.jetlinks.core.enums.ErrorCode;
import org.jetlinks.core.exception.DeviceOperationException;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DisconnectDeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.message.codec.http.Header;
import org.jetlinks.core.message.codec.http.HttpExchangeMessage;
import org.jetlinks.core.message.codec.http.SimpleHttpResponseMessage;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.protocol.official.FunctionalTopicHandlers;
import org.jetlinks.protocol.official.ObjectMappers;
import org.jetlinks.protocol.official.TopicMessageCodec;
import org.jetlinks.protocol.official.TopicPayload;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Http 的消息编解码器
 *
 * @author zhouhao
 * @since 3.0.0
 */
@Slf4j
public class JetLinksHttpDeviceMessageCodec implements DeviceMessageCodec {
    public static final DefaultConfigMetadata httpConfig = new DefaultConfigMetadata(
            "HTTP认证配置"
            , "使用HTTP Bearer Token进行认证")
            .add("bearer_token", "Token", "Token", new PasswordType());

    private final Transport transport;

    public JetLinksHttpDeviceMessageCodec(Transport transport) {
        this.transport = transport;
    }

    public JetLinksHttpDeviceMessageCodec() {
        this(DefaultTransport.HTTP);
    }

    @Override
    public Transport getSupportTransport() {
        return transport;
    }

    @Nonnull
    public Mono<MqttMessage> encode(@Nonnull MessageEncodeContext context) {
        //http 暂不支持下发
        return Mono.empty();
    }

    private static final SimpleHttpResponseMessage unauthorized = SimpleHttpResponseMessage
            .builder()
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":true,\"code\":\"unauthorized\"}")
            .status(401)
            .build();

    private static final SimpleHttpResponseMessage badRequest = SimpleHttpResponseMessage
            .builder()
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":false,\"code\":\"bad_request\"}")
            .status(400)
            .build();

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        HttpExchangeMessage message = (HttpExchangeMessage) context.getMessage();
        byte[] payload = message.payloadAsBytes();
        ObjectMapper mapper = ObjectMappers.JSON_MAPPER;

        Header header = message.getHeader(HttpHeaders.AUTHORIZATION).orElse(null);
        if (header == null || header.getValue() == null || header.getValue().length == 0) {
            return message
                    .response(unauthorized)
                    .thenMany(Mono.empty());
        }

        String[] token = header.getValue()[0].split(" ");
        if (token.length == 1) {
            return message
                    .response(unauthorized)
                    .thenMany(Mono.empty());
        }
        String basicToken = token[1];

        String[] paths = TopicMessageCodec.removeProductPath(message.getPath());
        if (paths.length < 1) {
            return message
                    .response(badRequest)
                    .thenMany(Mono.empty());
        }
        String deviceId = paths[1];
        return context
                .getDevice(deviceId)
                .flatMap(device -> device.getConfig("bearer_token"))
                .filter(value -> Objects.equals(value.asString(), basicToken))
                .flatMapMany(ignore -> TopicMessageCodec.decode(mapper, paths, payload))
                .switchOnFirst((s, flux) -> {
                    Mono<Void> handler;
                    //有结果则认为成功
                    if (s.hasValue()) {
                        handler = message.ok("{\"success\":true}");
                    } else {
                        return message
                                .response(unauthorized)
                                .then(Mono.error(new BusinessException("设备[" + deviceId + "]未激活或token [" + basicToken + "]错误")));
                    }
                    return handler.thenMany(flux);
                })
                .onErrorResume(err -> message
                        .error(500, getErrorMessage(err))
                        .then(Mono.error(err)))
                //跟踪信息
                .as(FluxTracer
                            .create(DeviceTracer.SpanName.decode(deviceId),
                                    builder -> builder.setAttribute(DeviceTracer.SpanKey.message, message.print())));

    }

    public String getErrorMessage(Throwable err) {
        if (err instanceof JsonParseException) {
            return "{\"success\":false,\"code\":\"request_body_format_error\"}";
        }
        return "{\"success\":false,\"code\":\"server_error\"}";
    }

}
