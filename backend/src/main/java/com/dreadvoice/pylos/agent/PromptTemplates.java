package com.dreadvoice.pylos.agent;

import com.dreadvoice.pylos.context.SiteType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Component
public class PromptTemplates {

    private static final String BASE_TEMPLATE = "prompts/system-base.txt";
    private static final String TEMPLATE_PATTERN = "prompts/%s.txt";

    private final String base;
    private final Map<SiteType, String> perSiteType = new EnumMap<>(SiteType.class);

    public PromptTemplates() {
        this.base = read(BASE_TEMPLATE);
        for (SiteType siteType : SiteType.values()) {
            perSiteType.put(siteType, read(TEMPLATE_PATTERN.formatted(siteType.name())));
        }
    }

    public String systemPrompt(SiteType siteType) {
        if (siteType == null) {
            return base;
        }
        return base + "\n\n" + perSiteType.get(siteType);
    }

    private static String read(String location) {
        ClassPathResource resource = new ClassPathResource(location);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader).strip();
        } catch (IOException exception) {
            throw new UncheckedIOException("Missing prompt template " + location, exception);
        }
    }
}
