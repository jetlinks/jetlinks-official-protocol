package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.jetlinks.core.Value;
import org.jetlinks.core.Values;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.codec.CoapMessage;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.MessageDecodeContext;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.DeviceConfigScope;
import org.jetlinks.core.metadata.types.EnumType;
import org.jetlinks.core.metadata.types.PasswordType;
import org.jetlinks.core.spi.EmptyServiceContext;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.core.trace.DeviceTracer;
import org.jetlinks.protocol.official.cipher.Ciphers;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

public class JetLinksCoapDeviceMessageCodec extends AbstractCoapDeviceMessageCodec {
    public static final DefaultConfigMetadata coapConfig = new DefaultConfigMetadata(
        "CoAP认证配置",
        "使用CoAP进行数据上报时,需要对数据进行加密:" +
            "encrypt(payload,secureKey);")
        .add("encAlg", "加密算法", "加密算法", new EnumType()
            .addElement(EnumType.Element.of("AES", "AES加密(ECB,PKCS#5)", "加密模式:ECB,填充方式:PKCS#5")), DeviceConfigScope.product)
        .add("secureKey", "密钥", "16位密钥KEY", new PasswordType());

    public JetLinksCoapDeviceMessageCodec(ServiceContext context, Transport transport) {
        super(context, transport);
    }

    public JetLinksCoapDeviceMessageCodec() {
        this(EmptyServiceContext.INSTANCE, DefaultTransport.CoAP);
    }


    protected DeviceMessage decode(CoapMessage message,
                                   BlockingMessageDecodeContext context,
                                   Consumer<Object> response) {
        String path = getPath(message);
        String deviceId = getDeviceId(message);
        // content type
        boolean cbor = message
            .getOption(OptionNumberRegistry.CONTENT_FORMAT)
            .map(option -> {
                String contentType = MediaTypeRegistry.toString(option.getIntegerValue());
                MediaType mediaType = MediaType.valueOf(contentType);
                return MediaType.APPLICATION_CBOR.includes(mediaType);
            })
            .orElse(false);
        ObjectMapper objectMapper = cbor ? ObjectMappers.CBOR_MAPPER : ObjectMappers.JSON_MAPPER;

        BlockingDeviceOperator device = context.getDevice(deviceId);

        if (device == null) {
            return null;
        }

        //链路追踪 设备解码
        return tracer(deviceId)
            .traceBlocking(
                "decode",
                (span) -> {
                    span.setAttributeLazy(DeviceTracer.SpanKey.message, () -> message.print(true));

                    Values configs = device.getConfigsNow("encAlg", "secureKey");
                    Ciphers ciphers = configs
                        .getValue("encAlg")
                        .map(Value::asString)
                        .flatMap(Ciphers::of)
                        .orElse(Ciphers.AES);
                    String secureKey = configs.getValue("secureKey").map(Value::asString).orElse(null);
                    byte[] payload = ciphers.decrypt(message.payloadAsBytes(), secureKey);

                    DeviceMessage msg = TopicMessageCodec
                        .decode(objectMapper, TopicMessageCodec.removeProductPath(path), payload);
                    if (msg == null) {
                        msg = FunctionalTopicHandlers
                            .handle(device,
                                    path.split("/"),
                                    payload,
                                    objectMapper,
                                    reply -> response.accept(reply.getPayload()));
                    }
                    return msg;
                });
    }


}
