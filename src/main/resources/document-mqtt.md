# MQTT认证说明
CONNECT报文:
```text
clientId: 设备ID
username: secureId+"|"+timestamp
password: md5(secureId+"|"+timestamp+"|"+secureKey)
 ```

说明: secureId以及secureKey在创建设备产品或设备实例时进行配置. 
timestamp为当前系统时间戳(毫秒),与系统时间不能相差5分钟.
md5为32位,不区分大小写.