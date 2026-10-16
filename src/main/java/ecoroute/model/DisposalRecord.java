package ecoroute.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DisposalRecord {
    private Long disposalId;
    private Long routeId;
    private Long facilityId;
    private LocalDateTime disposalDate;
    private BigDecimal grossWeightKg;
    private BigDecimal tareWeightKg;
    /** Computed by MySQL GENERATED column (gross - tare); may be null until refreshed from DB. */
    private BigDecimal netDisposedWeightKg;
    private String receiptNumber;
    private String status;
    private String notes;

    public DisposalRecord() {}

    public DisposalRecord(Long disposalId, Long routeId, Long facilityId, LocalDateTime disposalDate,
                          BigDecimal grossWeightKg, BigDecimal tareWeightKg, BigDecimal netDisposedWeightKg,
                          String receiptNumber, String status, String notes) {
        this.disposalId = disposalId;
        this.routeId = routeId;
        this.facilityId = facilityId;
        this.disposalDate = disposalDate;
        this.grossWeightKg = grossWeightKg;
        this.tareWeightKg = tareWeightKg;
        this.netDisposedWeightKg = netDisposedWeightKg;
        this.receiptNumber = receiptNumber;
        this.status = status;
        this.notes = notes;
    }

    public Long getDisposalId() { return disposalId; }
    public void setDisposalId(Long disposalId) { this.disposalId = disposalId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public LocalDateTime getDisposalDate() { return disposalDate; }
    public void setDisposalDate(LocalDateTime d) { this.disposalDate = d; }
    public BigDecimal getGrossWeightKg() { return grossWeightKg; }
    public void setGrossWeightKg(BigDecimal v) { this.grossWeightKg = v; }
    public BigDecimal getTareWeightKg() { return tareWeightKg; }
    public void setTareWeightKg(BigDecimal v) { this.tareWeightKg = v; }
    public BigDecimal getNetDisposedWeightKg() { return netDisposedWeightKg; }
    public void setNetDisposedWeightKg(BigDecimal v) { this.netDisposedWeightKg = v; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override public String toString() { return "DisposalRecord{" + disposalId + ", route=" + routeId + "}"; }
}
