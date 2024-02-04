## JetLinks 官方设备接入协议

JetLinks官方实现的设备接入协议,可用于参考实现自定义协议开发.

注意: 本协议仅用于参考自定义协议开发,在实际使用中请根据不同的场景进行调整.如认证方式,加密等.

### MQTT

[查看TOPIC说明](https://hanta.yuque.com/px7kg1/yfac2l/sr0metyagzmhbtm7)

用户名密码可以使用[生成工具进行生成](http://doc.jetlinks.cn/basics-guide/mqtt-auth-generator.html)

### HTTP

HTTP接入时需要使用`Bearer`
认证,URL和[MQTT的接入Topic]((http://doc.jetlinks.cn/basics-guide/jetlinks-protocol-support.html))一致.

```http request
POST /{productId}/{deviceId}/properties/report
Authorization: Bearer {产品或者设备中配置的Token}
Content-Type: application/json

{
 "properties":{
   "temp":38.5
 }
}
```

### TCP

报文格式说明:

第0-4字节对应的32位整型值为接下来报文的长度,

后续为报文数据，
具体报文格式见: [二进制格式说明](binary-protocol.md)

创建连接后第一个数据包需要发送[认证包](binary-protocol.md#0x01-online-首次连接),
密钥需要在`产品-设备接入`或者`设备详情`中进行配置

### UDP

报文格式说明:

第`0`字节表示认证类型,目前固定为0x00.

第`1-n`字节为`密钥信息`,编码使用`STRING`见: [数据类型定义](binary-protocol.md#数据类型)

密钥需要在`产品-设备接入`或者`设备详情`中进行配置

后续为报文数据,具体报文格式见: [二进制格式说明](binary-protocol.md)

UDP无需发送认证包,但是需要每个报文中都包含密钥信息.

除了ACK以外,其他平台下发的指令也都会包含认证密钥信息,用于设备侧校验请求.


### 测试

可以使用[模拟器](http://github.com/jetlinks/device-simulator)进行模拟测试
