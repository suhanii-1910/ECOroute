package ecoroute.model;


/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Zone {
    private Long zoneId;
    private String zoneName;
    private String description;

    public Zone() {}

    public Zone(Long zoneId, String zoneName, String description) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.description = description;
    }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "Zone{" + zoneId + ", " + zoneName + "}";
    }
}
