package com.asraoui.chatbotrag.config;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;


import java.io.File;
import java.nio.file.Path;
import java.util.List;

//@Component
public class DataLoaderRag {
    @Value("classpath:/pdfs/*")
    private Resource[] pdfFiles;

    @Value("enset-vs1.json")
    private String vectorStoreName;



    private static final Logger logger = LoggerFactory.getLogger(DataLoaderRag.class);

    //@Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        String path = Path.of("src", "main", "resources", "vectorstore").toFile().getAbsolutePath() + "/" + vectorStoreName;
        File fileStore = new File(path);

        if (fileStore.exists()) {
            logger.info("Vector store exists: " + path);
            simpleVectorStore.load(fileStore);
        } else {
            try {
                for (Resource pdfFile : pdfFiles) {
                    PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(pdfFile);
                    List<Document> documents = pagePdfDocumentReader.get();

                    if (documents.isEmpty()) {
                        logger.warn("No text found in PDF. Applying OCR...");
                        String extractedText = applyOCR(pdfFile);
                        if (extractedText.isBlank()) {
                            logger.error("OCR did not extract any text. Skipping vectorization.");
                            return simpleVectorStore;
                        }
                        documents = List.of(new Document(extractedText));
                    }

                    TextSplitter textSplitter = new TokenTextSplitter();
                    List<Document> chunks = textSplitter.split(documents);

                    if (chunks.isEmpty()) {
                        logger.warn("Text splitting resulted in no chunks. Skipping vector store creation.");
                        return simpleVectorStore;
                    }


                    File parentDir = fileStore.getParentFile();
                    if (!parentDir.exists()) {
                        boolean created = parentDir.mkdirs();
                        if (!created) {
                            logger.error("Échec de la création du dossier: " + parentDir.getAbsolutePath());
                            return simpleVectorStore; // Arrête la sauvegarde si le dossier ne peut pas être créé
                        }
                    }

                    simpleVectorStore.add(chunks);
                    simpleVectorStore.save(fileStore);
                }
            } catch (Exception e) {
                logger.error("Error processing PDF for vector store", e);
            }
        }

        return simpleVectorStore;
    }

    private String applyOCR(Resource pdfFile) {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata"); // Assurez-vous que le dossier contient les fichiers de langue OCR
        tesseract.setLanguage("eng+fra+ara");
        try {
            File file = pdfFile.getFile();
            return tesseract.doOCR(file);
        } catch (TesseractException | java.io.IOException e) {
            logger.error("OCR processing failed", e);
            return "";
        }
    }



}
