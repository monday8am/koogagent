---
description: Check and analyze project dependencies
---
// turbo-all
1. Check all dependencies
   ./gradlew dependencies

2. Check specific module dependencies (if needed)
   ./gradlew :app:dependencies
   ./gradlew :agent:dependencies
   ./gradlew :presentation:dependencies
   ./gradlew :data:dependencies
