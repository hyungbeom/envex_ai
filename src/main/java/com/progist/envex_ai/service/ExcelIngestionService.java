package com.progist.envex_ai.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import com.progist.envex_ai.util.CompanyFacts;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ExcelIngestionService {

    private final VectorStore vectorStore;

    public ExcelIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // 🌟 스프링 서버가 완벽하게 켜진 직후에 자동으로 이 메서드를 1번 실행합니다.
    @EventListener(ApplicationReadyEvent.class)
    public void loadExcelOnStartup() {
        System.out.println("🚀 [시스템] 서버가 시작되었습니다. result.xlsx 데이터를 Vector DB로 로딩합니다...");
        List<Document> documentsToSave = new ArrayList<>();
        int withLogo = 0;

        // 🌟 resources 폴더 안에 있는 result.xlsx 파일을 찾아서 읽어옵니다.
        try (InputStream is = new ClassPathResource("result.xlsx").getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // 첫 번째 줄(제목 행) 건너뛰기
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // 💡 열 번호(Index) 추출 (※ 이전 답변에서 맞추신 엑셀 열 번호를 그대로 사용하세요!)
                String comCode     = getCellValue(row.getCell(0));   // A열
                String companyH    = getCellValue(row.getCell(7));   // H열
                String boot        = getCellValue(row.getCell(71));  // BT열
                String onCateKor   = getCellValue(row.getCell(95));  // ON_CATE_KOR
                String tel         = getCellValue(row.getCell(16));  // Tel
                String homepage    = getCellValue(row.getCell(18));  // Homepage
                String keyword     = getCellValue(row.getCell(105)); // keyword
                String infoH       = getCellValue(row.getCell(106)); // InfoH
                String itemH       = getCellValue(row.getCell(108)); // ItemH
                String brandH1     = getCellValue(row.getCell(120)); // BrandH1
                String itemH2      = getCellValue(row.getCell(126)); // ItemH2
                String brandH2     = getCellValue(row.getCell(121)); // BrandH2
                String itemH3      = getCellValue(row.getCell(127)); // ItemH3
                String logo = resolveLogoFromRow(row);

                if (companyH.contains("천세")) {
                    System.out.println("🚨 [수사 1] 엑셀에서 천세 발견! -> 기업명: " + companyH + " / 부스: " + boot + " / A열값: " + comCode);
                }
                if (comCode.isEmpty() || companyH.isEmpty() || companyH.equalsIgnoreCase("NULL")) {
                    continue;
                }

// --- 텍스트 조립 ---
                StringBuilder textBuilder = new StringBuilder();
                // 🌟 AI가 로고 파일명을 인식할 수 있게 심어줍니다.
                textBuilder.append(String.format("■ 기업명: %s", companyH));
                if (!logo.isEmpty() && !logo.equalsIgnoreCase("NULL")) {
                    String fullLogoUrl = toLogoUrl(logo);
                    textBuilder.append(String.format(" (로고 URL: %s)", fullLogoUrl));
                }
                textBuilder.append("\n");


                if (!onCateKor.isEmpty() && !onCateKor.equalsIgnoreCase("NULL")) textBuilder.append(String.format(" (전시분야: %s)", onCateKor));
                textBuilder.append("\n");
                if (!boot.isEmpty() && !boot.equalsIgnoreCase("NULL")) textBuilder.append(String.format("■ 부스 번호: %s\n", boot));
                textBuilder.append(String.format("■ 연락처: %s / 홈페이지: %s\n", tel, homepage));
                if (!keyword.isEmpty() && !keyword.equalsIgnoreCase("NULL")) textBuilder.append(String.format("■ 주요 키워드: %s\n", keyword));
                textBuilder.append("\n");

                if (!infoH.isEmpty() && !infoH.equalsIgnoreCase("NULL")) textBuilder.append("[회사 소개]\n").append(infoH).append("\n\n");
                if (!itemH.isEmpty() && !itemH.equalsIgnoreCase("NULL")) textBuilder.append("[주력 제품 및 핵심 기술]\n").append(itemH).append("\n\n");

                boolean hasSubItem = (!brandH1.isEmpty() && !brandH1.equalsIgnoreCase("NULL")) || (!brandH2.isEmpty() && !brandH2.equalsIgnoreCase("NULL"));
                if (hasSubItem) {
                    textBuilder.append("[세부 제품 라인업]\n");
                    if (!brandH1.isEmpty() && !brandH1.equalsIgnoreCase("NULL")) textBuilder.append("- ").append(brandH1).append("\n");
                    if (!itemH2.isEmpty() && !itemH2.equalsIgnoreCase("NULL")) textBuilder.append("  * 상세설명: ").append(itemH2).append("\n");
                    if (!brandH2.isEmpty() && !brandH2.equalsIgnoreCase("NULL")) textBuilder.append("- ").append(brandH2).append("\n");
                    if (!itemH3.isEmpty() && !itemH3.equalsIgnoreCase("NULL")) textBuilder.append("  * 상세설명: ").append(itemH3).append("\n");
                }

                // Document 및 메타데이터 생성
                Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("company_id", comCode);
                metadata.put("company_name", companyH);
                if (!boot.isEmpty() && !boot.equalsIgnoreCase("NULL")) {
                    metadata.put("booth_number", boot.trim());
                }
                if (!logo.isEmpty() && !logo.equalsIgnoreCase("NULL")) {
                    metadata.put("logo_url", toLogoUrl(logo));
                    withLogo++;
                }
                documentsToSave.add(new Document(textBuilder.toString(), metadata));
            }

            // Vector DB에 저장
            if (!documentsToSave.isEmpty()) {
                vectorStore.accept(documentsToSave);
                System.out.println("✅ [시스템] 총 " + documentsToSave.size() + "개의 기업 정보가 Vector DB에 성공적으로 적재되었습니다!");
                System.out.println("   └ 로고 URL 포함: " + withLogo + "건");
            }

        } catch (Exception e) {
            System.err.println("❌ [시스템] 엑셀 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static final int[] LOGO_COLUMN_CANDIDATES = {110, 109, 111, 108, 107, 112};

    private String resolveLogoFromRow(Row row) {
        for (int columnIndex : LOGO_COLUMN_CANDIDATES) {
            String value = getCellValue(row.getCell(columnIndex));
            if (isLikelyLogoValue(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isLikelyLogoValue(String value) {
        if (value == null || value.isBlank() || "NULL".equalsIgnoreCase(value.trim())) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }
        return trimmed.matches("(?i).+\\.(jpg|jpeg|png|gif|webp|bmp)$");
    }

    private static String toLogoUrl(String logo) {
        String trimmed = logo.trim();
        String url;
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            url = trimmed;
        } else {
            url = "https://envex.or.kr/board/upload_file/ENVEX_form2/" + trimmed;
        }
        return CompanyFacts.sanitizeLogoUrl(url);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
}