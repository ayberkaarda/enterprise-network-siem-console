package com.example.demo.correlation;

import com.example.demo.common.InvalidRuleConditionException;
import com.example.demo.common.RuleNotFoundException;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CRUD for correlation rules. {@link CorrelationEngine} only ever reads
 * {@code enabled} rows, on its own refresh schedule; this service is the only
 * place a rule is written from outside a migration.
 */
@Service
public class RuleService {

    /**
     * The condition {@code type} discriminators {@link CorrelationEngine}
     * actually knows how to evaluate. Referenced from its constants rather than
     * retyped here so the two can never drift apart.
     */
    private static final Set<String> KNOWN_CONDITION_TYPES = Set.of(
            CorrelationEngine.TYPE_FLAP,
            CorrelationEngine.TYPE_SUBNET_OUTAGE,
            CorrelationEngine.TYPE_LATENCY_ANOMALY,
            CorrelationEngine.TYPE_EVENT_BURST);

    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    public RuleService(RuleRepository ruleRepository, ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.objectMapper = objectMapper;
    }

    public Page<Rule> list(Pageable pageable) {
        return ruleRepository.findAll(pageable);
    }

    public Rule getById(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> new RuleNotFoundException("Rule not found: " + id));
    }

    public Rule create(Rule rule) {
        validateConditionJson(rule.getConditionJson());
        return ruleRepository.save(rule);
    }

    public Rule update(Long id, Rule changes) {
        validateConditionJson(changes.getConditionJson());
        Rule existing = getById(id);
        existing.setName(changes.getName());
        existing.setEnabled(changes.isEnabled());
        existing.setConditionJson(changes.getConditionJson());
        existing.setThresholdCount(changes.getThresholdCount());
        existing.setWindowSeconds(changes.getWindowSeconds());
        existing.setSeverity(changes.getSeverity());
        return ruleRepository.save(existing);
    }

    public void delete(Long id) {
        ruleRepository.delete(getById(id));
    }

    /**
     * Checks that {@code conditionJson} is syntactically valid JSON and that its
     * {@code type} discriminator is one {@link CorrelationEngine} actually
     * evaluates. Without this a typo'd or unsupported type saved cleanly, and
     * {@link CorrelationEngine#compile} silently dropped the rule from the
     * active set on its next reload — the rule looked enabled but never fired
     * and nothing told the admin who wrote it. This does not validate the
     * type-specific fields (see {@link Rule}'s Javadoc); those stay the
     * engine's concern at evaluation time.
     */
    private void validateConditionJson(String conditionJson) {
        JsonNode condition;
        try {
            condition = objectMapper.readTree(conditionJson);
        } catch (Exception ex) {
            throw new InvalidRuleConditionException(
                    "conditionJson is not syntactically valid JSON: " + ex.getMessage());
        }

        JsonNode typeNode = condition.get("type");
        if (typeNode == null || typeNode.asText().isBlank()) {
            throw new InvalidRuleConditionException("conditionJson must include a non-blank \"type\" field.");
        }
        String type = typeNode.asText();
        if (!KNOWN_CONDITION_TYPES.contains(type)) {
            throw new InvalidRuleConditionException(
                    "conditionJson has an unknown \"type\": '" + type + "'. Known types: " + KNOWN_CONDITION_TYPES);
        }
    }
}
