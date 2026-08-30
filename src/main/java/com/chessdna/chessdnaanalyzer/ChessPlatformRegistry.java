package com.chessdna.chessdnaanalyzer;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ChessPlatformRegistry {

    private final Map<ChessPlatform, ChessPlatformService> providers = new EnumMap<>(ChessPlatform.class);

    public ChessPlatformRegistry(List<ChessPlatformService> platformServices) {
        for (ChessPlatformService service : platformServices) {
            ChessPlatformService previous = providers.put(service.platform(), service);
            if (previous != null) {
                throw new IllegalStateException("Multiple providers registered for " + service.platform());
            }
        }
    }

    public ChessPlatformService providerFor(ChessPlatform platform) {
        ChessPlatformService provider = providers.get(platform);
        if (provider == null) {
            throw new IllegalArgumentException("No provider configured for " + platform.displayName());
        }
        return provider;
    }
}
