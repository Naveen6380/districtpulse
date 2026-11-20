package org.example.districtpulse.news;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.districtpulse.platform.DocumentStore;

@RestController @RequestMapping("/api/news/import") @PreAuthorize("hasAnyRole('collector','administrator')")
public class DocumentImportController {
    private final NewsArticleRepository articles;private final PipelineCoordinator pipeline;private final DocumentStore store;
    @Value("${OCR_EXECUTABLE:}") private String ocr;
    public DocumentImportController(NewsArticleRepository articles,PipelineCoordinator pipeline,DocumentStore store){this.articles=articles;this.pipeline=pipeline;this.store=store;}
    @PostMapping(consumes="multipart/form-data") public Map<String,Object> upload(@RequestParam MultipartFile file,@RequestParam String title,@RequestParam String sourceUrl,@RequestParam String source,@RequestParam String publishedAt,@RequestParam boolean authorised,Authentication auth) throws Exception {
        if(!authorised)throw new IllegalArgumentException("Authorised content only");if(file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("File must be 1 byte to 10MB");
        var uri=java.net.URI.create(sourceUrl);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Public attribution URL required");
        if(title.isBlank()||title.length()>500||source.isBlank()||source.length()>255)throw new IllegalArgumentException("Invalid title or source");
        LocalDateTime publication;try{publication=OffsetDateTime.parse(publishedAt).atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDateTime();}catch(Exception e){throw new IllegalArgumentException("Publication date must have a timezone");}
        var now=LocalDateTime.now(ZoneId.of("Asia/Kolkata"));if(publication.isAfter(now)||publication.isBefore(now.minusDays(30)))throw new IllegalArgumentException("Article must be published within the last 30 days");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sourceUrl.getBytes(StandardCharsets.UTF_8)));if(articles.existsByUrlHash(hash))return Map.of("status","duplicate","message","This source URL was already imported");
        byte[] bytes=file.getBytes();String filename=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT),text;
        if(filename.endsWith(".pdf")){try(var pdf=Loader.loadPDF(bytes)){if(pdf.getNumberOfPages()>100)throw new IllegalArgumentException("Maximum 100 PDF pages");text=new PDFTextStripper().getText(pdf);}}
        else if(filename.endsWith(".xlsx")){try(var workbook=WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))){StringBuilder b=new StringBuilder();for(var sheet:workbook)for(var row:sheet)for(var cell:row){if(b.length()>100000)throw new IllegalArgumentException("Too much workbook text");b.append(cell).append(' ');}text=b.toString();}}
        else if(filename.endsWith(".txt")||filename.endsWith(".csv"))text=new String(bytes,StandardCharsets.UTF_8);
        else if(filename.endsWith(".png")||filename.endsWith(".jpg")||filename.endsWith(".jpeg"))text=ocr(bytes,filename.endsWith(".png")?".png":".jpg");
        else throw new IllegalArgumentException("Supported formats: text, CSV, XLSX, text PDF, PNG and JPEG");
        if(text.isBlank())throw new IllegalArgumentException("No readable text. For scanned PDFs, export page images and use OCR import");if(text.length()>100000)throw new IllegalArgumentException("Extracted text exceeds 100,000 characters");
        NewsArticle article=new NewsArticle();article.setTitle(title);article.setUrl(sourceUrl);article.setUrlHash(hash);article.setSource(source);article.setPublishedAt(publication);article.setCollectedAt(now);article.setContent(text);articles.save(article);store.audit(auth.getName(),"news.imported",article.getId().toString());return Map.of("articleId",article.getId(),"pipeline",pipeline.start(false));
    }
    private String ocr(byte[] bytes,String extension) throws Exception {
        if(ocr.isBlank())throw new IllegalArgumentException("OCR_EXECUTABLE is not configured; install Tesseract with eng/tam language packs");
        Path dir=Files.createTempDirectory("districtpulse-ocr-");Path input=dir.resolve("input"+extension),output=dir.resolve("result");Process process=null;
        try{Files.write(input,bytes);process=new ProcessBuilder(ocr,input.toString(),output.toString(),"-l","eng+tam").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalArgumentException("OCR timed out");}if(process.exitValue()!=0)throw new IllegalArgumentException("OCR failed; check image and language packs");return Files.readString(dir.resolve("result.txt"));}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();try(var files=Files.list(dir)){for(Path p:files.toList())Files.deleteIfExists(p);}Files.deleteIfExists(dir);}
    }
}
