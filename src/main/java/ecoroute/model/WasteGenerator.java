package ecoroute.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class WasteGenerator {
    private Long generatorId;
    private String name;
    private String generatorType;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Long zoneId;
    private boolean active;
    private LocalDateTime createdDate;

    public WasteGenerator() {}

    public WasteGenerator(Long generatorId, String name, String generatorType, String contactPerson, String phone, String email, String address, BigDecimal latitude, BigDecimal longitude, Long zoneId, boolean active, LocalDateTime createdDate) {
        this.generatorId = generatorId;
        this.name = name;
        this.generatorType = generatorType;
        this.contactPerson = contactPerson;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
        this.active = active;
        this.createdDate = createdDate;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGeneratorType() { return generatorType; }
    public void setGeneratorType(String generatorType) { this.generatorType = generatorType; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    @Override
    public String toString() {
        return "WasteGenerator{" + generatorId + ", " + name + "}";
    }
}
