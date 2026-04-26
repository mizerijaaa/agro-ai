package com.uiktp.agro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uiktp.agro.dto.ClimateDataDTO;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.repo.ClimateDataRepository;
import com.uiktp.agro.repo.ParcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class ClimateService {
    private static final Logger log = LoggerFactory.getLogger(ClimateService.class);
    private static final String OPEN_METEO = "https://api.open-meteo.com/v1/forecast";
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Europe/Skopje");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClimateDataRepository climateDataRepository;
    private final ParcelRepository parcelRepository;

    public ClimateService(
            RestTemplate restTemplate,
            ClimateDataRepository climateDataRepository,
            ParcelRepository parcelRepository) {
        this.restTemplate = restTemplate;
        this.climateDataRepository = climateDataRepository;
        this.parcelRepository = parcelRepository;
    }

    /**
     * Повик кон Open-Meteo за дадени координати (без hardcoded локација). Парсира JSON и враќа DTO.
     * Не зачувува во база — {@link #fetchForecastForParcel(Parcel)} ја вика за реална парцела и ја пишува во H2.
     */
    public ClimateDataDTO getClimateData(double lat, double lon) {
        String body = fetchJsonFromOpenMeteo(lat, lon);
        try {
            return parseJsonToDto(objectMapper.readTree(body), "open-meteo");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Open-Meteo response is not valid JSON", e);
        }
    }

    /**
     * Враќа true ако може да се повика Open-Meteo за оваа парцела.
     * Јавна за дашборд (јасна порака) и за препораки.
     */
    public boolean hasUsableWgs84(Parcel parcel) {
        return hasValidWgs84Coordinates(parcel);
    }

    /**
     * Open-Meteo + кеш. Со open-in-view=false, парцелата мора да биде управувана во оваа трансакција
     * инаку {@code persist(ClimateData → Parcel)} пушта исклучоци.
     */
    @Transactional
    public ClimateData fetchForecastForParcel(Parcel parcel) {
        Long parcelId = parcel.getId();
        if (parcelId != null) {
            final Long idForLoad = parcelId;
            parcel = parcelRepository.findById(idForLoad)
                    .orElseThrow(() -> new IllegalStateException("Parcel not found: " + idForLoad));
        }
        if (!hasValidWgs84Coordinates(parcel)) {
            log.warn("fetchForecastForParcel: id={} has no WGS84; returning default (no API call)",
                    parcel.getId() != null ? parcel.getId() : "new");
            return defaultClimateData(parcel);
        }
        double lat = parcel.getLatitude();
        double lon = parcel.getLongitude();

        RangeOfDay day = dayRangeInZone(LOCAL_ZONE, Instant.now());

        if (parcel.getId() != null) {
            try {
                List<ClimateData> today = climateDataRepository.findTodaysEntriesForParcel(
                        parcel.getId(), day.start(), day.end(), PageRequest.of(0, 1));
                if (!today.isEmpty()) {
                    ClimateData cached = today.get(0);
                    if (looksLikeFailedFetchCache(cached)) {
                        log.debug("Ignoring zero-filled cache for parcelId={}, refetching Open-Meteo", parcel.getId());
                    } else {
                        return cached;
                    }
                }
            } catch (Exception ex) {
                log.warn("Climate cache read failed for parcelId={}, refetching: {}", parcel.getId(), ex.getMessage());
            }
        }

        try {
            ClimateDataDTO dto = getClimateData(lat, lon);
            return persistAndReturn(parcel, dto);
        } catch (Exception ex) {
            if (parcel.getId() != null) {
                Optional<ClimateData> last = climateDataRepository.findFirstByParcelIdOrderByCreatedAtDesc(parcel.getId());
                if (last.isPresent() && !looksLikeFailedFetchCache(last.get())) {
                    return last.get();
                }
            }
            return defaultClimateData(parcel);
        }
    }

    private static boolean hasValidWgs84Coordinates(Parcel parcel) {
        Double la = parcel.getLatitude();
        Double lo = parcel.getLongitude();
        if (la == null || lo == null) {
            return false;
        }
        if (Double.isNaN(la) || Double.isNaN(lo)) {
            return false;
        }
        if (Math.abs(la) < 1e-4 && Math.abs(lo) < 1e-4) {
            return false;
        }
        return la >= -90.0 && la <= 90.0 && lo >= -180.0 && lo <= 180.0;
    }

    /** Stari записи со 0/0/0/0 јукаат повторен API повик. */
    private static boolean looksLikeFailedFetchCache(ClimateData c) {
        if (c == null) return true;
        return c.getTemperature() == 0.0
                && c.getPrecipitation() == 0.0
                && c.getEvapotranspiration() == 0.0
                && c.getSoilMoisture() == 0.0
                && (c.getSoilTemperature0cm() == 0.0);
    }

    private String fetchJsonFromOpenMeteo(double lat, double lon) {
        String url = UriComponentsBuilder
                .fromUri(URI.create(OPEN_METEO))
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,et0_fao_evapotranspiration")
                .queryParam("hourly", "soil_moisture_0_to_1cm,soil_moisture_3_to_9cm,soil_temperature_0cm")
                .queryParam("timezone", "Europe/Skopje")
                .queryParam("forecast_days", 7)
                .build()
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                throw new IllegalStateException("Open-Meteo response body is null");
            }
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("Open-Meteo request failed: " + e.getMessage(), e);
        }
    }

    private static ClimateDataDTO parseJsonToDto(JsonNode root, String source) {
        if (root.path("error").asBoolean(false)) {
            throw new IllegalStateException("Open-Meteo API: " + root.path("reason").asText("unknown error"));
        }
        double tMax = averageSeries(root.path("daily").path("temperature_2m_max"));
        double tMin = averageSeries(root.path("daily").path("temperature_2m_min"));
        double averageTemp = (tMax + tMin) / 2.0;
        double precip = averageSeries(root.path("daily").path("precipitation_sum"));
        double et0 = averageSeries(root.path("daily").path("et0_fao_evapotranspiration"));
        double s0 = averageSeries(root.path("hourly").path("soil_moisture_0_to_1cm"));
        double s3 = averageSeries(root.path("hourly").path("soil_moisture_3_to_9cm"));
        double soilMoisture = (s0 + s3) / 2.0;
        double soilTemp0 = averageSeries(root.path("hourly").path("soil_temperature_0cm"));
        return new ClimateDataDTO(averageTemp, precip, et0, soilMoisture, soilTemp0, source);
    }

    private ClimateData persistAndReturn(Parcel parcel, ClimateDataDTO dto) {
        ClimateData data = new ClimateData();
        data.setParcel(parcel);
        data.setTemperature(dto.averageTemperatureCelsius());
        data.setPrecipitation(dto.averageDailyPrecipitationMm());
        data.setSoilMoisture(dto.averageSoilMoisture());
        data.setEvapotranspiration(dto.averageEt0MmPerDay());
        data.setSoilTemperature0cm(dto.averageSoilTemperature0cmCelsius());
        data.setCreatedAt(Instant.now());
        return climateDataRepository.save(data);
    }

    private static ClimateData defaultClimateData(Parcel parcel) {
        ClimateData d = new ClimateData();
        d.setParcel(parcel);
        d.setTemperature(0);
        d.setPrecipitation(0);
        d.setSoilMoisture(0);
        d.setEvapotranspiration(0);
        d.setSoilTemperature0cm(0);
        d.setCreatedAt(Instant.now());
        return d;
    }

    private static double averageSeries(JsonNode arrayNode) {
        if (!arrayNode.isArray() || arrayNode.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode value : arrayNode) {
            if (value != null && value.isNumber() && !value.isNull()) {
                sum += value.asDouble();
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static RangeOfDay dayRangeInZone(ZoneId zone, Instant at) {
        var localDate = at.atZone(zone).toLocalDate();
        var start = localDate.atStartOfDay(zone).toInstant();
        var end = localDate.plusDays(1).atStartOfDay(zone).toInstant();
        return new RangeOfDay(start, end);
    }

    private record RangeOfDay(Instant start, Instant end) { }
}
