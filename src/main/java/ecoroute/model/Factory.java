package ecoroute.model;

public class Factory {
    private Long generatorId;
    private String pollutionConsentNo;

    public Factory() {}

    public Factory(Long generatorId, String pollutionConsentNo) {
        this.generatorId = generatorId;
        this.pollutionConsentNo = pollutionConsentNo;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }
    public String getPollutionConsentNo() { return pollutionConsentNo; }
    public void setPollutionConsentNo(String pollutionConsentNo) { this.pollutionConsentNo = pollutionConsentNo; }

    @Override public String toString() { return "Factory{" + generatorId + ", " + pollutionConsentNo + "}"; }
}
