package me.zhengjie.agent.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从类路径 YAML 加载诊断建议模板。
 */
@Service
public class FileSystemDiagnosisSuggestionTemplateService implements DiagnosisSuggestionTemplateService {

    static final String DEFAULT_RESOURCE_PATH = "rules/meal-plan/suggestion-template.yaml";

    private final Resource resource;
    private final ObjectMapper objectMapper;

    public FileSystemDiagnosisSuggestionTemplateService() {
        this(new ClassPathResource(DEFAULT_RESOURCE_PATH), new ObjectMapper());
    }

    FileSystemDiagnosisSuggestionTemplateService(Resource resource, ObjectMapper objectMapper) {
        this.resource = resource;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DiagnosisSuggestionTemplate> listTemplates() {
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> root = new Yaml().load(inputStream);
            Object rawTemplates = root == null ? null : root.get("templates");
            if (!(rawTemplates instanceof List<?> items)) {
                return List.of();
            }
            List<DiagnosisSuggestionTemplate> templates = new ArrayList<>();
            for (Object item : items) {
                templates.add(objectMapper.convertValue(item, DiagnosisSuggestionTemplate.class));
            }
            validateTemplates(templates);
            return templates;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load diagnosis suggestion templates", ex);
        }
    }

    @Override
    public Optional<DiagnosisSuggestionTemplate> findByCode(String code) {
        return listTemplates().stream()
            .filter(template -> template != null && code != null && code.equals(template.getCode()))
            .findFirst();
    }

    @Override
    public DiagnosisResponse applyTemplates(DiagnosisResponse response) {
        if (response == null || response.getReasons() == null) {
            return response;
        }
        Map<String, DiagnosisSuggestionTemplate> index = indexByCode(listTemplates());
        for (DiagnosisReasonDto reason : response.getReasons()) {
            if (reason == null) {
                continue;
            }
            DiagnosisSuggestionTemplate template = index.get(reason.getCode());
            if (template == null) {
                if (isBlank(reason.getConfidence())) {
                    reason.setConfidence("LOW");
                }
                continue;
            }
            if (isBlank(reason.getTitle())) {
                reason.setTitle(template.getTitle());
            }
            if (isBlank(reason.getSuggestion())) {
                reason.setSuggestion(template.getDefaultSuggestion());
            }
            if (reason.getNextActions() == null || reason.getNextActions().isEmpty()) {
                reason.setNextActions(new ArrayList<>(template.getNextActions()));
            }
        }
        if ((response.getNextActions() == null || response.getNextActions().isEmpty()) && !response.getReasons().isEmpty()) {
            List<String> nextActions = new ArrayList<>();
            for (DiagnosisReasonDto reason : response.getReasons()) {
                if (reason != null && reason.getNextActions() != null) {
                    nextActions.addAll(reason.getNextActions());
                }
            }
            response.setNextActions(nextActions);
        }
        return response;
    }

    void validateTemplates(List<DiagnosisSuggestionTemplate> templates) {
        Map<String, DiagnosisSuggestionTemplate> index = new LinkedHashMap<>();
        for (DiagnosisSuggestionTemplate template : templates) {
            if (template == null || isBlank(template.getCode())) {
                throw new IllegalStateException("suggestion template code must not be blank");
            }
            if (index.containsKey(template.getCode())) {
                throw new IllegalStateException("duplicate suggestion template code: " + template.getCode());
            }
            if (isBlank(template.getDefaultSuggestion())) {
                throw new IllegalStateException("suggestion template defaultSuggestion must not be blank: " + template.getCode());
            }
            if (template.getNextActions() == null || template.getNextActions().isEmpty()) {
                throw new IllegalStateException("suggestion template nextActions must not be empty: " + template.getCode());
            }
            index.put(template.getCode(), template);
        }
    }

    private Map<String, DiagnosisSuggestionTemplate> indexByCode(List<DiagnosisSuggestionTemplate> templates) {
        Map<String, DiagnosisSuggestionTemplate> index = new LinkedHashMap<>();
        for (DiagnosisSuggestionTemplate template : templates) {
            index.put(template.getCode(), template);
        }
        return index;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
