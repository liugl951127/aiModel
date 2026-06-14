package com.aiplatform.trainer.service;

import com.aiplatform.trainer.model.ModelTrainer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring-managed registry of all {@link ModelTrainer} beans. Populated
 * automatically via {@code @Component} scanning. The UI hits
 * {@link #list()} to populate the model picker; the orchestrator uses
 * {@link #get(String)} to look up the chosen trainer.
 */
@Component
@RequiredArgsConstructor
public class TrainerRegistry {

    private final List<ModelTrainer> trainers;

    /** All registered trainers, sorted by display name. */
    public List<ModelTrainer> list() {
        return trainers.stream()
                .sorted(Comparator.comparing(ModelTrainer::displayName))
                .collect(Collectors.toList());
    }

    public ModelTrainer get(String id) {
        return trainers.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    /** UI-shaped projection: id + display name + description. */
    public List<Summary> summaries() {
        return list().stream()
                .map(t -> new Summary(t.id(), t.displayName(), t.description(),
                        t.hyperParams(), t.defaultParams()))
                .collect(Collectors.toList());
    }

    /** DTO for the UI model picker. */
    public record Summary(String id, String displayName, String description,
                          List<ModelTrainer.HyperParam> hyperParams,
                          Map<String, Object> defaultParams) {}
}
