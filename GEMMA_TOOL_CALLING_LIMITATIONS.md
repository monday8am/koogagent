# Gemma Tool Calling: Limitations & Failure Scenarios

## Overview

The `GemmaLLMClient` uses a simplified JSON protocol for tool calling because Gemma 3n (1B parameters) lacks the capacity for complex structured output like larger models (Claude, GPT-4, etc.).

## Protocol Summary

```
Tool call:  {"tool":"toolName"}
No tool:    {"tool":"none"} followed by response text
```

## Known Limitations

### 1. **Single Tool Per Turn** ⚠️

**What it means**: Model can only request ONE tool at a time.

**Anthropic can do this**:
```json
{
  "content": [
    {"type": "tool_use", "name": "getWeather", "input": {"city": "NYC"}},
    {"type": "tool_use", "name": "getLocation", "input": {}}
  ]
}
```

**Gemma cannot**:
```json
// ❌ Not supported
{"tool":"getWeather"},{"tool":"getLocation"}

// ✅ What Gemma does
{"tool":"getWeather"}
```

**When this fails**:
- User asks: "What's the weather and my location?"
- Gemma must choose ONE tool, then wait for result before calling the second
- Requires multiple agent turns (orchestrated by Koog)

---

### 2. **No Tool Parameters** ⚠️

**What it means**: Tools receive empty arguments `"{}"`

**Anthropic can do this**:
```json
{
  "type": "tool_use",
  "name": "getWeather",
  "input": {"city": "New York", "units": "celsius"}
}
```

**Gemma cannot**:
```json
// ❌ Gemma can't generate this reliably
{"tool":"getWeather", "args": {"city": "New York"}}

// ✅ What Gemma does
{"tool":"getWeather"}
```

**Workarounds**:
1. **Tools must not require parameters** - Design parameter-free tools
2. **Context from conversation** - Extract parameters from user message
3. **Hardcoded defaults** - Tool uses sensible defaults (e.g., current location)

**Example**:
```kotlin
// ❌ Won't work with Gemma
class GetWeatherTool : Tool {
    @Parameter(required = true)
    val city: String
}

// ✅ Works with Gemma
class GetWeatherTool(
    private val locationProvider: LocationProvider
) : Tool {
    // No parameters - uses current location
    override suspend fun execute(): String {
        val location = locationProvider.getCurrentLocation()
        return fetchWeather(location)
    }
}
```

---

### 3. **Protocol Confusion** 🔴

**When Gemma gets confused about the format**:

```
// ✅ Correct
{"tool":"getWeather"}

// ❌ Malformed JSON
{tool: "getWeather"}           // Missing quotes
{"tool":"getWeather"           // Missing closing brace
{"Tool":"getWeather"}          // Wrong case
{"tool" : "getWeather" }       // Extra spaces (actually OK - regex handles this)
{ "tool" : "getWeather"}       // Also OK - regex handles this
```

**Current mitigation**:
- Regex is flexible with whitespace: `\{\s*"tool"\s*:\s*"([^"]+)"\s*\}`
- Falls back to treating response as regular text if no match
- Provides examples in prompt to reinforce format

**When this fails**:
- Model is undertrained on JSON format
- Prompt is too long and model loses focus
- Temperature is too high causing creative deviations

---

### 4. **Mixed Output** 🟡

**What it means**: Model mixes tool syntax with conversational text.

```
// ❌ Ambiguous
Let me check the weather for you. {"tool":"getWeather"}

// ❌ Tool after text
The weather is... wait, let me check. {"tool":"getWeather"}

// ✅ Expected format
{"tool":"getWeather"}
```

**Current handling**:
- Regex finds FIRST `{"tool":"X"}` match
- Ignores any text before/after
- Returns tool call, discarding surrounding text

**Edge case**:
```kotlin
// Response: "Let me help! {"tool":"getWeather"} I'll check for you."
// Parsed as: Message.Tool.Call(tool = "getWeather")
// Lost context: "Let me help!" and "I'll check for you."
```

**When this becomes a problem**:
- Model wants to explain why it's calling a tool
- User needs intermediate feedback
- Multi-step reasoning is important

---

### 5. **Hallucinated Tools** 🟡

**What it means**: Model invents tool names that don't exist.

```
Available tools:
- getWeather: Gets current weather

User: What's my location?
Gemma: {"tool":"getLocation"}  ❌ Tool doesn't exist!
```

**Current handling**:
```kotlin
if (!toolExists) {
    logger.w { "Model requested non-existent tool: '$toolName'" }
    return Message.Assistant(
        "I tried to use a tool called '$toolName' but it doesn't exist.
         Let me try to help you another way."
    )
}
```

**Why this happens**:
- Model generalizes from examples
- Similar task → invents similar tool name
- Limited context about available tools

**Mitigation strategies**:
1. ✅ Validate against available tools (current implementation)
2. Add negative examples in prompt (e.g., "Don't invent tool names")
3. Fine-tune model on your specific tool set
4. Increase prompt clarity about available tools

---

### 6. **Parallel Tool Requirements** 🔴

**Scenario**: Task inherently requires multiple tools simultaneously.

