package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.entity.CertificateEntity;
import com.foodrescue.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<ApiResponse<List<CertificateEntity>>> getHotelCertificates(
            @PathVariable("hotelId") UUID hotelId) {
        List<CertificateEntity> certificates = certificateService.getCertificatesForHotel(hotelId);
        return ResponseEntity.ok(ApiResponse.ok(certificates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CertificateEntity>> getCertificateById(
            @PathVariable("id") UUID id) {
        return certificateService.getCertificateById(id)
                .map(cert -> ResponseEntity.ok(ApiResponse.ok(cert)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/render", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderCertificateHtml(@PathVariable("id") UUID id) {
        return certificateService.getCertificateById(id)
                .map(cert -> {
                    String html = certificateService.generateHtmlCertificate(cert);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.TEXT_HTML);
                    return ResponseEntity.ok().headers(headers).body(html);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/hotel/{hotelId}/generate")
    public ResponseEntity<ApiResponse<CertificateEntity>> generateCertificateForHotel(
            @PathVariable("hotelId") UUID hotelId,
            @RequestParam(value = "month", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        LocalDate targetMonth = month != null ? month : LocalDate.now().minusMonths(1).withDayOfMonth(1);
        CertificateEntity cert = certificateService.generateMonthlyCertificate(hotelId, targetMonth);
        return ResponseEntity.ok(ApiResponse.ok("Certificate generated successfully", cert));
    }

    @PostMapping("/admin/generate-all")
    public ResponseEntity<ApiResponse<List<CertificateEntity>>> generateAllCertificates(
            @RequestParam(value = "month", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        LocalDate targetMonth = month != null ? month : LocalDate.now().minusMonths(1).withDayOfMonth(1);
        List<CertificateEntity> certs = certificateService.generateCertificatesForAllHotels(targetMonth);
        return ResponseEntity.ok(ApiResponse.ok("Generated " + certs.size() + " certificates", certs));
    }
}
