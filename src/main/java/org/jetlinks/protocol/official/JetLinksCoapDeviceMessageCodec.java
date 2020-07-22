package org.jetlinks.protocol.official;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.jetlinks.core.Value;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.protocol.official.cipher.Ciphers;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

@Slf4j
public class JetLinksCoapDeviceMessageCodec extends JetlinksTopicMessageCodec implements DeviceMessageCodec {


    @Override
    public Transport getSupportTransport() {
        return DefaultTransport.CoAP;
    }

    protected JSONObject decode(String text) {
        return JSON.parseObject(text);
    }

    protected Mono<? extends Message> decode(CoapMessage message, MessageDecodeContext context) {
        String path = message.getPath();
        return context
                .getDevice()
                .getConfigs("encAlg", "secureKey")
                .flatMap(configs -> {
                    Ciphers ciphers = configs.getValue("encAlg").map(Value::asString).flatMap(Ciphers::of).orElse(Ciphers.AES);
                    String secureKey = configs.getValue("secureKey").map(Value::asString).orElse(null);
                    ByteBuf byteBuf = message.getPayload();
                    byte[] req = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(req);
                    byteBuf.resetReaderIndex();
                    String payload = new String(ciphers.decrypt(req, secureKey));
                    //解码
                    return Mono.just(decode(path, decode(payload)).getMessage());
                });
    }

    protected Mono<? extends Message> decode(CoapExchangeMessage message, MessageDecodeContext context) {
        CoapExchange exchange = message.getExchange();
        return decode((CoapMessage) message, context)
                .doOnSuccess(msg -> {
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
    public Mono<? extends Message> decode(@Nonnull MessageDecodeContext context) {
        return Mono.defer(() -> {
            log.debug("handle coap message:\n{}", context.getMessage());
            if (context.getMessage() instanceof CoapExchangeMessage) {
                return decode(((CoapExchangeMessage) context.getMessage()), context);
            }
            if (context.getMessage() instanceof CoapMessage) {
                return decode(((CoapMessage) context.getMessage()), context);
            }

            return Mono.empty();
        });
    }

    @Nonnull
    @Override
    public Mono<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.empty();
    }
}
