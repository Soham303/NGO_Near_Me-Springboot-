package com.foodrescue.service;

import com.foodrescue.entity.CertificateEntity;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.CertificateRepository;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final HotelRepository hotelRepository;
    private final ListingRepository listingRepository;
    private final PickupRepository pickupRepository;

    @Transactional(readOnly = true)
    public List<CertificateEntity> getCertificatesForHotel(UUID hotelId) {
        return certificateRepository.findByHotelIdOrderByMonthDesc(hotelId);
    }

    @Transactional(readOnly = true)
    public Optional<CertificateEntity> getCertificateById(UUID certId) {
        return certificateRepository.findById(certId);
    }

    @Transactional
    public CertificateEntity generateMonthlyCertificate(UUID hotelId, LocalDate month) {
        LocalDate startOfMonth = month.withDayOfMonth(1);
        LocalDate endOfMonth = month.withDayOfMonth(month.lengthOfMonth());

        HotelEntity hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new IllegalArgumentException("Hotel not found with ID: " + hotelId));

        // Find all listings for this hotel
        List<UUID> listingIds = listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId)
                .stream().map(ListingEntity::getId).toList();

        // Find completed, non-voided pickups within the target month
        List<PickupEntity> completedPickups = pickupRepository.findAll().stream()
                .filter(p -> listingIds.contains(p.getListingId()))
                .filter(p -> p.getStatus() == PickupStatus.COMPLETED && !Boolean.TRUE.equals(p.getVoided()))
                .filter(p -> {
                    if (p.getCompletedAt() == null) return false;
                    LocalDate completedDate = p.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    return !completedDate.isBefore(startOfMonth) && !completedDate.isAfter(endOfMonth);
                })
                .toList();

        int totalMeals = completedPickups.stream()
                .mapToInt(p -> p.getPortionsReceived() != null ? p.getPortionsReceived() : 0)
                .sum();

        long distinctNgos = completedPickups.stream()
                .map(PickupEntity::getNgoId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // 1 meal rescued avoids approx 0.5 kg CO2
        BigDecimal co2Avoided = BigDecimal.valueOf(totalMeals * 0.5)
                .setScale(2, RoundingMode.HALF_UP);

        // Check if certificate exists for this month, otherwise create new
        CertificateEntity certificate = certificateRepository.findByHotelIdAndMonth(hotelId, startOfMonth)
                .orElse(CertificateEntity.builder()
                        .hotelId(hotelId)
                        .month(startOfMonth)
                        .build());

        certificate.setMeals(totalMeals);
        certificate.setNgosServed((int) Math.max(distinctNgos, 1));
        certificate.setCo2KgAvoided(co2Avoided);
        certificate.setPdfPath(String.format("certificates/%s/%s.pdf", hotelId, startOfMonth));

        CertificateEntity saved = certificateRepository.save(certificate);
        log.info("Generated certificate for hotel {} month {}: {} meals, {} CO2 avoided",
                hotel.getName(), startOfMonth, totalMeals, co2Avoided);
        return saved;
    }

    @Transactional
    public List<CertificateEntity> generateCertificatesForAllHotels(LocalDate month) {
        List<HotelEntity> hotels = hotelRepository.findAll();
        List<CertificateEntity> generated = new ArrayList<>();
        for (HotelEntity hotel : hotels) {
            try {
                generated.add(generateMonthlyCertificate(hotel.getId(), month));
            } catch (Exception e) {
                log.error("Failed to generate certificate for hotel {}", hotel.getId(), e);
            }
        }
        return generated;
    }

    public String generateHtmlCertificate(CertificateEntity cert) {
        HotelEntity hotel = hotelRepository.findById(cert.getHotelId()).orElse(null);
        String hotelName = hotel != null ? hotel.getName() : "Partner Hotel";
        String monthFormatted = cert.getMonth().format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>FoodRescue Impact Certificate - %s</title>
              <style>
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                  background: #f8fafc;
                  display: flex;
                  justify-content: center;
                  padding: 40px;
                }
                .cert-container {
                  background: white;
                  width: 800px;
                  border: 12px solid #2E7D32;
                  border-radius: 16px;
                  padding: 48px;
                  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
                  text-align: center;
                  position: relative;
                }
                .badge-header {
                  color: #2E7D32;
                  font-weight: 800;
                  letter-spacing: 2px;
                  text-transform: uppercase;
                  font-size: 14px;
                  margin-bottom: 8px;
                }
                h1 {
                  font-size: 32px;
                  color: #1F2933;
                  margin: 0 0 16px;
                }
                .hotel-name {
                  font-size: 28px;
                  font-weight: 700;
                  color: #2E7D32;
                  margin: 24px 0 8px;
                  border-bottom: 2px dashed #E4E7EC;
                  padding-bottom: 8px;
                  display: inline-block;
                }
                .period {
                  color: #667085;
                  font-size: 16px;
                  margin-bottom: 32px;
                }
                .metrics-grid {
                  display: flex;
                  justify-content: space-around;
                  margin: 32px 0;
                  background: #F8FAF7;
                  border-radius: 12px;
                  padding: 24px;
                }
                .metric-val {
                  font-size: 36px;
                  font-weight: 900;
                  color: #2E7D32;
                }
                .metric-label {
                  font-size: 12px;
                  color: #667085;
                  font-weight: 600;
                  text-transform: uppercase;
                  margin-top: 4px;
                }
                .footer {
                  margin-top: 40px;
                  font-size: 13px;
                  color: #94a3b8;
                  border-top: 1px solid #E4E7EC;
                  padding-top: 16px;
                }
              </style>
            </head>
            <body>
              <div class="cert-container">
                <div class="badge-header">Official Recognition of Food Rescue Impact</div>
                <h1>Certificate of Environmental & Community Impact</h1>
                <p>Presented in grateful recognition to</p>
                <div class="hotel-name">%s</div>
                <div class="period">For outstanding surplus food rescue during %s</div>
                
                <div class="metrics-grid">
                  <div>
                    <div class="metric-val">%,d</div>
                    <div class="metric-label">Meals Rescued</div>
                  </div>
                  <div>
                    <div class="metric-val">%d</div>
                    <div class="metric-label">NGO Partners Fed</div>
                  </div>
                  <div>
                    <div class="metric-val">%.1f kg</div>
                    <div class="metric-label">Estimated CO₂ Avoided</div>
                  </div>
                </div>

                <p style="color:#475569; font-size: 14px; max-width: 600px; margin: 0 auto;">
                  Through dedicated operational commitment to zero edible food waste, your team
                  helped divert quality cooked surplus directly to vulnerable community members.
                </p>

                <div class="footer">
                  Verified by FoodRescue Platform &bull; Certificate ID: %s &bull; Issued: %s
                </div>
              </div>
            </body>
            </html>
            """,
                hotelName,
                hotelName,
                monthFormatted,
                cert.getMeals(),
                cert.getNgosServed(),
                cert.getCo2KgAvoided().doubleValue(),
                cert.getId(),
                cert.getCreatedAt() != null ? cert.getCreatedAt().toString() : LocalDate.now().toString()
        );
    }
}
