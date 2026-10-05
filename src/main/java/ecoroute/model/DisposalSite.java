package ecoroute.model;

import java.math.BigDecimal;

/** Stored destination data; selection of a suitable site belongs to the caller. */
public class DisposalSite {
    private Long siteId;
    private String siteName;
    private String siteType;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal capacityKg;
    private String status;

    public DisposalSite() {}

    public DisposalSite(Long siteId, String siteName, String siteType, String address,
                        BigDecimal latitude, BigDecimal longitude, BigDecimal capacityKg, String status) {
        this.siteId = siteId;
        this.siteName = siteName;
        this.siteType = siteType;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacityKg = capacityKg;
        this.status = status;
    }

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteType() { return siteType; }
    public void setSiteType(String siteType) { this.siteType = siteType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override public String toString() { return "DisposalSite{" + siteId + ", " + siteName + "}"; }
}
