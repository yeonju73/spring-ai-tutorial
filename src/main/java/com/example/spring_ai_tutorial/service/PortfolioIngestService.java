package com.example.spring_ai_tutorial.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 앱 시작 시 portfolio-data/ 의 .md 파일을 ## 헤더 단위로 청킹하여 Qdrant에 적재
 *
 * chunk_id 형식: "파일상대경로__섹션-슬러그"  (예: projects/driveu__기술-스택)
 * metadata    : chunk_id, file, section, hash(MD5)
 */
@Slf4j
@Service
public class PortfolioIngestService implements ApplicationRunner {

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;
    private final String dataPath;
    private final String collectionName;

    public PortfolioIngestService(VectorStore vectorStore,
                                  QdrantClient qdrantClient,
                                  @Value("${portfolio.data-path}") String dataPath,
                                  @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName) {
        this.vectorStore = vectorStore;
        this.qdrantClient = qdrantClient;
        this.dataPath = dataPath;
        this.collectionName = collectionName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("포트폴리오 데이터 Qdrant 적재 시작. 경로: {}", dataPath);

        Path root = Paths.get(dataPath);
        if (!Files.exists(root)) {
            log.warn("portfolio.data-path 경로가 존재하지 않습니다: {}", root.toAbsolutePath());
            return;
        }

        // 기존 포인트 전체 삭제 (재시작 시 중복 방지) — 빈 Filter = 컬렉션 내 전체 포인트
        qdrantClient.deleteAsync(collectionName, Filter.newBuilder().build()).get();
        log.info("기존 포트폴리오 데이터 삭제 완료 (컬렉션: {})", collectionName);

        List<Document> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                 .sorted()
                 .forEach(path -> {
                     try {
                         List<Document> chunks = parseMarkdownToChunks(root, path);
                         documents.addAll(chunks);
                         log.debug("파싱 완료: {} → {}개 청크", root.relativize(path), chunks.size());
                     } catch (Exception e) {
                         log.error("파일 처리 실패: {}", path, e);
                     }
                 });
        }

        if (documents.isEmpty()) {
            log.warn("적재할 청크가 없습니다.");
            return;
        }

        vectorStore.add(documents);
        log.info("포트폴리오 데이터 적재 완료. 총 청크 수: {}", documents.size());
    }

    /**
     * 마크다운 파일을 ## 헤더 기준으로 파싱하여 Document 리스트로 반환
     * - # (h1) 은 파일 제목으로 간주하여 청크에서 제외
     * - ### 이하 서브헤더는 부모 ## 섹션의 내용으로 포함
     */
    private List<Document> parseMarkdownToChunks(Path root, Path filePath) throws IOException {
        // 상대 경로 + 확장자 제거 (예: "projects/driveu")
        String file = root.relativize(filePath).toString()
                          .replace("\\", "/")
                          .replaceAll("\\.md$", "");

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");

        List<Document> chunks = new ArrayList<>();
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## ")) {
                // 이전 섹션 저장
                if (currentSection != null) {
                    String sectionContent = currentContent.toString().strip();
                    if (!sectionContent.isBlank()) {
                        chunks.add(buildDocument(file, currentSection, sectionContent));
                    }
                }
                currentSection = line.substring(3).strip();
                currentContent = new StringBuilder();
            } else if (!line.startsWith("# ")) {
                // h1은 파일 제목이므로 스킵, 나머지는 현재 섹션 내용에 추가
                currentContent.append(line).append("\n");
            }
        }

        // 마지막 섹션 저장
        if (currentSection != null) {
            String sectionContent = currentContent.toString().strip();
            if (!sectionContent.isBlank()) {
                chunks.add(buildDocument(file, currentSection, sectionContent));
            }
        }

        return chunks;
    }

    private Document buildDocument(String file, String section, String content) {
        // 섹션명의 공백 → 하이픈 (예: "기술 스택" → "기술-스택")
        String sectionSlug = section.replace(" ", "-");
        String chunkId = file + "__" + sectionSlug;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunk_id", chunkId);
        metadata.put("file", file);
        metadata.put("section", section);
        metadata.put("hash", md5(content));

        return new Document(content, metadata);
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 해시 생성 실패", e);
            return "unknown";
        }
    }
}