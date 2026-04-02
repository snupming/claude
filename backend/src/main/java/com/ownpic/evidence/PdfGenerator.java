package com.ownpic.evidence;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class PdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerator.class);

    /**
     * 템플릿 이름으로 HTML을 로드하고 데이터를 바인딩한다.
     * 간단한 {{ key }} 치환 방식 사용.
     */
    public String renderTemplate(String templateName, Map<String, Object> data) {
        try {
            var resource = new ClassPathResource("templates/" + templateName + ".html");
            String html;
            try (InputStream is = resource.getInputStream()) {
                html = new String(is.readAllBytes());
            }

            // 단순 변수 치환
            for (var entry : data.entrySet()) {
                if (entry.getValue() instanceof String || entry.getValue() instanceof Number) {
                    html = html.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
                }
            }

            // 리스트 처리 ({{#list}} ... {{/list}} 블록)
            html = processLists(html, data);

            return html;
        } catch (Exception e) {
            log.error("Template rendering failed: {}", templateName, e);
            throw new RuntimeException("템플릿 렌더링 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String processLists(String html, Map<String, Object> data) {
        for (var entry : data.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                String startTag = "{{#" + entry.getKey() + "}}";
                String endTag = "{{/" + entry.getKey() + "}}";
                int startIdx = html.indexOf(startTag);
                int endIdx = html.indexOf(endTag);
                if (startIdx >= 0 && endIdx > startIdx) {
                    String template = html.substring(startIdx + startTag.length(), endIdx);
                    StringBuilder sb = new StringBuilder();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            String row = template;
                            for (var e : ((Map<String, Object>) map).entrySet()) {
                                row = row.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue() != null ? e.getValue() : ""));
                            }
                            sb.append(row);
                        }
                    }
                    html = html.substring(0, startIdx) + sb + html.substring(endIdx + endTag.length());
                }
            }
        }
        return html;
    }

    /**
     * HTML → PDF 변환 (OpenHTMLtoPDF)
     */
    public byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "/");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("PDF 생성 실패", e);
        }
    }

    /**
     * HTML → DOCX 변환 (간단한 텍스트 추출 방식)
     */
    public byte[] htmlToDocx(String html, String title) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            XWPFDocument doc = new XWPFDocument();

            // HTML에서 텍스트 추출
            Document jsoupDoc = Jsoup.parse(html);
            jsoupDoc.select("style, script").remove();

            for (Element el : jsoupDoc.body().children()) {
                String tagName = el.tagName();
                String text = el.text().trim();
                if (text.isEmpty()) continue;

                XWPFParagraph para = doc.createParagraph();

                if ("h1".equals(tagName) || "h2".equals(tagName) || "h3".equals(tagName)) {
                    var run = para.createRun();
                    run.setBold(true);
                    run.setFontSize("h1".equals(tagName) ? 18 : "h2".equals(tagName) ? 14 : 12);
                    run.setText(text);
                } else if ("table".equals(tagName)) {
                    // 테이블 → 텍스트 변환
                    for (Element row : el.select("tr")) {
                        var run = para.createRun();
                        run.setText(row.text());
                        run.addBreak();
                    }
                } else {
                    var run = para.createRun();
                    run.setFontSize(10);
                    run.setText(text);
                }
            }

            doc.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("DOCX generation failed", e);
            throw new RuntimeException("DOCX 생성 실패", e);
        }
    }
}
