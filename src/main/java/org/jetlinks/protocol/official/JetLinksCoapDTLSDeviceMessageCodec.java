package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.californium.core.coap.CoAP;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Slf4j
public class JetLinksCoapDTLSDeviceMessageCodec extends JetlinksTopicMessageCodec implements DeviceMessageCodec {

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.CoAP_DTLS;
    }


    public Mono<? extends Message> decode(CoapMessage message, MessageDecodeContext context, Consumer<Object> response) {
        return Mono.defer(() -> {
            String path = message.getPath();
            String sign = message.getStringOption(2110).orElse(null);
            String token = message.getStringOption(2111).orElse(null);
            String payload = message.getPayload().toString(StandardCharsets.UTF_8);
            if ("/auth".equals(path)) {
                //认证
                return context.getDevice()
                        .getConfig("secureKey")
                        .flatMap(sk -> {
                            String secureKey = sk.asString();
                            if (!verifySign(secureKey, context.getDevice().getDeviceId(), payload, sign)) {
                                response.accept(CoAP.ResponseCode.BAD_REQUEST);
                                return Mono.empty();
                            }
                            String newToken = IDGenerator.MD5.generate();
                            return context.getDevice()
                                    .setConfig("coap-token", newToken)
                                    .doOnSuccess(success -> {
                                        JSONObject json = new JSONObject();
                                        json.put("token", newToken);
                                        response.accept(json.toJSONString());
                                    });
                        })
                        .then(Mono.empty());
            }
            if (StringUtils.isEmpty(token)) {
                response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                return Mono.empty();
            }
            return context.getDevice()
                    .getConfig("coap-token")
                    .switchIfEmpty(Mono.fromRunnable(() -> {
                        response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                    }))
                    .flatMap(value -> {
                        String tk = value.asString();
                        if (!token.equals(tk)) {
                            response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                            return Mono.empty();
                        }
                        return Mono
                                .just(decode(path, JSON.parseObject(payload)).getMessage())
                                .switchIfEmpty(Mono.fromRunnable(() -> response.accept(CoAP.ResponseCode.BAD_REQUEST)));
                    })
                    .doOnSuccess(msg -> {
                        response.accept(CoAP.ResponseCode.CREATED);
                    })
                    .doOnError(error -> {
                        log.error("decode coap message error", error);
                        response.accept(CoAP.ResponseCode.BAD_REQUEST);
                    });
        });

    }

    @Nonnull
    @Override
    public Mono<? extends Message> decode(@Nonnull MessageDecodeContext context) {
        if (context.getMessage() instanceof CoapExchangeMessage) {
            CoapExchangeMessage exchangeMessage = ((CoapExchangeMessage) context.getMessage());
            return decode(exchangeMessage, context, resp -> {
                if (resp instanceof CoAP.ResponseCode) {
                    exchangeMessage.getExchange().respond(((CoAP.ResponseCode) resp));
                }
                if (resp instanceof String) {
                    exchangeMessage.getExchange().respond(((String) resp));
                }
            });
        }
        if (context.getMessage() instanceof CoapMessage) {
            return decode(((CoapMessage) context.getMessage()), context, resp -> {
                log.info("skip response coap request:{}", resp);
            });
        }

        return Mono.empty();
    }

    protected boolean verifySign(String secureKey, String deviceId, String payload, String sign) {
        //验证签名
        if (StringUtils.isEmpty(secureKey) || !DigestUtils.md5Hex(payload.concat(secureKey)).equalsIgnoreCase(sign)) {
            log.info("device [{}] coap sign [{}] error, payload:{}", deviceId, sign, payload);
            return false;
        }
        return true;
    }

    @Nonnull
    @Override
    public Mono<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.empty();
    }
}
