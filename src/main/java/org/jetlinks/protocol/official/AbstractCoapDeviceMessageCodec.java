package org.jetlinks.protocol.official;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;
import org.reactivestreams.Publisher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractCoapDeviceMessageCodec extends BlockingDeviceMessageCodec {

    public AbstractCoapDeviceMessageCodec(ServiceContext context, Transport transport) {
        super(context, transport);
    }

    protected abstract DeviceMessage decode(CoapMessage message,
                                            BlockingMessageDecodeContext context,
                                            Consumer<Object> response);

    protected String getPath(CoapMessage message) {
        String path = message.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    protected String getDeviceId(CoapMessage message) {
        String deviceId = message.getStringOption(2100).orElse(null);
        String[] paths = TopicMessageCodec.removeProductPath(getPath(message));
        if (!StringUtils.hasText(deviceId) && paths.length > 1) {
            deviceId = paths[1];
        }
        return deviceId;
    }


    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        if (context.getData() instanceof CoapExchangeMessage) {
            CoapExchangeMessage exchangeMessage = ((CoapExchangeMessage) context.getData());
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
            try {
                context.sendToPlatformLater(
                    this.decode(exchangeMessage, context, responseHandler)
                );
                context.async(
                    Mono.fromRunnable(() -> responseHandler.accept(CoAP.ResponseCode.CREATED))
                );
            } catch (Throwable err) {
                responseHandler.accept(CoAP.ResponseCode.BAD_REQUEST);
            }
        }
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        // 不支持下发
    }

}
