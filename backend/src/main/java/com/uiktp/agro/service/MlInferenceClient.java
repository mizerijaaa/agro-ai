package com.uiktp.agro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class MlInferenceClient {
    private static final Logger log = LoggerFactory.getLogger(MlInferenceClient.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public MlInferenceClient(
            ObjectMapper objectMapper,
            @Value("${agro.ml.base-url:http://127.0.0.1:8090}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public JsonNode recommend(Parcel parcel, ClimateData climate, List<String> candidateCrops) {
        try {
            var payload = objectMapper.createObjectNode();
            var p = payload.putObject("parcel");
            p.put("latitude", parcel.getLatitude());
            p.put("longitude", parcel.getLongitude());
            if (parcel.getSoilType() != null) p.put("soilType", parcel.getSoilType());

            var c = payload.putObject("climate");
            // Use the existing climate aggregates we have (forecast-derived). This is a starting point.
            c.put("tmean_c_year", climate.getTemperature());
            c.put("precip_mm_year", climate.getPrecipitation() * 365.0 / 7.0);
            c.put("et0_mm_year", climate.getEvapotranspiration() * 365.0);
            c.put("water_balance_mm_year", (climate.getPrecipitation() - climate.getEvapotranspiration()) * 365.0);

            var arr = payload.putArray("candidateCrops");
            for (String id : candidateCrops) arr.add(id);

            byte[] body = objectMapper.writeValueAsBytes(payload);
            log.info("ML recommend -> {} /recommend candidates={} lat={} lon={}",
                    baseUrl, candidateCrops, parcel.getLatitude(), parcel.getLongitude());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/recommend"))
                    .timeout(Duration.ofSeconds(15))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("ML service error status={} body={}", resp.statusCode(), resp.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service error: " + resp.statusCode() + " " + resp.body());
            }
            log.info("ML recommend <- status={} bytes={}", resp.statusCode(), resp.body() != null ? resp.body().length() : 0);
            return objectMapper.readTree(resp.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("ML service call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service call failed: " + e.getMessage(), e);
        }
    }
}

