package ecoroute.model;

public class HousingSociety {
    private Long generatorId;
    private String registrationNumber;

    public HousingSociety() {}

    public HousingSociety(Long generatorId, String registrationNumber) {
        this.generatorId = generatorId;
        this.registrationNumber = registrationNumber;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    @Override public String toString() { return "HousingSociety{" + generatorId + ", " + registrationNumber + "}"; }
}
