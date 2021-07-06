package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Consumer;

@Slf4j
public class JetLinksCoapDTLSDeviceMessageCodec implements DeviceMessageCodec {

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.CoAP_DTLS;
    }


    public Flux<DeviceMessage> decode(CoapMessage message, MessageDecodeContext context, Consumer<Object> response) {
        return Flux.defer(() -> {
            String path = message.getPath();
            String sign = message.getStringOption(2110).orElse(null);
            String token = message.getStringOption(2111).orElse(null);
            byte[] payload = message.payloadAsBytes();
            boolean cbor = message
                    .getStringOption(OptionNumberRegistry.CONTENT_FORMAT)
                    .map(MediaType::valueOf)
                    .map(MediaType.APPLICATION_CBOR::includes)
                    .orElse(false);
            ObjectMapper objectMapper = cbor ? ObjectMappers.CBOR_MAPPER : ObjectMappers.JSON_MAPPER;
            if ("/auth".equals(path)) {
                //认证
                return context
                        .getDevice()
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
            return context
                    .getDevice()
                    .getConfig("coap-token")
                    .switchIfEmpty(Mono.fromRunnable(() -> response.accept(CoAP.ResponseCode.UNAUTHORIZED)))
                    .flatMapMany(value -> {
                        String tk = value.asString();
                        if (!token.equals(tk)) {
                            response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                            return Mono.empty();
                        }
                        return TopicMessageCodec
                                .decode(objectMapper, TopicMessageCodec.removeProductPath(path), payload)
                                .switchIfEmpty(Mono.fromRunnable(() -> response.accept(CoAP.ResponseCode.BAD_REQUEST)));
                    })
                    .doOnComplete(() -> {
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
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
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

        return Flux.empty();
    }

    protected boolean verifySign(String secureKey, String deviceId, byte[] payload, String sign) {
        //验证签名
        byte[] secureKeyBytes = secureKey.getBytes();
        byte[] signPayload = Arrays.copyOf(payload, payload.length + secureKeyBytes.length);
        System.arraycopy(secureKeyBytes, 0, signPayload, 0, secureKeyBytes.length);
        if (StringUtils.isEmpty(secureKey) || !DigestUtils.md5Hex(signPayload).equalsIgnoreCase(sign)) {
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
