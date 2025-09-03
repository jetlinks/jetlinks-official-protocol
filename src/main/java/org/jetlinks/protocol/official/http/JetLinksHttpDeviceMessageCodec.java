package org.jetlinks.protocol.official.http;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.device.*;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DisconnectDeviceMessage;
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
import org.jetlinks.core.spi.EmptyServiceContext;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.trace.TraceHolder;
import org.jetlinks.protocol.official.ObjectMappers;
import org.jetlinks.protocol.official.TopicMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
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
public class JetLinksHttpDeviceMessageCodec extends BlockingDeviceMessageCodec implements Authenticator {

    static final ConfigKey<String> BEARER_TOKEN = ConfigKey.of("bearer_token");
    static final ConfigKey<String> WS_TOKEN = ConfigKey.of("ws_token");

    public static final DefaultConfigMetadata httpConfig = new DefaultConfigMetadata(
        "HTTP认证配置"
        , "使用HTTP Bearer Token进行认证")
        .add(BEARER_TOKEN.getKey(), "Token", "Token", new PasswordType());

    public static final DefaultConfigMetadata webSocketConfig = new DefaultConfigMetadata(
        "WebSocket认证配置"
        , "使用WebSocket param进行认证")
        .add(WS_TOKEN.getKey(), "token", "连接携带token参数", new PasswordType());

    public JetLinksHttpDeviceMessageCodec(ServiceContext context, Transport transport) {
        super(context, transport);
    }

    public JetLinksHttpDeviceMessageCodec() {
        this(EmptyServiceContext.INSTANCE, DefaultTransport.HTTP);
    }

    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        if (context.getData() instanceof HttpExchangeMessage) {
            decodeHttp(context);
        }

        if (context.getData() instanceof WebSocketSessionMessage) {
            decodeWebsocket(context);
        }
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        if (context.getMessage() instanceof DisconnectDeviceMessage) {
            return;
        }

        JSONObject json = context.getMessage().toJson();
        //转为json 发送给设备
        context.sendToDeviceLater(
            DefaultWebSocketMessage.of(
                WebSocketMessage.Type.TEXT,
                Unpooled.wrappedBuffer(json.toJSONString().getBytes()))
        );

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

    private void decodeWebsocket(BlockingMessageDecodeContext context) {
        WebSocketSessionMessage msg = ((WebSocketSessionMessage) context.getData());

        DeviceMessage message = (DeviceMessage) MessageType
            .convertMessage(msg.payloadAsJson())
            .orElse(null);

        context.sendToPlatformLater(message);

    }

    private void decodeHttp(BlockingMessageDecodeContext context) {
        HttpExchangeMessage exchange = (HttpExchangeMessage) context.getData();

        //校验请求头中的Authorization header,格式:
        // Authorization: Bearer <token>
        Header header = exchange.getHeader(HttpHeaders.AUTHORIZATION).orElse(null);
        if (header == null || header.getValue() == null || header.getValue().length == 0) {

            context.async(
                exchange
                    .response(unauthorized("Authorization header is required"))
            );

            return;
        }
        // Bearer <token>
        String[] token = header.getValue()[0].split(" ");
        if (token.length == 1) {
            context.async(
                exchange
                    .response(unauthorized("Illegal token format"))
            );
            return;
        }
        String basicToken = token[1];
        // 移除产品前缀
        String[] paths = TopicMessageCodec.removeProductPath(exchange.getPath());
        if (paths.length < 1) {
            context.async(
                exchange
                    .response(badRequest())
            );
            return;
        }

        String deviceId = paths[1];
        BlockingDeviceOperator device = context.getDevice(deviceId);
        // 设备不存在
        if (device == null) {
            context.async(
                exchange
                    .response(unauthorized("Device no register"))
            );
            return;
        }

        String deviceToken = device.getConfigNow(BEARER_TOKEN);

        // token不正确
        if (!Objects.equals(deviceToken, basicToken)) {
            logger(deviceId)
                .warn("device token not match,device:{},token:{}", deviceId, basicToken);
            context.async(
                exchange
                    .response(unauthorized("Token not match"))
            );
            return;
        }

        try {

            //解码并发送给平台
            context.async(
                exchange
                    .payload()
                    .mapNotNull(payload -> {
                        byte[] bytes = ByteBufUtil.getBytes(payload);
                        return TopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, paths, bytes);
                    })
                    .flatMap(context::sendToPlatformReactive)
                    .as(MonoTracer.create(
                        DeviceTracer.SpanName.decode0(deviceId),
                        (span) -> span.setAttributeLazy(DeviceTracer.SpanKey.message, exchange::print)))

            );

            //响应http
            context.async(
                exchange.ok("{\"success\":true}")
            );

        } catch (Throwable e) {
            context.async(
                exchange
                    .error(500, getErrorMessage(e)
                    ));
        }

    }

    private DeviceMessage doDecode(HttpExchangeMessage message, String[] paths) {
        ByteBuf body = await(message.payload());

        if (body == null) {
            body = Unpooled.EMPTY_BUFFER;
        }

        byte[] bytes = ByteBufUtil.getBytes(body);

        return TopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, paths, bytes);
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
