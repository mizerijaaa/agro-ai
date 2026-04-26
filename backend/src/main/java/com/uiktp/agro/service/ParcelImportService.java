package com.uiktp.agro.service;

import com.uiktp.agro.model.Parcel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ParcelImportService {

    public record ImportFieldError(String field, String message) {}

    public record ImportRowPreview(
            int rowNumber,
            Map<String, String> raw,
            ParcelDraft parsed,
            List<ImportFieldError> errors
    ) {}

    public record ImportPreviewResponse(
            String fileName,
            String fileType,
            List<String> requiredColumns,
            List<ImportRowPreview> rows,
            int totalRows,
            int validRows,
            int invalidRows
    ) {}

    public record ParcelDraft(
            String name,
            String location,
            String soilType,
            Double areaHa,
            Double latitude,
            Double longitude,
            String previousCrop,
            String currentCrop,
            String notes
    ) {}

    private static final List<String> REQUIRED = List.of("name", "latitude", "longitude", "areaHa", "soilType");

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("name", "name"),
            Map.entry("parcelname", "name"),
            Map.entry("ime", "name"),
            Map.entry("ime na parcela", "name"),
            Map.entry("parcela", "name"),

            Map.entry("location", "location"),
            Map.entry("lokacija", "location"),
            Map.entry("mesto", "location"),

            Map.entry("soiltype", "soilType"),
            Map.entry("soil type", "soilType"),
            Map.entry("soil_type", "soilType"),
            Map.entry("tip na pochva", "soilType"),
            Map.entry("tip na pocva", "soilType"),
            Map.entry("tip na po\u010Dva", "soilType"),
            Map.entry("pochva", "soilType"),
            Map.entry("pocva", "soilType"),
            Map.entry("po\u010Dva", "soilType"),

            Map.entry("areaha", "areaHa"),
            Map.entry("area (ha)", "areaHa"),
            Map.entry("area_ha", "areaHa"),
            Map.entry("area", "areaHa"),
            Map.entry("povrsina", "areaHa"),
            Map.entry("povr\u0161ina", "areaHa"),
            Map.entry("povrsina (ha)", "areaHa"),
            Map.entry("povr\u0161ina (ha)", "areaHa"),

            Map.entry("latitude", "latitude"),
            Map.entry("lat", "latitude"),
            Map.entry("geografska shirina", "latitude"),
            Map.entry("geografska \u0161irina", "latitude"),

            Map.entry("longitude", "longitude"),
            Map.entry("lon", "longitude"),
            Map.entry("geografska dolzhina", "longitude"),
            Map.entry("geografska dol\u017Eina", "longitude"),

            Map.entry("previouscrop", "previousCrop"),
            Map.entry("previous crop", "previousCrop"),
            Map.entry("previous_crop", "previousCrop"),
            Map.entry("prethodna kultura", "previousCrop"),
            Map.entry("prethodni kulturi", "previousCrop"),

            Map.entry("currentcrop", "currentCrop"),
            Map.entry("current crop", "currentCrop"),
            Map.entry("current_crop", "currentCrop"),
            Map.entry("momentna kultura", "currentCrop"),

            Map.entry("notes", "notes"),
            Map.entry("note", "notes"),
            Map.entry("zabeleski", "notes"),
            Map.entry("zabele\u0161ki", "notes")
    );

    public ImportPreviewResponse preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        String fn = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String lower = fn.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".csv")) {
                return previewCsv(file);
            }
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return previewExcel(file);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type. Upload .csv, .xls or .xlsx");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse file: " + e.getMessage(), e);
        }
    }

    public List<Parcel> toParcels(List<ParcelDraft> drafts) {
        if (drafts == null) return List.of();
        List<Parcel> out = new ArrayList<>();
        for (ParcelDraft d : drafts) {
            Parcel p = new Parcel();
            p.setName(safeTrim(d.name()));
            String loc = safeTrim(d.location());
            p.setLocation(StringUtils.hasText(loc) ? loc : "—");
            p.setSoilType(StringUtils.hasText(d.soilType()) ? safeTrim(d.soilType()) : "—");
            p.setAreaHa(d.areaHa() != null ? d.areaHa() : 0.0);
            p.setLatitude(d.latitude());
            p.setLongitude(d.longitude());
            List<String> crops = new ArrayList<>();
            if (StringUtils.hasText(d.previousCrop())) crops.add(safeTrim(d.previousCrop()));
            if (StringUtils.hasText(d.currentCrop())) crops.add(safeTrim(d.currentCrop()));
            p.setPreviousCrops(crops);
            out.add(p);
        }
        return out;
    }

    public List<ImportFieldError> validateDraft(ParcelDraft d) {
        List<ImportFieldError> errs = new ArrayList<>();
        if (d == null) {
            errs.add(new ImportFieldError("_row", "Missing row data"));
            return errs;
        }
        if (!StringUtils.hasText(d.name())) errs.add(new ImportFieldError("name", "Required"));
        if (d.latitude() == null) errs.add(new ImportFieldError("latitude", "Required"));
        else if (d.latitude() < -90 || d.latitude() > 90) errs.add(new ImportFieldError("latitude", "Must be between -90 and 90"));
        if (d.longitude() == null) errs.add(new ImportFieldError("longitude", "Required"));
        else if (d.longitude() < -180 || d.longitude() > 180) errs.add(new ImportFieldError("longitude", "Must be between -180 and 180"));
        if (d.areaHa() == null) errs.add(new ImportFieldError("area", "Required"));
        else if (d.areaHa() <= 0) errs.add(new ImportFieldError("area", "Must be > 0"));
        if (!StringUtils.hasText(d.soilType())) errs.add(new ImportFieldError("soil_type", "Required"));
        return errs;
    }

    private ImportPreviewResponse previewCsv(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(r)) {
            Map<String, String> canonByActual = canonicalizeHeaders(parser.getHeaderMap().keySet());
            ensureRequiredHeadersPresent(canonByActual.values());

            List<ImportRowPreview> rows = new ArrayList<>();
            int valid = 0;
            int invalid = 0;
            int rowNo = 1; // data rows start at 1 for user after header
            for (CSVRecord rec : parser) {
                Map<String, String> raw = new LinkedHashMap<>();
                for (String actual : canonByActual.keySet()) {
                    raw.put(actual, rec.isMapped(actual) ? safeTrim(rec.get(actual)) : "");
                }
                ParcelDraft draft = draftFromMap(toCanonicalValueMap(raw, canonByActual));
                List<ImportFieldError> errors = validateDraft(draft);
                if (errors.isEmpty()) valid++; else invalid++;
                rows.add(new ImportRowPreview(rowNo, raw, draft, errors));
                rowNo++;
                if (rows.size() >= 500) break; // protect UI; still enough for preview
            }
            return new ImportPreviewResponse(
                    file.getOriginalFilename(),
                    "csv",
                    REQUIRED,
                    rows,
                    valid + invalid,
                    valid,
                    invalid
            );
        }
    }

    private ImportPreviewResponse previewExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel file has no sheets");
            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel sheet is empty");
            Row headerRow = it.next();
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            List<String> headers = new ArrayList<>();
            int last = headerRow.getLastCellNum();
            for (int i = 0; i < last; i++) {
                Cell c = headerRow.getCell(i);
                String v = c == null ? "" : safeTrim(fmt.formatCellValue(c));
                headers.add(v);
            }
            Map<String, String> canonByActual = canonicalizeHeaders(headers);
            ensureRequiredHeadersPresent(canonByActual.values());

            List<ImportRowPreview> rows = new ArrayList<>();
            int valid = 0;
            int invalid = 0;
            int rowNo = 1; // first data row after header
            while (it.hasNext()) {
                Row row = it.next();
                if (row == null) continue;
                Map<String, String> raw = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String actual = headers.get(i);
                    if (actual == null) continue;
                    Cell c = row.getCell(i);
                    String v = cellToString(fmt, c);
                    raw.put(actual, v);
                }
                if (raw.values().stream().allMatch(v -> !StringUtils.hasText(v))) {
                    rowNo++;
                    continue;
                }
                ParcelDraft draft = draftFromMap(toCanonicalValueMap(raw, canonByActual));
                List<ImportFieldError> errors = validateDraft(draft);
                if (errors.isEmpty()) valid++; else invalid++;
                rows.add(new ImportRowPreview(rowNo, raw, draft, errors));
                rowNo++;
                if (rows.size() >= 500) break;
            }

            return new ImportPreviewResponse(
                    file.getOriginalFilename(),
                    "excel",
                    REQUIRED,
                    rows,
                    valid + invalid,
                    valid,
                    invalid
            );
        }
    }

    private static String cellToString(DataFormatter fmt, Cell c) {
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC) {
            // formatter is locale sensitive; we want dot.
            String s = fmt.formatCellValue(c);
            return safeTrim(s).replace(",", ".");
        }
        return safeTrim(fmt.formatCellValue(c));
    }

    private static Map<String, String> canonicalizeHeaders(Collection<String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String h : headers) {
            if (!StringUtils.hasText(h)) continue;
            String canon = toCanonicalHeader(h);
            if (canon == null) continue;
            out.put(h, canon);
        }
        return out;
    }

    private static String toCanonicalHeader(String header) {
        if (header == null) return null;
        String k = header.trim().toLowerCase(Locale.ROOT);
        k = k.replace("\uFEFF", ""); // BOM if any
        k = k.replace("_", " ");
        k = k.replaceAll("\\s+", " ").trim();
        k = k.replace("(", "").replace(")", "");
        k = k.replace(":", "");
        k = k.trim();
        String direct = HEADER_ALIASES.get(k);
        if (direct != null) return direct;
        String collapsed = k.replace(" ", "");
        return HEADER_ALIASES.get(collapsed);
    }

    private static void ensureRequiredHeadersPresent(Collection<String> canonicalHeaders) {
        Set<String> set = new HashSet<>(canonicalHeaders);
        for (String r : REQUIRED) {
            if (!set.contains(r)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Missing required column: " + r + ". Required: " + String.join(", ", REQUIRED)
                );
            }
        }
    }

    private static Map<String, String> toCanonicalValueMap(Map<String, String> raw, Map<String, String> canonByActual) {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String actual = e.getKey();
            String canon = canonByActual.get(actual);
            if (canon == null) continue;
            String val = e.getValue();
            if (!StringUtils.hasText(val)) continue;
            m.put(canon, val);
        }
        return m;
    }

    private static ParcelDraft draftFromMap(Map<String, String> v) {
        String name = safeTrim(v.get("name"));
        String location = safeTrim(v.get("location"));
        String soilType = safeTrim(v.get("soilType"));
        Double areaHa = parseDouble(v.get("areaHa"));
        Double lat = parseDouble(v.get("latitude"));
        Double lon = parseDouble(v.get("longitude"));
        String previousCrop = safeTrim(v.get("previousCrop"));
        String currentCrop = safeTrim(v.get("currentCrop"));
        String notes = safeTrim(v.get("notes"));
        return new ParcelDraft(name, location, soilType, areaHa, lat, lon, previousCrop, currentCrop, notes);
    }

    private static Double parseDouble(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim().replace(",", ".");
        try {
            // BigDecimal handles both "2" and "2.5" reliably.
            return new BigDecimal(t).doubleValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}

