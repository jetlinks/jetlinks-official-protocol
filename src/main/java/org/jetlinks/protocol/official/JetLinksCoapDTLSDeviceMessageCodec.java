package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.CoapMessage;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.MessageDecodeContext;
import org.jetlinks.core.message.codec.Transport;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.function.Consumer;

@Slf4j
public class JetLinksCoapDTLSDeviceMessageCodec extends AbstractCoapDeviceMessageCodec {

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.CoAP_DTLS;
    }


    public Flux<DeviceMessage> decode(CoapMessage message, MessageDecodeContext context, Consumer<Object> response) {
        if (context.getDevice() == null) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            String path = getPath(message);
            String deviceId = getDeviceId(message);
            String sign = message.getStringOption(2110).orElse(null);
            String token = message.getStringOption(2111).orElse(null);
            byte[] payload = message.payloadAsBytes();
            boolean cbor = message
                    .getStringOption(OptionNumberRegistry.CONTENT_FORMAT)
                    .map(MediaType::valueOf)
                    .map(MediaType.APPLICATION_CBOR::includes)
                    .orElse(false);
            ObjectMapper objectMapper = cbor ? ObjectMappers.CBOR_MAPPER : ObjectMappers.JSON_MAPPER;

            if (StringUtils.isEmpty(deviceId)) {
                response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                return Mono.empty();
            }
            // TODO: 2021/7/30 移到 FunctionalTopicHandlers
            if (path.endsWith("/request-token")) {
                //认证
                return context
                        .getDevice(deviceId)
                        .switchIfEmpty(Mono.fromRunnable(() -> response.accept(CoAP.ResponseCode.UNAUTHORIZED)))
                        .flatMap(device -> device
                                .getConfig("secureKey")
                                .flatMap(sk -> {
                                    String secureKey = sk.asString();
                                    if (!verifySign(secureKey, deviceId, payload, sign)) {
                                        response.accept(CoAP.ResponseCode.BAD_REQUEST);
                                        return Mono.empty();
                                    }
                                    String newToken = IDGenerator.MD5.generate();
                                    return device
                                            .setConfig("coap-token", newToken)
                                            .doOnSuccess(success -> {
                                                JSONObject json = new JSONObject();
                                                json.put("token", newToken);
                                                response.accept(json.toJSONString());
                                            });
                                }))
                        .then(Mono.empty());
            }
            if (StringUtils.isEmpty(token)) {
                response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                return Mono.empty();
            }
            return context
                    .getDevice(deviceId)
                    .flatMapMany(device -> device
                            .getSelfConfig("coap-token")
                            .switchIfEmpty(Mono.fromRunnable(() -> response.accept(CoAP.ResponseCode.UNAUTHORIZED)))
                            .flatMapMany(value -> {
                                String tk = value.asString();
                                if (!token.equals(tk)) {
                                    response.accept(CoAP.ResponseCode.UNAUTHORIZED);
                                    return Mono.empty();
                                }
                                return TopicMessageCodec
                                        .decode(objectMapper, TopicMessageCodec.removeProductPath(path), payload)
                                        //如果不能直接解码，可能是其他设备功能
                                        .switchIfEmpty(FunctionalTopicHandlers
                                                               .handle(device,
                                                                       path.split("/"),
                                                                       payload,
                                                                       objectMapper,
                                                                       reply -> Mono.fromRunnable(() -> response.accept(reply.getPayload()))));
                            }))

                    .doOnComplete(() -> response.accept(CoAP.ResponseCode.CREATED))
                    .doOnError(error -> {
                        log.error("decode coap message error", error);
                        response.accept(CoAP.ResponseCode.BAD_REQUEST);
                    });
        });

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


}
