# DS-Hibernate ReadOnly-Transaction Add-on

A DeltaSpike transaction strategy that automatically switches the Hibernate
session to `FlushMode.MANUAL` when `@Transactional(readOnly = true)` is used,
including support for nested transaction constellations.

## Overview

When a method is annotated with `@Transactional(readOnly = true)`, this
strategy replaces DeltaSpike's default `EnvironmentAwareTransactionStrategy`
and sets the Hibernate `Session` to `FlushMode.MANUAL` for the duration of
the transaction. The original flush mode is restored after the method completes,
even in nested scenarios.

## Usage

The strategy activates automatically as a CDI `@Alternative` with
`@Priority(LIBRARY_BEFORE)`. No explicit configuration is needed beyond
adding this addon to your classpath.

```java
@Transactional(readOnly = true)
public List<User> findAllUsers() {
    // Hibernate session uses FlushMode.MANUAL here
    return em.createQuery("SELECT u FROM User u", User.class).getResultList();
}
```

## Requirements

- Java 25+
- Maven 3.6.3+
- Jakarta CDI 4.1
- Jakarta Persistence 3.2
- DeltaSpike 2.0.1 (core + JPA module)
- Hibernate 6.6.x

## Building

```bash
mvn clean verify
```

## Testing

Tests use the [dynamic-cdi-test-bean-addon](https://github.com/os890/dynamic-cdi-test-bean-addon)
with `@EnableTestBeans` for CDI SE integration testing, plus Mockito-based
unit tests for flush mode restoration behaviour.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
