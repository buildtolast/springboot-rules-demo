# GENERATE: src/main/java/com/codrite/ruleaudit/rules/RuleRepository.java

Spring Data JPA repository per the contract. Package com.codrite.ruleaudit.rules.
- public interface RuleRepository extends JpaRepository<Rule, Long>
- java.util.List<Rule> findByActiveTrue();
- Import org.springframework.data.jpa.repository.JpaRepository.
- Output exactly one top-level interface.
