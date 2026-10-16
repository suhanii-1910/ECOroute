package ecoroute.model;

public class Hospital {
    private Long generatorId;
    private String licenseNumber;
    private String biomedicalAuthNumber;

    public Hospital() {}

    public Hospital(Long generatorId, String licenseNumber, String biomedicalAuthNumber) {
        this.generatorId = generatorId;
        this.licenseNumber = licenseNumber;
        this.biomedicalAuthNumber = biomedicalAuthNumber;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getBiomedicalAuthNumber() { return biomedicalAuthNumber; }
    public void setBiomedicalAuthNumber(String biomedicalAuthNumber) { this.biomedicalAuthNumber = biomedicalAuthNumber; }

    @Override public String toString() { return "Hospital{" + generatorId + ", " + licenseNumber + "}"; }
}
