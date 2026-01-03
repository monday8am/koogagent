---
description: Export HuggingFace models to LiteRT-LM format
---
// turbo-all
1. Install dependencies
   pip install ai-edge-torch torch transformers

2. Export model with custom context length
   python export_to_litert.py \
     --model_id Qwen/Qwen3-0.6B-Instruct \
     --output_path qwen3_0.6b_q8_ekv4096.litertlm \
     --max_seq_len 4096 \
     --quantize int8

# Context Size Recommendations
- 1024 tokens: Basic prompts and single-turn conversations
- 2048 tokens: Multi-turn conversations with moderate history
- 4096 tokens: Complex prompts with tool calling (recommended)
- 8192+ tokens: Long conversations with extensive tool calling

# Important Notes
- Only dynamic-8bits quantization supported on GPU L4 processors
- Larger context = more memory usage
- Runtime context cannot exceed compiled context window
- See ARCHITECTURE.md for detailed export instructions
