package ecoroute.model;

import java.math.BigDecimal;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Vehicle {
    private Long vehicleId;
    private String vehicleNumber;
    private BigDecimal capacityKg;
    private String status;
    private Long assignedZoneId;

    public Vehicle() {}

    public Vehicle(Long vehicleId, String vehicleNumber, BigDecimal capacityKg, String status, Long assignedZoneId) {
        this.vehicleId = vehicleId;
        this.vehicleNumber = vehicleNumber;
        this.capacityKg = capacityKg;
        this.status = status;
        this.assignedZoneId = assignedZoneId;
    }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAssignedZoneId() { return assignedZoneId; }
    public void setAssignedZoneId(Long assignedZoneId) { this.assignedZoneId = assignedZoneId; }

    @Override
    public String toString() {
        return "Vehicle{" + vehicleId + ", " + vehicleNumber + "}";
    }
}
