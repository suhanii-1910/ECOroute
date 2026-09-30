package ecoroute.model;

import java.time.LocalDate;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Hospital {
    private Long generatorId;
    private String licenseNumber;
    private String biomedicalAuthNumber;
    private LocalDate authExpiryDate;

    public Hospital() {}

    public Hospital(Long generatorId, String licenseNumber, String biomedicalAuthNumber, LocalDate authExpiryDate) {
        this.generatorId = generatorId;
        this.licenseNumber = licenseNumber;
        this.biomedicalAuthNumber = biomedicalAuthNumber;
        this.authExpiryDate = authExpiryDate;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getBiomedicalAuthNumber() { return biomedicalAuthNumber; }
    public void setBiomedicalAuthNumber(String biomedicalAuthNumber) { this.biomedicalAuthNumber = biomedicalAuthNumber; }

    public LocalDate getAuthExpiryDate() { return authExpiryDate; }
    public void setAuthExpiryDate(LocalDate authExpiryDate) { this.authExpiryDate = authExpiryDate; }

    @Override
    public String toString() {
        return "Hospital{" + generatorId + ", " + licenseNumber + "}";
    }
}
