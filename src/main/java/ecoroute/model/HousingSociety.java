package ecoroute.model;


/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class HousingSociety {
    private Long generatorId;
    private String registrationNumber;
    private Integer numberOfFlats;

    public HousingSociety() {}

    public HousingSociety(Long generatorId, String registrationNumber, Integer numberOfFlats) {
        this.generatorId = generatorId;
        this.registrationNumber = registrationNumber;
        this.numberOfFlats = numberOfFlats;
    }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public Integer getNumberOfFlats() { return numberOfFlats; }
    public void setNumberOfFlats(Integer numberOfFlats) { this.numberOfFlats = numberOfFlats; }

    @Override
    public String toString() {
        return "HousingSociety{" + generatorId + ", " + registrationNumber + "}";
    }
}
