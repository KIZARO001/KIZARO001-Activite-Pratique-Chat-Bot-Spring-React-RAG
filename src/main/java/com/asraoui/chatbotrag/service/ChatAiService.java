package com.asraoui.chatbotrag.service;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

@BrowserCallable
@AnonymousAllowed
public class ChatAiService {
    private ChatClient chatClient;
    private VectorStore vectorStore;

    @Value("classpath:/prompts/prompt.st")
    private Resource promptResource;

    public ChatAiService(ChatClient.Builder chatClient,VectorStore vectorStore) {
        this.chatClient = chatClient.build();
        this.vectorStore=vectorStore;
    }

    public String ragChat(String question) {

        PromptTemplate promptTemplate= new PromptTemplate(promptResource);
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).build()
        );
        List<String> contexte = documents.stream().map(d -> d.getContent()).toList();
        Prompt prompt = promptTemplate.create(Map.of("context", contexte, "question", question));
        return chatClient.prompt(prompt).call().content();


        //return chatClient.prompt().user(question).call().content();
    }
}
