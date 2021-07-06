package org.jetlinks.protocol.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

public class ObjectMappers {

    public static final ObjectMapper JSON_MAPPER;
    public static final ObjectMapper CBOR_MAPPER;

    static {
        JSON_MAPPER = Jackson2ObjectMapperBuilder
                .json()
                .build()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        ;
        CBOR_MAPPER = Jackson2ObjectMapperBuilder
                .cbor()
                .build()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

}
