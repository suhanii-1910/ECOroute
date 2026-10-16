package ecoroute.model;

import java.math.BigDecimal;

public class DisposalFacility {
    private Long facilityId;
    private String facilityName;
    private String facilityType;
    private String address;
    private Double latitude;
    private Double longitude;
    private BigDecimal capacityTonnesPerDay;
    private String status;
    private String contactInfo;

    public DisposalFacility() {}

    public DisposalFacility(Long facilityId, String facilityName, String facilityType, String address,
                            Double latitude, Double longitude, BigDecimal capacityTonnesPerDay, String status, String contactInfo) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.facilityType = facilityType;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacityTonnesPerDay = capacityTonnesPerDay;
        this.status = status;
        this.contactInfo = contactInfo;
    }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public BigDecimal getCapacityTonnesPerDay() { return capacityTonnesPerDay; }
    public void setCapacityTonnesPerDay(BigDecimal v) { this.capacityTonnesPerDay = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    @Override public String toString() { return "DisposalFacility{" + facilityId + ", " + facilityName + "}"; }
}
