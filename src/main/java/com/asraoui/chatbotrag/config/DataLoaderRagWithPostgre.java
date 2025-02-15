package com.asraoui.chatbotrag.config;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class DataLoaderRagWithPostgre {

    private static final Logger logger = LoggerFactory.getLogger(DataLoaderRagWithPostgre.class);

    @Value("classpath*:pdfs/*.pdf")
    private Resource[] pdfFiles;

    private final JdbcClient jdbcClient;
    private final VectorStore vectorStore;

    public DataLoaderRagWithPostgre(JdbcClient jdbcClient, VectorStore vectorStore) {
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void initStore() {
        try {
            // Vérifier si la table est vide
            Integer count = jdbcClient.sql("SELECT COUNT(*) FROM vector_store")
                    .query(Integer.class)
                    .single(); // Retourne 0 si la requête échoue

            if (count == 0) {
                for (Resource pdfFile : pdfFiles) {
                    processPdfFile(pdfFile);
                }
            }
        } catch (Exception e) {
            logger.error("Error initializing vector store", e);
        }
    }

    private void processPdfFile(Resource pdfFile) {
        try {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfFile);
            List<Document> documents = pdfReader.get();

            if (documents.isEmpty()) {
                logger.warn("No text found in PDF. Applying OCR...");
                String extractedText = applyOCR(pdfFile);

                if (extractedText.isBlank()) {
                    logger.error("OCR did not extract any text. Skipping vectorization.");
                    return;
                }
                documents = List.of(new Document(extractedText));
            }

            TextSplitter textSplitter = new TokenTextSplitter();
            List<Document> chunks = textSplitter.split(documents);

            if (chunks.isEmpty()) {
                logger.warn("Text splitting resulted in no chunks. Skipping vector store creation.");
                return;
            }

            vectorStore.add(chunks);
            logger.info("Added {} chunks to vector store", chunks.size());

        } catch (Exception e) {
            logger.error("Error processing PDF: " + pdfFile.getFilename(), e);
        }
    }

    private String applyOCR(Resource pdfFile) {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata"); // Vérifier que le dossier tessdata existe
        tesseract.setLanguage("eng+fra+ara"); // Ajout de plusieurs langues

        try {
            File file = pdfFile.getFile();
            return tesseract.doOCR(file);
        } catch (TesseractException | java.io.IOException e) {
            logger.error("OCR processing failed", e);
            return "";
        }
    }
}
