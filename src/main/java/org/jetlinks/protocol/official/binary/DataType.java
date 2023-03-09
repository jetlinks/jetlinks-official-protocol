package org.jetlinks.protocol.official.binary;

import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetlinks.protocol.official.ObjectMappers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public enum DataType {
    //0x00
    NULL {
        @Override
        public Object read(ByteBuf buf) {
            return null;
        }

        @Override
        public void write(ByteBuf buf, Object value) {

        }
    },
    //0x01
    BOOLEAN {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readBoolean();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeBoolean((Boolean) value);
        }
    },
    //0x02
    INT8 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readByte();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeByte((Byte) value);
        }
    },
    //0x03
    INT16 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readShort();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeShort((Short) value);
        }
    },
    //0x04
    INT32 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readInt();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeInt((Integer) value);
        }
    },
    //0x05
    INT64 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readLong();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeLong((Long) value);
        }
    },
    //0x06
    UINT8 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readUnsignedByte();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeByte((Byte) value);
        }
    },
    //0x07
    UINT16 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readUnsignedShort();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeShort((Short) value);
        }
    },
    //0x08
    UINT32 {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readUnsignedInt();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeInt((Integer) value);
        }
    },
    //0x09
    FLOAT {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readFloat();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeFloat((Float) value);
        }
    },
    //0x0A
    DOUBLE {
        @Override
        public Object read(ByteBuf buf) {
            return buf.readDouble();
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            buf.writeDouble((Double) value);
        }
    },
    //0x0B
    STRING {
        @Override
        public Object read(ByteBuf buf) {
            int len = buf.readUnsignedShort();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public void write(ByteBuf buf, Object value) {

            byte[] bytes = ((String) value).getBytes();
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    },
    //0x0C
    BINARY {
        @Override
        public Object read(ByteBuf buf) {
            int len = buf.readUnsignedShort();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return bytes;
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            byte[] bytes = (byte[]) value;
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    },
    //0x0D
    ARRAY {
        @Override
        public Object read(ByteBuf buf) {
            int len = buf.readUnsignedShort();
            List<Object> array = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                array.add(readFrom(buf));
            }
            return array;
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            Collection<Object> array = (Collection<Object>) value;
            buf.writeShort(array.size());
            for (Object o : array) {
                writeTo(o, buf);
            }
        }
    },
    //0x0E
    OBJECT {
        @Override
        public Object read(ByteBuf buf) {
            int len = buf.readUnsignedShort();
            Map<String, Object> data = Maps.newLinkedHashMapWithExpectedSize(len);
            for (int i = 0; i < len; i++) {
                data.put((String) STRING.read(buf), readFrom(buf));
            }
            return data;
        }

        @Override
        public void write(ByteBuf buf, Object value) {
            Map<String, Object> data = value instanceof Map ? ((Map) value) : ObjectMappers.JSON_MAPPER.convertValue(value, Map.class);
            buf.writeShort(data.size());

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                STRING.write(buf, entry.getKey());
                writeTo(entry.getValue(), buf);
            }
        }
    };

    private final static DataType[] VALUES = values();

    public abstract Object read(ByteBuf buf);

    public abstract void write(ByteBuf buf, Object value);

    public static Object readFrom(ByteBuf buf) {
        return VALUES[buf.readUnsignedByte()].read(buf);
    }

    public static void writeTo(Object data, ByteBuf buf) {
        DataType type = loopUpType(data);
        buf.writeByte(type.ordinal());
        type.write(buf, data);
    }

    private static DataType loopUpType(Object data) {
        if (data == null) {
            return NULL;
        } else if (data instanceof Boolean) {
            return BOOLEAN;
        } else if (data instanceof Byte) {
            return INT8;
        } else if (data instanceof Short) {
            return INT16;
        } else if (data instanceof Integer) {
            return INT32;
        } else if (data instanceof Long) {
            return INT64;
        } else if (data instanceof Float) {
            return FLOAT;
        } else if (data instanceof Double) {
            return DOUBLE;
        } else if (data instanceof String) {
            return STRING;
        } else if (data instanceof byte[]) {
            return BINARY;
        } else if (data instanceof Collection) {
            return ARRAY;
        } else if (data instanceof Map) {
            return OBJECT;
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + data.getClass());
        }
    }

    public static void main(String[] args) {
        System.out.println("| Byte | Type |");
        System.out.println("|  ----  | ----  |");
        for (DataType value : DataType.values()) {
            System.out.print("|");
            System.out.print("0x0"+Integer.toString(value.ordinal(),16));
            System.out.print("|");
            System.out.print(value.name());
            System.out.print("|");
            System.out.println();
        }
        System.out.println();
    }
}
