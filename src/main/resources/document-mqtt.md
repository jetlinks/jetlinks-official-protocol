### 认证说明

CONNECT报文:

使用`明文认证`时:

```text
clientId: 设备ID
username: secureId
password: secureKey
```

使用`MD5签名`认证时:

```text
clientId: 设备ID
username: secureId+"|"+timestamp
password: md5(secureId+"|"+timestamp+"|"+secureKey)
 ```

说明: secureId以及secureKey在创建设备产品或设备实例时进行配置.
timestamp为当前时间戳(毫秒),与服务器时间不能相差5分钟.
md5为32位,不区分大小写.