package org.jetlinks.protocol.official.http;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.device.*;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DisconnectDeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.message.codec.http.Header;
import org.jetlinks.core.message.codec.http.HttpExchangeMessage;
import org.jetlinks.core.message.codec.http.SimpleHttpResponseMessage;
import org.jetlinks.core.message.codec.http.websocket.DefaultWebSocketMessage;
import org.jetlinks.core.message.codec.http.websocket.WebSocketMessage;
import org.jetlinks.core.message.codec.http.websocket.WebSocketSessionMessage;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.principal.CredentialType;
import org.jetlinks.core.principal.Identity;
import org.jetlinks.core.principal.Principal;
import org.jetlinks.core.principal.TokenCredential;
import org.jetlinks.core.spi.EmptyServiceContext;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.protocol.official.ObjectMappers;
import org.jetlinks.protocol.official.TopicMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingDevicePrincipal;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/**
 * Http 的消息编解码器
 *
 * @author zhouhao
 * @since 3.0.0
 */
public class JetLinksHttpDeviceMessageCodec extends BlockingDeviceMessageCodec implements Authenticator {

    public static final String identityType = "j_ws";

    public static final ConfigKey<String> KEY_ACCESS_ID = ConfigKey.of("httpAccessId");

    public static final DefaultConfigMetadata httpConfig = new DefaultConfigMetadata("HTTP认证配置", "")
        .add(KEY_ACCESS_ID.getKey(), "AccessId", "AccessId", new PasswordType());

    public static final DefaultConfigMetadata webSocketConfig = new DefaultConfigMetadata(
        "WebSocket认证配置"
        , "")
        .add(KEY_ACCESS_ID.getKey(), "AccessId", "AccessId", new PasswordType());

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
        String deviceId = context.getMessage().getDeviceId();
        JSONObject json = context.getMessage().toJson();
        if (context.getMessage() instanceof DisconnectDeviceMessage) {
            tracer(deviceId)
                    .traceBlocking(DeviceTracer.OperationName.encode, span -> {
                        span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                        span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");
                        span.setAttribute(DeviceTracer.SpanKey.input, json.toJSONString());
                        span.setAttribute(DeviceTracer.SpanKey.tag, "平台主动断开连接");
                        context.disconnect();
                        return null;
                    });
            return;
        }

        EncodedMessage encodedMessage = tracer(deviceId)
                .traceBlocking(DeviceTracer.OperationName.encode, _span -> {
                    // 设备ID
                    _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据下发");

                    if (logger(deviceId).isTraceEnabled()) {
                        logger(deviceId).debug(
                            "使用TEXT数据类型，下发json报文：{}", json.toJSONString()
                        );
                    }

                    DefaultWebSocketMessage msg = DefaultWebSocketMessage.of(
                            WebSocketMessage.Type.TEXT,
                            Unpooled.wrappedBuffer(json.toJSONString().getBytes()));
                    TopicMessageCodec codec = TopicMessageCodec.lookup(context.getMessage().getClass());
                    if (codec != null) {
                        _span.setAttribute(DeviceTracer.SpanKey.tag, codec.getRoute().getGroup());
                    }
                    //  输出报文
                    _span.setAttribute(DeviceTracer.SpanKey.output, msg.payloadAsString());
                    return msg;
                });

        //转为json 发送给设备
        context.sendToDeviceLater(encodedMessage);

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

        DeviceMessage message = tracer()
                .traceBlocking(DeviceTracer.OperationName.decode, _span -> {
                    DeviceMessage _msg = (DeviceMessage) MessageType
                            .convertMessage(msg.payloadAsJson())
                            .orElse(null);
                    // 原始报文
                    _span.setAttribute(DeviceTracer.SpanKey.input, msg.payloadAsString());
                    // 详细信息
                    _span.setAttribute(DeviceTracer.SpanKey.message, "数据上报");

                    if (_msg != null) {
                        // 设备ID
                        _span.setAttribute(DeviceTracer.SpanKey.deviceId, _msg.getDeviceId());
                        // 输出报文
                        _span.setAttribute(DeviceTracer.SpanKey.output, _msg.toJson().toString());
                    } else {
                        logger().warn("输出消息为空");
                    }
                    return _msg;
                });

