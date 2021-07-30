package org.jetlinks.protocol.official;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.reactivestreams.Publisher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractCoapDeviceMessageCodec implements DeviceMessageCodec {

    protected abstract Flux<DeviceMessage> decode(CoapMessage message, MessageDecodeContext context, Consumer<Object> response);

    protected String getPath(CoapMessage message){
        String path = message.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    protected String getDeviceId(CoapMessage message){
        String deviceId = message.getStringOption(2100).orElse(null);
        String[] paths = TopicMessageCodec.removeProductPath(getPath(message));
        if (StringUtils.isEmpty(deviceId) && paths.length > 1) {
            deviceId = paths[1];
        }
        return deviceId;
    }

    @Nonnull
    @Override
    public Flux<DeviceMessage> decode(@Nonnull MessageDecodeContext context) {
        if (context.getMessage() instanceof CoapExchangeMessage) {
            CoapExchangeMessage exchangeMessage = ((CoapExchangeMessage) context.getMessage());
            AtomicBoolean alreadyReply = new AtomicBoolean();
            Consumer<Object> responseHandler = (resp) -> {
                if (alreadyReply.compareAndSet(false, true)) {
                    if (resp instanceof CoAP.ResponseCode) {
                        exchangeMessage.getExchange().respond(((CoAP.ResponseCode) resp));
                    }
                    if (resp instanceof String) {
                        exchangeMessage.getExchange().respond(((String) resp));
                    }
                    if (resp instanceof byte[]) {
                        exchangeMessage.getExchange().respond(CoAP.ResponseCode.CONTENT, ((byte[]) resp));
                    }
                }
            };

            return this
                    .decode(exchangeMessage, context, responseHandler)
                    .doOnComplete(() -> responseHandler.accept(CoAP.ResponseCode.CREATED))
                    .doOnError(error -> {
                        log.error("decode coap message error", error);
                        responseHandler.accept(CoAP.ResponseCode.BAD_REQUEST);
                    })
                    .switchIfEmpty(Mono.fromRunnable(() -> responseHandler.accept(CoAP.ResponseCode.BAD_REQUEST)));
        }
        if (context.getMessage() instanceof CoapMessage) {
            return decode(((CoapMessage) context.getMessage()), context, resp -> {
                log.info("skip response coap request:{}", resp);
            });
        }

        return Flux.empty();
    }

    @Nonnull
    @Override
    public Publisher<? extends EncodedMessage> encode(@Nonnull MessageEncodeContext context) {
        return Mono.empty();
    }
}
