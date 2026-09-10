package dev.gnius.llmlab.dto;

import java.util.List;

/**
 * Payload for {@code POST /api/results/delete}: the set of run-result ids to delete.
 * An empty or missing list is rejected with 400.
 */
public record ResultDeleteRequest(List<Long> ids) {
}
