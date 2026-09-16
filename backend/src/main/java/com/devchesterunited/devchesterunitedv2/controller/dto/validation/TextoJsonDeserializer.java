package com.devchesterunited.devchesterunitedv2.controller.dto.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Evita que Jackson convierta números o booleanos en credenciales de texto. */
public final class TextoJsonDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (String) contexto.handleUnexpectedToken(String.class, parser);
        }
        return parser.getText();
    }
}
