package com.foodrescue.dto;

import com.foodrescue.entity.CertificateEntity;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ScoreSnapshotEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HotelDashboardResponse {
    private HotelEntity hotel;
    private ScoreSnapshotEntity latestScore;
    private long pickupsLast60Days;
    private int portionsRescuedLast60Days;
    private double claimRateLast60Days;
    private List<ListingResponse> recentListings;
}
