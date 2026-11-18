package com.foodrescue.dto;

import com.foodrescue.entity.CertificateEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HotelImpactResponse {
    private int totalMealsRescued;
    private double co2KgAvoided;
    private long ngosServed;
    private int streakWeeks;
    private String tierBadge;
    private List<CertificateEntity> certificates;
}
