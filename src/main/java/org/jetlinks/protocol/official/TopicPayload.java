package org.jetlinks.protocol.official;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor(staticName = "of") // FIXME 使用对象池,节省一丢丢内存?
public class TopicPayload {

    private String topic;

    private byte[] payload;
}
