package com.storagehealth.application.service.ai;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.RecommendationEntity;
import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.domain.event.FileDiscoveredEvent;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
public class AiRecommendationAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public AiRecommendationAgent(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String modelName,
            FileRepository fileRepository,
            RecommendationRepository recommendationRepository) {
        
        this.chatLanguageModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .build();
                
        this.fileRepository = fileRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @KafkaListener(topics = "file-discovered-topic", groupId = "storage-health-ai-group")
    public void onFileDiscovered(FileDiscoveredEvent event) {
        log.info("AI Agent analyzing newly discovered file: {}", event.getPath());
        
        try {
            Optional<FileEntity> fileOpt = fileRepository.findById(event.getFileId());
            if (fileOpt.isEmpty()) {
                log.warn("File {} not found in database", event.getFileId());
                return;
            }
            FileEntity file = fileOpt.get();
            
            // Construct prompt for Llama 3
            String prompt = String.format("""
                You are an AI Storage Health Assistant. Evaluate the following file metadata and determine if it is likely to be clutter or safe to delete.
                Provide a short 1-sentence explanation of why, starting with 'CLUTTER:' or 'KEEP:'.
                
                File Name: %s
                Path: %s
                Size (bytes): %d
                Extension: %s
                MIME Type: %s
                """, event.getName(), event.getPath(), event.getSizeBytes(), event.getExtension(), event.getMimeType());
                
            String response = chatLanguageModel.generate(prompt);
            log.info("AI response for {}: {}", event.getName(), response);
            
            if (response != null && response.trim().toUpperCase().startsWith("CLUTTER:")) {
                String explanation = response.substring("CLUTTER:".length()).trim();
                
                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(file)
                    .type(RecommendationType.TEMP_FILE) // Map to existing enum
                    .confidenceScore(new BigDecimal("0.85")) // Default AI confidence
                    .explanation("AI Agent Analysis: " + explanation)
                    .recoverableSpace(file.getSizeBytes())
                    .isActedOn(false)
                    .build();
                    
                recommendationRepository.save(rec);
            }
            
        } catch (Exception e) {
            log.error("AI analysis failed for file {}", event.getPath(), e);
        }
    }
}
