package ecoroute.model;

import java.time.LocalDate;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Factory {
    private Long generatorId;
    private String industryType;
    private String pollutionConsentNo;
    private LocalDate consentExpiryDate;

    public Factory() {}

    public Factory(Long generatorId, String industryType, String pollutionConsentNo, LocalDate consentExpiryDate) {
        this.generatorId = generatorId;
        this.industryType = industryType;
        this.pollutionConsentNo = pollutionConsentNo;
        this.consentExpiryDate = consentExpiryDate;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public String getIndustryType() { return industryType; }
    public void setIndustryType(String industryType) { this.industryType = industryType; }

    public String getPollutionConsentNo() { return pollutionConsentNo; }
    public void setPollutionConsentNo(String pollutionConsentNo) { this.pollutionConsentNo = pollutionConsentNo; }

    public LocalDate getConsentExpiryDate() { return consentExpiryDate; }
    public void setConsentExpiryDate(LocalDate consentExpiryDate) { this.consentExpiryDate = consentExpiryDate; }

    @Override
    public String toString() {
        return "Factory{" + generatorId + ", " + industryType + "}";
    }
}
