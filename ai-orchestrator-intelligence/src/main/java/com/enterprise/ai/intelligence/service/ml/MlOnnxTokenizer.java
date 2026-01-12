package com.enterprise.ai.intelligence.service.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple tokenizer for ONNX models.
 * For production use, replace with BERT tokenizer (e.g., HuggingFace tokenizer).
 * 
 * This is a basic word-based tokenizer. For BERT models, use proper tokenization
 * (e.g., WordPiece tokenization from HuggingFace).
 */
@Slf4j
@Component
public class MlOnnxTokenizer {

    private static final int DEFAULT_MAX_LENGTH = 512;
    private static final String UNK_TOKEN = "[UNK]";
    private static final String PAD_TOKEN = "[PAD]";
    private static final String CLS_TOKEN = "[CLS]";
    private static final String SEP_TOKEN = "[SEP]";

    /**
     * Tokenize text into tokens (simple word-based tokenization).
     * For BERT models, this should be replaced with proper WordPiece tokenization.
     * 
     * @param text Input text
     * @return List of token IDs
     */
    public List<Long> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Simple word-based tokenization (split by whitespace and punctuation)
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");

        List<Long> tokens = new ArrayList<>();
        
        // Add CLS token at start (for BERT-style models)
        tokens.add(1L); // CLS token ID (placeholder - use actual vocab)

        // Convert words to token IDs (simple hash-based mapping)
        // For production, use actual vocabulary mapping from tokenizer
        for (String word : words) {
            if (!word.isEmpty()) {
                // Simple hash-based token ID (replace with actual vocab lookup)
                long tokenId = Math.abs(word.hashCode() % 30000) + 100; // Placeholder
                tokens.add(tokenId);
            }
        }

        // Add SEP token at end (for BERT-style models)
        tokens.add(2L); // SEP token ID (placeholder - use actual vocab)

        return tokens;
    }

    /**
     * Tokenize and pad/truncate to fixed length.
     * 
     * @param text Input text
     * @param maxLength Maximum sequence length
     * @return Array of token IDs with padding/truncation
     */
    public long[] tokenizeAndPad(String text, int maxLength) {
        List<Long> tokens = tokenize(text);
        
        long[] result = new long[maxLength];
        int length = Math.min(tokens.size(), maxLength);
        
        // Copy tokens
        for (int i = 0; i < length; i++) {
            result[i] = tokens.get(i);
        }
        
        // Pad with 0 (PAD token ID)
        for (int i = length; i < maxLength; i++) {
            result[i] = 0L;
        }
        
        return result;
    }

    /**
     * Create attention mask (1 for real tokens, 0 for padding).
     * 
     * @param tokenIds Token IDs
     * @return Attention mask
     */
    public long[] createAttentionMask(long[] tokenIds) {
        long[] mask = new long[tokenIds.length];
        for (int i = 0; i < tokenIds.length; i++) {
            mask[i] = tokenIds[i] != 0 ? 1L : 0L;
        }
        return mask;
    }

    /**
     * Tokenize text into input format for ONNX models.
     * Returns token IDs and attention mask.
     * 
     * @param text Input text
     * @param maxLength Maximum sequence length
     * @return Map with "input_ids" and "attention_mask" arrays
     */
    public Map<String, long[]> tokenizeForOnnx(String text, int maxLength) {
        long[] inputIds = tokenizeAndPad(text, maxLength);
        long[] attentionMask = createAttentionMask(inputIds);
        
        Map<String, long[]> result = new HashMap<>();
        result.put("input_ids", inputIds);
        result.put("attention_mask", attentionMask);
        return result;
    }
}
