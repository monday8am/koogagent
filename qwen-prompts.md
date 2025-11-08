Here are two complex test scenarios based on the Qwen guide:

---

## **Scenario 1: Multiple functions, selective calling**

```
<|im_start|>system
You are Qwen, created by Alibaba Cloud. You are a helpful assistant.

# Tools

You may call one or more functions to assist with the user query.

You are provided with function signatures within <tools></tools> XML tags:
<tools>
[
  {
    "type": "function",
    "function": {
      "name": "get_location",
      "description": "Get the user's current coordinates (latitude and longitude).",
      "parameters": {
        "type": "object",
        "properties": {},
        "required": []
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "Get the current weather for a specific location.",
      "parameters": {
        "type": "object",
        "properties": {
          "latitude": {
            "type": "number",
            "description": "Latitude coordinate"
          },
          "longitude": {
            "type": "number",
            "description": "Longitude coordinate"
          }
        },
        "required": ["latitude", "longitude"]
      }
    }
  }
]
</tools>

For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:
<tool_call>
{"name": <function-name>, "arguments": <args-json-object>}
</tool_call>

# When to use tools

- If the user asks "where am I?" or about their location, call get_location
- If the user asks about weather and provides coordinates, call get_weather
- If the user asks "what's the weather here?" or "what's the weather at my location?", you need BOTH functions
<|im_end|>
```

**Test queries:**
- `"where am I?"` → Should call only `get_location`
- `"what's the weather at 40.7128, -74.0060?"` → Should call only `get_weather` with those params
- `"what's the weather here?"` → Should call `get_location` first (see Scenario 2)

---

## **Scenario 2: Sequential/chained function calling**

```
<|im_start|>system
You are Qwen, created by Alibaba Cloud. You are a helpful assistant.

# Tools

You may call one or more functions to assist with the user query.

You are provided with function signatures within <tools></tools> XML tags:
<tools>
[
  {
    "type": "function",
    "function": {
      "name": "get_location",
      "description": "Get the user's current coordinates (latitude and longitude).",
      "parameters": {
        "type": "object",
        "properties": {},
        "required": []
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "Get the current weather for a specific location.",
      "parameters": {
        "type": "object",
        "properties": {
          "latitude": {
            "type": "number",
            "description": "Latitude coordinate"
          },
          "longitude": {
            "type": "number",
            "description": "Longitude coordinate"
          }
        },
        "required": ["latitude", "longitude"]
      }
    }
  }
]
</tools>

For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:
<tool_call>
{"name": <function-name>, "arguments": <args-json-object>}
</tool_call>

# When to use tools

If the user asks about weather at their current location:
1. First call get_location to retrieve coordinates
2. Then call get_weather using the latitude and longitude from step 1
3. Continue using tools until you have all information needed to answer completely

The responses from these function calls will be appended to this dialogue. Please provide responses based on the information from these function calls.
<|im_end|>
```

**Expected flow for "what's the weather here?":**

```
<|im_start|>user
what's the weather here?
<|im_end|>

<|im_start|>assistant
<tool_call>
{"name": "get_location", "arguments": {}}
</tool_call>
<|im_end|>

<|im_start|>tool
<tool_response>
{"latitude": 37.7749, "longitude": -122.4194}
</tool_response>
<|im_end|>

<|im_start|>assistant
<tool_call>
{"name": "get_weather", "arguments": {"latitude": 37.7749, "longitude": -122.4194}}
</tool_call>
<|im_end|>

<|im_start|>tool
<tool_response>
{"temperature": 65, "condition": "cloudy", "humidity": 72}
</tool_response>
<|im_end|>

<|im_start|>assistant
The weather at your current location is 65°F and cloudy with 72% humidity.
<|im_end|>
```

---

## **Scenario 3: Parallel function calling (bonus)**

```
<|im_start|>system
You are Qwen, created by Alibaba Cloud. You are a helpful assistant.

# Tools

You may call one or more functions to assist with the user query.

You are provided with function signatures within <tools></tools> XML tags:
<tools>
[
  {
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "Get the current weather for a specific location.",
      "parameters": {
        "type": "object",
        "properties": {
          "latitude": {
            "type": "number",
            "description": "Latitude coordinate"
          },
          "longitude": {
            "type": "number",
            "description": "Longitude coordinate"
          },
          "location_name": {
            "type": "string",
            "description": "Human-readable location name"
          }
        },
        "required": ["latitude", "longitude"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "get_time",
      "description": "Get the current time for a specific location.",
      "parameters": {
        "type": "object",
        "properties": {
          "latitude": {
            "type": "number",
            "description": "Latitude coordinate"
          },
          "longitude": {
            "type": "number",
            "description": "Longitude coordinate"
          }
        },
        "required": ["latitude", "longitude"]
      }
    }
  }
]
</tools>

For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags. You can call multiple functions at once if they don't depend on each other:
<tool_call>
{"name": <function-name>, "arguments": <args-json-object>}
</tool_call>

# When to use tools

If the user asks about both weather AND time for a location, call BOTH functions simultaneously since they don't depend on each other.
<|im_end|>
```

**Test query:** `"What's the weather and time in New York?"` 

**Expected response:**
```
<tool_call>
{"name": "get_weather", "arguments": {"latitude": 40.7128, "longitude": -74.0060, "location_name": "New York"}}
</tool_call>
<tool_call>
{"name": "get_time", "arguments": {"latitude": 40.7128, "longitude": -74.0060}}
</tool_call>
```

---

**⚠️ Important note from the guide:** The sequential scenario (Scenario 2) is where 0.6B models typically fail. The guide explicitly states that small models struggle to recognize they need a second tool call after receiving the first result. For reliable multi-step calling, use 7B+ models or implement external orchestration logic.