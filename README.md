## JetLinks 官方设备接入协议

类名: `org.jetlinks.protocol.official.JetLinksProtocolSupportProvider`


## MQTT(S)接入
目前支持MQTT3.1.1和3.1版本协议.

### 认证

CONNECT报文:
```
clientId: 设备实例ID
username: secureId+"|"+timestamp
password: md5(secureId+"|"+timestamp+"|"+secureKey)
```

说明: `secureId`以及`secureKey`在创建设备产品和设备实例时进行配置.
`timestamp`为当前系统时间戳(毫秒),与系统时间不能相差5分钟.


### Topic

 - 读取设备属性: 
    
    topic: `/{productId}/{deviceId}/properties/read`
    
    方向: `下行`
    
    消息格式: 
    
        {
        "messageId":"消息ID",
        "deviceId":"设备ID",
        "properties":["sn","model"] //要读取到属性列表
        }
             
    回复Topic: `/{productId}/{deviceId}/properties/read/reply`
    
    回复消息格式:
        
        //成功
        {
          "messageId":"与下行消息中的messageId相同",
          "properties":{"sn":"test","model":"test"}, //key与设备模型中定义的属性id一致
          "deviceId":"设备ID",
          "success":true,
        }
        //失败. 下同
        {
          "messageId":"与下行消息中的messageId相同",
          "success":false,
          "code":"error_code",
          "message":"失败原因"
        }
 - 修改设备属性:
    
     topic: `/{productId}/{deviceId}/properties/write`
     
     消息格式: 
        
        {
         "messageId":"消息ID",
         "deviceId":"设备ID",
         "properties":{"color":"red"} //要设置的属性
        }
     回复Topic: `/{productId}/{deviceId}/properties/wirte/reply`
     
     回复消息格式:
     
        {
          "messageId":"与下行消息中的messageId相同",
          "properties":{"color":"red"}, //设置成功后的属性,可不返回
          "success":true,
        }
        
 - 调用设备功能
 
      topic: `/{productId}/{deviceId}/function/invoke`
      
      消息格式: 
         
         {
          "messageId":"消息ID",
          "deviceId":"设备ID",
          "function":"playVoice",//功能ID
          "inputs":[{"name":"text","value":"播放声音"}] //参数
         }
         
      回复Topic: `/{productId}/{deviceId}/function/invoke/reply`
      
      回复消息格式:
         
         {
           "messageId":"与下行消息中的messageId相同",
           "output":"success", //返回执行结果
           "success":true,
         }
         
 - 设备事件上报
     
      topic: /{productId}/{deviceId}/event/{eventId}
      
      消息格式: 
        
        {
        "messageId":"随机消息ID",
        "data":100 //上报数据
        }
       
      拓展:
      
      定时上报属性:
                
        {
        "messageId":"随机消息ID",
        "data":{"color":"red"},//属性列表
        "headers":{"report-properties":true} //标记为上报属性事件
        } 
      
### 动态注册

暂不支持

## CoAP接入
使用CoAP协议接入仅需要对数据进行加密即可.加密算法: AES/ECB/PKCS5Padding.

使用自定义Option: `2100:设备ID` 来标识设备.

将请求体进行加密,密钥为在创建设备产品和设备实例时进行配置的(`secureKey`).

请求地址(`URI`)与MQTT `Topic`相同.消息体(`payload`)与MQTT相同.


## DTLS接入
使用CoAP DTLS 协议接入时需要先进行认证:

发送认证请求:
```text
POST /auth
Accept: application/json
Content-Format: application/json
2100: 设备ID
2110: 签名 md5(payload+secureKey)
payload: {"timestamp":"时间戳"}
```

响应结果:
```text
2.05 (Content)
payload: {"token":"令牌"}
```

之后的请求中需要将返回的令牌携带到自定义Option:2111

例如:
```text
POST /test/device1/event/fire_alarm
2100: 设备ID
2111: 令牌
...其他Option
payload: json数据
```