```
User: "Compare weather in NYC and London"

Ideal (Anthropic):
[
  {"type": "tool_use", "name": "getWeather", "input": {"city": "NYC"}},
  {"type": "tool_use", "name": "getWeather", "input": {"city": "London"}}
]

Gemma's approach:
Turn 1: {"tool":"getWeather"}  // Gets NYC weather (inferred from context)
Turn 2: {"tool":"getWeather"}  // Gets London weather
Turn 3: Compare results
```

**Problems**:
- **Slower**: Requires 3 agent turns instead of 1
- **Context loss**: Hard to remember first result while fetching second
- **Parameter ambiguity**: Can't specify different cities without parameters

**Current workaround**: Not handled - would require:
- Multi-step conversation orchestration
- Memory/context management
- Or redesign tools to accept batch inputs

---

### 7. **Complex Tool Workflows** 🔴

**Scenario**: Tools that depend on output from previous tools.

```
Example: "Book a flight to the cheapest sunny destination"

Required workflow:
1. searchDestinations(criteria: "sunny") → list of cities
2. getWeatherForecast(cities: list) → weather data
3. getFlightPrices(cities: list) → price data
4. rankDestinations(weather, prices) → best option
5. bookFlight(destination: best)
```

**Gemma's limitations**:
- ❌ Can't call multiple tools in parallel (steps 2-3)
- ❌ Can't pass structured output between tools (no parameters)
- ❌ Can't maintain complex state across turns

**This approach FAILS for**:
- Multi-tool pipelines
- Data aggregation tasks
- Workflows requiring structured intermediate results

---

## When to Use This Approach ✅

### **Good Use Cases**:

1. **Simple, single-tool queries**
   - "What's the weather?"
   - "Get my location"
   - "Search for X"

2. **Parameter-free tools**
   - Tools that use context/defaults
   - Tools with no configuration options

3. **Sequential, independent tasks**
   - "Check weather" → (result) → "Tell me about it"
   - Each tool call is self-contained

4. **Low-stakes applications**
   - Prototyping
   - Personal projects
   - Non-critical features

---

## When NOT to Use This Approach ❌

### **Bad Use Cases**:

1. **Parallel tool execution required**
   - "Compare X and Y"
   - "Aggregate data from multiple sources"

2. **Complex tool parameters needed**
   - "Search for flights from NYC to LAX on March 15th"
   - "Calculate mortgage with 20% down at 6.5% APR"

3. **Multi-step dependent workflows**
   - "Find the best restaurant near the cheapest hotel"
   - Tool chains where output of A feeds into B

4. **Production-critical applications**
   - Financial transactions
   - Medical advice
   - Legal operations

---

## Comparison: Gemma vs. Anthropic

| Feature | Anthropic Claude | Gemma 3n (1B) |
|---------|-----------------|---------------|
| **Parallel Tools** | ✅ Yes | ❌ No |
| **Tool Parameters** | ✅ Full JSON schemas | ❌ None (empty `{}`) |
| **Parameter Types** | String, Int, Object, Array, Enum | N/A |
| **Multiple Tools/Turn** | ✅ Yes | ❌ No (one at a time) |
| **Tool Validation** | ✅ API-level | ⚠️ Client-side only |
| **Structured Output** | ✅ JSON, XML, etc. | ⚠️ Text-based protocol |
| **Error Recovery** | ✅ Retry with hints | ⚠️ Fallback to text |
| **Context Length** | 200K+ tokens | ~8K tokens (MediaPipe limit) |

---

## Future Improvements

### **Potential Enhancements**:

1. **Support multi-tool syntax**:
   ```json
   {"tools":["getWeather","getLocation"]}
   ```

2. **Add basic parameter support**:
   ```json
   {"tool":"search", "query":"weather"}
   ```

3. **Fallback to larger Gemma models** (2B, 7B):
   - Better structured output capability
   - More reliable protocol following

4. **Fine-tuning**:
   - Train Gemma on tool-calling examples
   - Improve format consistency

5. **Prompt engineering**:
   - Add more examples
   - Use few-shot learning
   - Chain-of-thought prompting

---

## Safety Mechanisms

### Infinite Loop Detection ✅

**Problem**: Small models may get stuck calling the same tool repeatedly instead of using the result.

**Solution**: The `GemmaLLMClient` now detects when:
1. The model receives a tool result
2. The model tries to call the SAME tool again

**Behavior**:
```kotlin
// If detected:
logger.w { "INFINITE LOOP DETECTED: Model trying to call '$toolName' again" }

// Returns instead:
Message.Assistant(
    "I have the information from $toolName: ${result}.
     Let me provide you with the answer based on that."
)
```

This prevents context overflow and provides a graceful fallback.

---

## Conclusion

The simplified protocol is a **pragmatic solution** for Gemma 3n's limitations. It works well for:
- ✅ Simple, single-tool use cases
- ✅ Rapid prototyping
- ✅ On-device inference scenarios
- ✅ Protected against infinite loops with safety mechanisms

But it **should not be used** for:
- ❌ Complex tool workflows
- ❌ Parallel tool execution
- ❌ Parameter-heavy tools
- ❌ Production-critical features

For production use with complex tool calling, consider:
- Using larger models (Gemma 7B+, or cloud models)
- Redesigning tools to be parameter-free
- Implementing custom orchestration logic
- Enabling safety mechanisms (like infinite loop detection)
- Or accepting the limitations and working within them
