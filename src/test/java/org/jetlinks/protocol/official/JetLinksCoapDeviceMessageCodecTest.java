package org.jetlinks.protocol.official;

import lombok.SneakyThrows;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.hswebframework.utils.RandomUtil;
import org.jetlinks.core.defaults.CompositeProtocolSupports;
import org.jetlinks.core.device.DeviceInfo;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.StandaloneDeviceMessageBroker;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.CoapExchangeMessage;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MessageDecodeContext;
import org.jetlinks.protocol.official.cipher.Ciphers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public class JetLinksCoapDeviceMessageCodecTest {


   JetLinksCoapDeviceMessageCodec codec = new JetLinksCoapDeviceMessageCodec();

    DeviceOperator device;

    private String key = RandomUtil.randomChar(16);

    @Before
    public void init() {
        TestDeviceRegistry registry = new TestDeviceRegistry(new CompositeProtocolSupports(), new StandaloneDeviceMessageBroker());
        device = registry.register(DeviceInfo.builder()
                .id("test")
                .protocol("jetlinks")
                .build())
                .flatMap(operator -> operator.setConfig("secureKey", key).thenReturn(operator))
                .block();
    }

    @Test
    @SneakyThrows
    public void test() {
        AtomicReference<Message> messageRef = new AtomicReference<>();

        CoapServer server = new CoapServer() {
            @Override
            protected Resource createRoot() {
                return new CoapResource("/", true) {

                    @Override
                    public void handlePOST(CoapExchange exchange) {
                        codec.decode(new MessageDecodeContext() {
                            @Nonnull
                            @Override
                            public EncodedMessage getMessage() {
                                return new CoapExchangeMessage(exchange);
                            }

                            @Override
                            public DeviceOperator getDevice() {
                                return device;
                            }
                        })
                                .doOnSuccess(messageRef::set)
                                .doOnError(Throwable::printStackTrace)
                                .subscribe();
                    }

                    @Override
                    public Resource getChild(String name) {
                        return this;
                    }
                };
            }
        };

        Endpoint endpoint = new CoapEndpoint.Builder()
                .setPort(12345).build();
        server.addEndpoint(endpoint);
        server.start();


        CoapClient coapClient = new CoapClient();


        Request request = Request.newPost();
        String payload = "{\"data\":1}";

        request.setURI("coap://localhost:12345/test/test/event/event1");
        request.setPayload(Ciphers.AES.encrypt(payload.getBytes(),key));
//        request.getOptions().setContentFormat(MediaTypeRegistry.APPLICATION_JSON);

        CoapResponse response = coapClient.advanced(request);
        Assert.assertTrue(response.isSuccess());
        Thread.sleep(1000);
        Assert.assertNotNull(messageRef.get());

        System.out.println(messageRef.get());
    }


}