        context.sendToPlatformLater(message);

    }

    private void decodeHttp(BlockingMessageDecodeContext context) {
        HttpExchangeMessage exchange = (HttpExchangeMessage) context.getData();

        //校验请求头中的Authorization header,格式:
        // Authorization: Bearer <token>
        Header header = exchange.getHeader(HttpHeaders.AUTHORIZATION).orElse(null);
        if (header == null || header.getValue() == null || header.getValue().length == 0) {
            logger().warn("请求头不能为空");
            context.async(
                exchange
                    .response(unauthorized("Authorization header is required"))
            );

            return;
        }
        // Bearer <token>
        String[] token = header.getValue()[0].split(" ");
        if (token.length == 1) {
            logger().warn("token格式错误，token:{}", header.getValue()[0]);
            context.async(
                exchange
                    .response(unauthorized("Illegal token format"))
            );
            return;
        }
        String basicToken = token[1];
        // 移除产品前缀
        if (logger().isDebugEnabled()) {
            logger().debug("移除uri中的产品前缀。原始值：{}", exchange.getPath());
        }
        String[] paths = TopicMessageCodec.removeProductPath(exchange.getPath());
        if (paths.length < 1) {
            logger().warn("path解析错误，path为空");
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
            logger().warn("设备不存在，id：{}", deviceId);
            context.async(
                exchange
                    .response(unauthorized("Device no register"))
            );
            return;
        }

        BlockingDevicePrincipal principal = context.resolveDevice(
            Principal.create(
                Identity.create(identityType, deviceId),
                TokenCredential.create(basicToken)
            )
        );

        // token不正确
        if (principal == null || !principal.isVerified()) {
            logger(deviceId)
                .warn("token不匹配，device:{},token:{}", deviceId, basicToken);
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
                    .mapNotNull(payload -> tracer(deviceId)
                            .traceBlocking(DeviceTracer.OperationName.decode, _span -> {
                                String input = ByteBufUtil.hexDump(payload);
                                if (logger(deviceId).isDebugEnabled()) {
                                    logger(deviceId).debug(
                                            "请求原始报文：{}", ByteBufUtil.hexDump(payload)
                                    );
                                }
                                // 原始报文
                                _span.setAttribute(DeviceTracer.SpanKey.input, input);
                                // 设备ID
                                _span.setAttribute(DeviceTracer.SpanKey.deviceId, deviceId);
                                // 详细信息
                                _span.setAttribute(DeviceTracer.SpanKey.message, "数据上报");

                                byte[] bytes = ByteBufUtil.getBytes(payload);
                                DeviceMessage msg = TopicMessageCodec.decode(
                                        ObjectMappers.JSON_MAPPER, paths, bytes, _span, logger(deviceId)
                                );
                                if (msg != null) {
                                    // 输出报文
                                    _span.setAttribute(DeviceTracer.SpanKey.output, msg.toJson().toString());
                                } else {
                                    logger(deviceId).warn("输出消息为空");
                                }
                                return msg;
                            }))
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

//    private DeviceMessage doDecode(HttpExchangeMessage message, String[] paths) {
//        ByteBuf body = await(message.payload());
//
//        if (body == null) {
//            body = Unpooled.EMPTY_BUFFER;
//        }
//
//        byte[] bytes = ByteBufUtil.getBytes(body);
//
//        return TopicMessageCodec.decode(ObjectMappers.JSON_MAPPER, paths, bytes);
//    }

    public String getErrorMessage(Throwable err) {
        if (err instanceof JsonParseException) {
            return "{\"success\":false,\"code\":\"request_body_format_error\"}";
        }
        return "{\"success\":false,\"code\":\"server_error\"}";
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator device) {
       return Mono
               .defer(() -> {
                   if (!(request instanceof WebsocketAuthenticationRequest)) {
                       return Mono.just(AuthenticationResponse.error(400, "不支持的认证方式"));
                   }
                   WebsocketAuthenticationRequest req = ((WebsocketAuthenticationRequest) request);
                   Map<String, String> params = req.getSocketSession().getQueryParameters();
                   String accessId = params.get("accessId");
                   String accessToken = params.get("accessToken");
                   if (logger(device.getDeviceId()).isDebugEnabled()) {
                       logger(device.getDeviceId()).info(
                               "开始认证，请求参数：accessId：{}，accessToken：{}", accessId, accessToken
                       );
                   }

                   if (accessId == null || accessToken == null) {
                       return Mono.just(AuthenticationResponse.error(401, "缺少认证信息"));
                   }

                   return device
                           .getCredential(
                                   Identity.create(identityType, accessId),
                                   CredentialType.token
                           )
                           .map(credential -> {
                               String accessTokenCredential = credential.unwrap(TokenCredential.class).getAccessToken();
                               if (logger(device.getDeviceId()).isDebugEnabled()) {
                                   logger(device.getDeviceId()).debug(
                                           "成功设备接入身份凭证信息，accessToken：{}", accessTokenCredential
                                   );
                               }
                               // 对比token
                               if (Objects.equals(credential.unwrap(TokenCredential.class).getAccessToken(),
                                                  accessToken)) {
                                   return AuthenticationResponse.success(device.getDeviceId());
                               }
                               return AuthenticationResponse.error(401, "认证信息错误");
                           })
                           .switchIfEmpty(Mono.fromSupplier(() -> AuthenticationResponse.error(401, "token错误")));
               })
               .as(tracer()
                           .traceMono(DeviceTracer.OperationName.auth, (ctx, _span) -> {
                               _span.setAttribute(DeviceTracer.SpanKey.message, "设备身份token认证");
                               _span.setAttribute(DeviceTracer.SpanKey.tag, "HTTP推送");
                           }));
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        return Mono
                .defer(() -> {
                    if (!(request instanceof WebsocketAuthenticationRequest)) {
                        return Mono.just(AuthenticationResponse.error(400, "不支持的认证方式"));
                    }
                    WebsocketAuthenticationRequest req = ((WebsocketAuthenticationRequest) request);
                    Map<String, String> params = req.getSocketSession().getQueryParameters();
                    String accessId = params.get("accessId");
                    String accessToken = params.get("accessToken");
                    if (logger().isDebugEnabled()) {
                        logger().info(
                                "开始认证，请求参数：accessId：{}，accessToken：{}", accessId, accessToken
                        );
                    }

                    if (accessId == null || accessToken == null) {
                        return Mono.just(AuthenticationResponse.error(401, "缺少认证信息"));
                    }

                    return registry
                            .resolveDevice(
                                    Principal.create(
                                            Identity.create(identityType, accessId),
                                            TokenCredential.create(accessToken)
                                    ))
                            .map(principal -> {
                                String deviceId = principal.getDevice().getDeviceId();
                                String accessTokenCredential = principal
                                        .credential()
                                        .unwrap(TokenCredential.class)
                                        .getAccessToken();
                                if (logger(deviceId).isDebugEnabled()) {
                                    logger(deviceId).debug(
                                            "成功设备接入身份凭证信息，deviceId：{}，accessToken：{}", deviceId, accessTokenCredential
                                    );
                                }
                                // 对比token
                                if (Objects.equals(accessTokenCredential, accessToken)) {
                                    return AuthenticationResponse.success(deviceId);
                                }
                                return AuthenticationResponse.error(401, "认证信息错误");
                            })
                            .switchIfEmpty(Mono.fromSupplier(() -> AuthenticationResponse.error(401, "token错误")));
                })
                .as(tracer()
                            .traceMono(DeviceTracer.OperationName.auth, (ctx, _span) -> {
                                _span.setAttribute(DeviceTracer.SpanKey.message, "设备身份token认证");
                                _span.setAttribute(DeviceTracer.SpanKey.tag, "HTTP推送");
                            }));
    }
}
