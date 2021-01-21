package org.jetlinks.protocol.official;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

public class ObjectMappers {

    public static final ObjectMapper JSON_MAPPER;
    public static final ObjectMapper CBOR_MAPPER;

    static {
        JSON_MAPPER = Jackson2ObjectMapperBuilder
                .json()
                .build()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CBOR_MAPPER = Jackson2ObjectMapperBuilder
                .cbor()
                .build()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

}
