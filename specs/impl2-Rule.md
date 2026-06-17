# GENERATE: src/main/java/com/codrite/ruleaudit/rules/Rule.java

JPA entity per the contract. Package com.codrite.ruleaudit.rules.
- jakarta.persistence annotations (@Entity, @Table(name="rule"), @Id,
  @GeneratedValue(strategy=GenerationType.IDENTITY)).
- Fields: Long id; String description; @Column(length=2000) String spelExpression;
  boolean active; java.time.Instant updatedAt.
- A no-arg constructor (required by JPA) and a convenience constructor
  (description, spelExpression, active) that sets updatedAt = Instant.now().
- Standard getters and setters for all fields.
- Output exactly one top-level class. No other class.
