package ecoroute.model;

import java.math.BigDecimal;

public class FacilityAcceptedWaste {
    private Long facilityId;
    private Long categoryId;
    private BigDecimal processingRatePerKg;

    public FacilityAcceptedWaste() {}

    public FacilityAcceptedWaste(Long facilityId, Long categoryId, BigDecimal processingRatePerKg) {
        this.facilityId = facilityId;
        this.categoryId = categoryId;
        this.processingRatePerKg = processingRatePerKg;
    }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getProcessingRatePerKg() { return processingRatePerKg; }
    public void setProcessingRatePerKg(BigDecimal v) { this.processingRatePerKg = v; }

    @Override public String toString() { return "FacilityAcceptedWaste{" + facilityId + ", cat=" + categoryId + "}"; }
}
