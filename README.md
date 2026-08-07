# centralized_alert
This apps is used to Centralized Alert for another Service

## Local development (Maven CLI + hot reload)

Spring Boot DevTools restarts the application quickly when compiled classes change. Run the app in one terminal and recompile after edits in another.

**Terminal 1 — run:**

```bash
cd alert
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 2 — after code or resource changes:**

```bash
cd alert
mvn -DskipTests compile
```

Watch the run terminal for DevTools messages such as `Restarting due to ...`.

### Limits

| Change | What to do |
|--------|------------|
| Java/resources in `alert` | `mvn -DskipTests compile` (DevTools restart) |
| `sdk-util` dependency | `mvn install` in `sdk-util`, then `mvn compile` in `alert` |
| New Flyway migration | Full restart; ensure DB migration state is consistent |
| Large structural changes (new beans, signatures) | Full context restart (same as compile trigger) |

DevTools is `optional` and excluded from production packaging when the app is built as a runnable JAR.
