package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.jetlinks.core.Value;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.protocol.official.cipher.Ciphers;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

@Slf4j
public class JetLinksCoapDeviceMessageCodec implements DeviceMessageCodec {

    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.CoAP;
    }

    protected Flux<DeviceMessage> decode(CoapMessage message, MessageDecodeContext context) {
        String path =  message.getPath();

        boolean cbor = message
                .getStringOption(OptionNumberRegistry.CONTENT_FORMAT)
                .map(MediaType::valueOf)
                .map(MediaType.APPLICATION_CBOR::includes)
                .orElse(false);
        ObjectMapper objectMapper = cbor ? ObjectMappers.CBOR_MAPPER : ObjectMappers.JSON_MAPPER;
        return context
                .getDevice()
                .getConfigs("encAlg", "secureKey")
                .flatMapMany(configs -> {
                    Ciphers ciphers = configs
                            .getValue("encAlg")
                            .map(Value::asString)
                            .flatMap(Ciphers::of)
                            .orElse(Ciphers.AES);
                    String secureKey = configs.getValue("secureKey").map(Value::asString).orElse(null);
                    ByteBuf byteBuf = message.getPayload();
                    byte[] req = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(req);
                    byteBuf.resetReaderIndex();
                    byte[] payload = ciphers.decrypt(req, secureKey);
                    //解码
                    return TopicMessageCodec.decode(objectMapper, TopicMessageCodec.removeProductPath(path), payload);
                });
    }

    protected Flux<DeviceMessage> decode(CoapExchangeMessage message, MessageDecodeContext context) {
        CoapExchange exchange = message.getExchange();
        return decode(((CoapMessage) message), context)
                .doOnComplete(() -> {
                    exchange.respond(CoAP.ResponseCode.CREATED);
                    exchange.accept();
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                }))
                .doOnError(error -> {
                    log.error("decode coap message error", error);
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                });
    }

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        return Flux.defer(() -> {
            log.debug("handle coap message:\n{}", context.getMessage());
            if (context.getMessage() instanceof CoapExchangeMessage) {
                return decode(((CoapExchangeMessage) context.getMessage()), context);
            }
            if (context.getMessage() instanceof CoapMessage) {
                return decode(((CoapMessage) context.getMessage()), context);
            }

            return Flux.empty();
        });
    }

    @Nonnull
    @Override
    public Mono<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.empty();
    }
}
