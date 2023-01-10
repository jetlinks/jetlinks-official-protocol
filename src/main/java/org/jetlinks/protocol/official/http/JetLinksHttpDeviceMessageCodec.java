package org.jetlinks.protocol.official.http;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.device.*;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.message.codec.http.Header;
import org.jetlinks.core.message.codec.http.HttpExchangeMessage;
import org.jetlinks.core.message.codec.http.SimpleHttpResponseMessage;
import org.jetlinks.core.message.codec.http.websocket.DefaultWebSocketMessage;
import org.jetlinks.core.message.codec.http.websocket.WebSocketMessage;
import org.jetlinks.core.message.codec.http.websocket.WebSocketSessionMessage;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.protocol.official.ObjectMappers;
import org.jetlinks.protocol.official.TopicMessageCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
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
public class JetLinksHttpDeviceMessageCodec implements DeviceMessageCodec, Authenticator {
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
    public Mono<EncodedMessage> encode(@Nonnull MessageEncodeContext context) {

        JSONObject json = context.getMessage().toJson();
        //通过websocket下发
        return Mono.just(DefaultWebSocketMessage.of(
                WebSocketMessage.Type.TEXT,
                Unpooled.wrappedBuffer(json.toJSONString().getBytes())));
    }

    private static SimpleHttpResponseMessage unauthorized(String msg) {
        return SimpleHttpResponseMessage
                .builder()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"code\":\"unauthorized\",\"message\":\"" + msg + "\"}")
                .status(401)
                .build();
    }


    private static SimpleHttpResponseMessage badRequest() {
        return SimpleHttpResponseMessage
                .builder()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"code\":\"bad_request\"}")
                .status(400)
                .build();
    }

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        if (context.getMessage() instanceof HttpExchangeMessage) {
            return decodeHttp(context);
        }

        if (context.getMessage() instanceof WebSocketSessionMessage) {
            return decodeWebsocket(context);
        }

        return Flux.empty();
    }

    private Flux<DeviceMessage> decodeWebsocket(MessageDecodeContext context) {
        WebSocketSessionMessage msg = ((WebSocketSessionMessage) context.getMessage());

        return Mono
                .justOrEmpty(MessageType.convertMessage(msg.payloadAsJson()))
                .cast(DeviceMessage.class)
                .flux();

    }

    private Flux<DeviceMessage> decodeHttp(MessageDecodeContext context) {
        HttpExchangeMessage message = (HttpExchangeMessage) context.getMessage();

        //校验请求头中的Authorization header,格式:
        // Authorization: Bearer <token>
        Header header = message.getHeader(HttpHeaders.AUTHORIZATION).orElse(null);
        if (header == null || header.getValue() == null || header.getValue().length == 0) {
            return message
                    .response(unauthorized("Authorization header is required"))
                    .thenMany(Mono.empty());
        }

        String[] token = header.getValue()[0].split(" ");
        if (token.length == 1) {
            return message
                    .response(unauthorized("Illegal token format"))
                    .thenMany(Mono.empty());
        }
        String basicToken = token[1];

        String[] paths = TopicMessageCodec.removeProductPath(message.getPath());
        if (paths.length < 1) {
            return message
                    .response(badRequest())
                    .thenMany(Mono.empty());
        }
        String deviceId = paths[1];
        return context
                .getDevice(deviceId)
                .flatMap(device -> device.getConfig("bearer_token"))
                //校验token
                .filter(value -> Objects.equals(value.asString(), basicToken))
                //设备或者配置不对
                .switchIfEmpty(Mono.defer(() -> message
                        .response(unauthorized("Device no register or token not match"))
                        .then(Mono.empty())))
                //解码
                .flatMapMany(ignore -> doDecode(message, paths))
                .switchOnFirst((s, flux) -> {
                    Mono<Void> handler;
                    //有结果则认为成功
                    if (s.hasValue()) {
                        handler = message.ok("{\"success\":true}");
                    } else {
                        return message
                                .response(badRequest())
                                .then(Mono.empty());
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

    private Flux<DeviceMessage> doDecode(HttpExchangeMessage message, String[] paths) {
        return message
                .payload()
                .flatMapMany(buf -> {
                    byte[] body = ByteBufUtil.getBytes(buf);
                    return TopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, paths, body);
                });
    }

    public String getErrorMessage(Throwable err) {
        if (err instanceof JsonParseException) {
            return "{\"success\":false,\"code\":\"request_body_format_error\"}";
        }
        return "{\"success\":false,\"code\":\"server_error\"}";
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator device) {
        if (!(request instanceof WebsocketAuthenticationRequest)) {
            return Mono.just(AuthenticationResponse.error(400, "不支持的认证方式"));
        }
        WebsocketAuthenticationRequest req = ((WebsocketAuthenticationRequest) request);
        String token = req
                .getSocketSession()
                .getQueryParameters()
                .get("token");

        if (StringUtils.isEmpty(token)) {
            return Mono.just(AuthenticationResponse.error(401, "认证参数错误"));
        }

        return device
                .getConfig("bearer_token")
                //校验token
                .filter(value -> Objects.equals(value.asString(), token))
                .map(ignore -> AuthenticationResponse.success(device.getDeviceId()))
                //未配置或者配置不对
                .switchIfEmpty(Mono.fromSupplier(() -> AuthenticationResponse.error(401, "token错误")));
    }

    static AuthenticationResponse deviceNotFound = AuthenticationResponse.error(404, "设备不存在");

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        if (!(request instanceof WebsocketAuthenticationRequest)) {
            return Mono.just(AuthenticationResponse.error(400, "不支持的认证方式"));
        }
        WebsocketAuthenticationRequest req = ((WebsocketAuthenticationRequest) request);
        String[] paths = TopicMessageCodec.removeProductPath(req.getSocketSession().getPath());
        if (paths.length < 1) {
            return Mono.just(AuthenticationResponse.error(400, "URL格式错误"));
        }

        return registry
                .getDevice(paths[1])
                .flatMap(device -> authenticate(request, device))
                .defaultIfEmpty(deviceNotFound);

    }
}
