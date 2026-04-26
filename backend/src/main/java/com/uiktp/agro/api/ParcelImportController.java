package com.uiktp.agro.api;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.repo.AppUserRepository;
import com.uiktp.agro.repo.ParcelRepository;
import com.uiktp.agro.service.CurrentUserService;
import com.uiktp.agro.service.ParcelImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/parcels/import")
public class ParcelImportController {
    private final ParcelImportService parcelImportService;
    private final CurrentUserService currentUserService;
    private final AppUserRepository appUserRepository;
    private final ParcelRepository parcelRepository;

    public ParcelImportController(
            ParcelImportService parcelImportService,
            CurrentUserService currentUserService,
            AppUserRepository appUserRepository,
            ParcelRepository parcelRepository) {
        this.parcelImportService = parcelImportService;
        this.currentUserService = currentUserService;
        this.appUserRepository = appUserRepository;
        this.parcelRepository = parcelRepository;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParcelImportService.ImportPreviewResponse preview(@RequestPart("file") MultipartFile file) {
        currentUserService.requireCurrentUser();
        return parcelImportService.preview(file);
    }

    @PostMapping("/commit")
    public CommitResponse commit(@Valid @RequestBody CommitRequest request) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        AppUser managed = appUserRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rows is required");
        }
        if (request.rows().size() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many rows (max 2000)");
        }

        List<ParcelImportService.ParcelDraft> drafts = request.rows();
        List<RowResult> results = new ArrayList<>();
        List<Parcel> toSave = new ArrayList<>();
        boolean[] invalid = new boolean[drafts.size() + 1];

        int idx = 0;
        for (ParcelImportService.ParcelDraft d : drafts) {
            idx++;
            var errors = parcelImportService.validateDraft(d);
            if (!errors.isEmpty()) {
                results.add(new RowResult(idx, false, null, errors));
                invalid[idx] = true;
                continue;
            }
            Parcel p = parcelImportService.toParcels(List.of(d)).get(0);
            normalizeParcelForDb(p);
            p.setUser(managed);
            toSave.add(p);
        }

        List<Parcel> saved = parcelRepository.saveAll(toSave);
        int savedIdx = 0;
        for (int i = 0; i < drafts.size(); i++) {
            int rowNum = i + 1;
            if (invalid[rowNum]) continue;
            Parcel sp = saved.get(savedIdx++);
            results.add(new RowResult(rowNum, true, sp.getId(), List.of()));
        }
        results.sort((a, b) -> Integer.compare(a.rowNumber(), b.rowNumber()));

        return new CommitResponse(saved.size(), results);
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> templateCsv() {
        currentUserService.requireCurrentUser();
        String bom = "\uFEFF";
        String csv = String.join(",", List.of("name", "location", "latitude", "longitude", "area", "soil_type", "previous_crop", "current_crop", "notes")) + "\n"
                + "Парцелa Север, Скопје, 41.9981, 21.4254, 2.5, глинеста, пченица, пченка, пример парцела\n"
                + "Парцелa Исток, Велес, 41.7156, 21.7756, 3.2, песоклива, сончоглед, пченица, потребна проверка\n";
        byte[] bytes = (bom + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parcel-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> templateXlsx() {
        currentUserService.requireCurrentUser();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("parcels");
            var header = sheet.createRow(0);
            List<String> cols = List.of("name", "location", "latitude", "longitude", "area", "soil_type", "previous_crop", "current_crop", "notes");
            for (int i = 0; i < cols.size(); i++) {
                header.createCell(i).setCellValue(cols.get(i));
            }
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Парцелa Север");
            row1.createCell(1).setCellValue("Скопје");
            row1.createCell(2).setCellValue(41.9981);
            row1.createCell(3).setCellValue(21.4254);
            row1.createCell(4).setCellValue(2.5);
            row1.createCell(5).setCellValue("глинеста");
            row1.createCell(6).setCellValue("пченица");
            row1.createCell(7).setCellValue("пченка");
            row1.createCell(8).setCellValue("пример парцела");

            var row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Парцелa Исток");
            row2.createCell(1).setCellValue("Велес");
            row2.createCell(2).setCellValue(41.7156);
            row2.createCell(3).setCellValue(21.7756);
            row2.createCell(4).setCellValue(3.2);
            row2.createCell(5).setCellValue("песоклива");
            row2.createCell(6).setCellValue("сончоглед");
            row2.createCell(7).setCellValue("пченица");
            row2.createCell(8).setCellValue("потребна проверка");
            for (int i = 0; i < cols.size(); i++) sheet.autoSizeColumn(i);
            wb.write(out);
            byte[] bytes = out.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parcel-import-template.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate template", e);
        }
    }

    private static void normalizeParcelForDb(Parcel parcel) {
        if (parcel.getLocation() == null || parcel.getLocation().trim().isEmpty()) parcel.setLocation("—");
        if (!StringUtils.hasText(parcel.getSoilType())) parcel.setSoilType("—");
        if (parcel.getPreviousCrops() == null) parcel.setPreviousCrops(new ArrayList<>());
    }

    public record CommitRequest(List<ParcelImportService.ParcelDraft> rows) {}

    public record CommitResponse(int created, List<RowResult> results) {}

    public record RowResult(
            int rowNumber,
            boolean created,
            Long parcelId,
            List<ParcelImportService.ImportFieldError> errors
    ) {}
}

