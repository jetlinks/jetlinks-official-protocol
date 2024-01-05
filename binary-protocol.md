## 头信息

| 字节数 | 类型    |          字段          | 备注               |
| :----: | ------- | :--------------------: | ------------------ |
|   n    | 自定    |        消息长度        | 平台配置项         |
|   1    | INT8    |        消息类型        | 见消息类型定义     |
|   8    | INT64   |       UTC时间戳        |                    |
|   2    | INT16   |        消息序号        |                    |
|   2    | INT16   |       设备ID长度       |                    |
|   n    | STRING  |         设备ID         | 根据设备ID长度得出 |
|   n    | MESSAGE | 消息类型对应的编码规则 |                    |

## 数据类型

所有数据类型均采用`大端`编码

| Byte |  Type   | 编码规则                                                     | 备注                                                         |
| :--: | :-----: | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 0x00 |  NULL   |                                                              |                                                              |
| 0x01 | BOOLEAN | 1字节 0x00为false 其他为true                                 |                                                              |
| 0x02 |  INT8   | 1字节 (byte)                                                 |                                                              |
| 0x03 |  INT16  | 2字节整型 (short)                                            |                                                              |
| 0x04 |  INT32  | 4字节整型 (int)                                              |                                                              |
| 0x05 |  INT64  | 8字节整型 (long)                                             |                                                              |
| 0x06 |  UINT8  | 1字节无符号整型                                              |                                                              |
| 0x07 | UINT16  | 2字节无符号整型                                              |                                                              |
| 0x08 | UINT32  | 4字节无符号整型                                              |                                                              |
| 0x09 |  FLOAT  | 4字节 IEEE 754浮点数                                         |                                                              |
| 0x0a | DOUBLE  | 8字节 IEEE 754浮点数                                         |                                                              |
| 0x0b | STRING  | 前`2字节无符号整型`表示字符串长度,接下来长度的字节为字符串内容,UTF8编码 | 2+N，2个字节（UnsignedShort）表示N的长度                     |
| 0x0c | BINARY  | 前`2字节无符号整型`表示数据长度,接下来长度的字节为数据内容   | 2+N，2个字节（UnsignedShort）表示N的长度                     |
| 0x0d |  ARRAY  | 前`2字节无符号整型`表述数组长度,接下来根据后续报文类型来解析元素 | 2+N，2个字节（UnsignedShort）表示ARRAY的长度N=很多*(1+X)，1个字节（UnsignedShort）表示X的数据类型，X表示长度，见上几行。 |
| 0x0e | OBJECT  | 前`2字节无符号整型`表述对象字段长度,接下来根据后续报文类型来解析key value | 2+N，2个字节（UnsignedShort）表示OBJECT的长度,N是STRING+数据类型的组合了（见上几行）。 |

## 消息类型定义

| Byte | Type               | 说明     |
|:----:|:-------------------|--------|
| 0x00 | keepalive          | 心跳     |
| 0x01 | online             | 首次连接   |
| 0x02 | ack                | 应答     |
| 0x03 | reportProperty     | 上报属性   |
| 0x04 | readProperty       | 读取属性   |
| 0x05 | readPropertyReply  | 读取属性回复 |
| 0x06 | writeProperty      | 修改属性   |
| 0x07 | writePropertyReply | 修改属性回复 |
| 0x08 | function           | 功能调用   |
| 0x09 | functionReply      | 功能调用回复 |

### 0x00 keepalive 心跳

message:[]

### 0x01 online 首次连接

message:[STRING:密钥信息 ]

### 0x02 ack 应答

message:[应答码 ]

应答码: 0x00:ok , 0x01: 未认证, 0x02: 不支持.

### 0x03 reportProperty 上报属性

message:[属性数据:OBJECT类型 ]

### 0x04 readProperty 读取属性

message:[属性列表:ARRAY类型 ]

### 0x05 readPropertyReply 读取属性回复

读取成功:

message:[ `0x01`,属性数据:OBJECT类型 ]

读取失败:
message:[,`0x00`,错误码:动态类型,错误消息:动态类型 ]

`动态读取`表示类型不确定,根据对应的`数据类型`来定义类型.

如: 无错误信息

[ 0x00,`0x00`,`0x00` ]

`INT8(0x02)`类型错误码:`0x04`

[0x00,`0x02,0x04`,0x00 ]

### 0x06 writeProperty 修改属性

message:[属性列表:OBJECT类型 ]

### 0x07 writePropertyReply 修改属性回复

修改成功:

message:[ `0x01`,属性数据:OBJECT类型 ]

修改失败:
message:[,`0x00`,错误码:动态类型,错误消息:动态类型 ]

`动态读取`表示类型不确定,根据对应的`数据类型`来定义类型.

如: 无错误信息

[ 0x00,`0x00`,`0x00` ]

`INT8(0x02)`类型错误码:`0x04`

[0x00,`0x02,0x04`,0x00 ]

### 0x08 function 功能调用

message:[功能ID:STRING类型,功能参数:OBJECT类型 ]

### 0x09 functionReply功能调用回复

调用成功:

message:[ `0x01`,属性数据:OBJECT类型 ]

调用失败:
message:[,`0x00`,错误码:动态类型,错误消息:动态类型 ]

`动态读取`表示类型不确定,根据对应的`数据类型`来定义类型.

如: 无错误信息

[ 0x00,`0x00`,`0x00` ]

`INT8(0x02)`类型错误码:`0x04`

[0x00,`0x02,0x04`,0x00 ]