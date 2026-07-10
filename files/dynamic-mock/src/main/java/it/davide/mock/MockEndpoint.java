package it.davide.mock;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public record MockEndpoint(
        // Accetta sia "GET" che ["GET", "POST"]
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> method,
        String uri,
        Object responseBody
) {}