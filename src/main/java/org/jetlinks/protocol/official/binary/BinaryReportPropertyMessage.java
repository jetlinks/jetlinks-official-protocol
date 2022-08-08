package org.jetlinks.protocol.official.binary;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetlinks.core.message.property.ReportPropertyMessage;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
public class BinaryReportPropertyMessage implements BinaryMessage<ReportPropertyMessage> {

    @Override
    public BinaryMessageType getType() {
        return BinaryMessageType.reportProperty;
    }

    private ReportPropertyMessage message;

    @Override
    public void read(ByteBuf buf) {
        message = new ReportPropertyMessage();
        @SuppressWarnings("all")
        Map<String, Object> map = (Map<String, Object>) DataType.OBJECT.read(buf);
        message.setProperties(map);
    }

    @Override
    public void write(ByteBuf buf) {
        DataType.OBJECT.write(buf, message.getProperties());
    }

    @Override
    public void setMessage(ReportPropertyMessage message) {
        this.message = message;
    }

    @Override
    public ReportPropertyMessage getMessage() {
        return message;
    }

}
