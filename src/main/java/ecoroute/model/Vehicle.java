package ecoroute.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class Vehicle {
    private Long vehicleId;
    private String vehicleNumber;
    private BigDecimal capacityKg;
    private String status;
    private Long assignedZoneId;
    private LocalDateTime createdDate;

    public Vehicle() {}

    public Vehicle(Long vehicleId, String vehicleNumber, BigDecimal capacityKg, String status, Long assignedZoneId, LocalDateTime createdDate) {
        this.vehicleId = vehicleId;
        this.vehicleNumber = vehicleNumber;
        this.capacityKg = capacityKg;
        this.status = status;
        this.assignedZoneId = assignedZoneId;
        this.createdDate = createdDate;
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

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    @Override
    public String toString() {
        return "Vehicle{" + vehicleId + ", " + vehicleNumber + "}";
    }
}
