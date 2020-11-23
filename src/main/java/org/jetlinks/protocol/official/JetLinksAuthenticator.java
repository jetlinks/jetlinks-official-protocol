package org.jetlinks.protocol.official;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetlinks.core.Value;
import org.jetlinks.core.defaults.Authenticator;
import org.jetlinks.core.device.*;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class JetLinksAuthenticator implements Authenticator {

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        MqttAuthenticationRequest mqtt = ((MqttAuthenticationRequest) request);

        return registry
                .getDevice(mqtt.getClientId())
                .flatMap(device -> authenticate(request, device));
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator deviceOperation) {
        if (request instanceof MqttAuthenticationRequest) {
            MqttAuthenticationRequest mqtt = ((MqttAuthenticationRequest) request);
            // secureId|timestamp
            String username = mqtt.getUsername();
            // md5(secureId|timestamp|secureKey)
            String password = mqtt.getPassword();
            String requestSecureId;
            try {
                String[] arr = username.split("[|]");
                if (arr.length <= 1) {
                    return Mono.just(AuthenticationResponse.error(401, "用户名格式错误"));
                }
                requestSecureId = arr[0];
                long time = Long.parseLong(arr[1]);
                //和设备时间差大于5分钟则认为无效
                if (Math.abs(System.currentTimeMillis() - time) > TimeUnit.MINUTES.toMillis(5)) {
                    return Mono.just(AuthenticationResponse.error(401, "设备时间不同步"));
                }
                return deviceOperation.getConfigs("secureId", "secureKey")
                        .map(conf -> {
                            String secureId =  conf.getValue("secureId").map(Value::asString).orElse(null);

                            String secureKey = conf.getValue("secureKey").map(Value::asString).orElse(null);
                            //签名
                            String digest = DigestUtils.md5Hex(username + "|" + secureKey);
                            if (requestSecureId.equals(secureId) && digest.equals(password)) {
                                return AuthenticationResponse.success(deviceOperation.getDeviceId());
                            } else {
                                return AuthenticationResponse.error(401, "密钥错误");
                            }
                        });
            } catch (NumberFormatException e) {
                return Mono.just(AuthenticationResponse.error(401, "用户名格式错误"));
            }
        }
        return Mono.just(AuthenticationResponse.error(400, "不支持的授权类型:" + request));
    }
}
