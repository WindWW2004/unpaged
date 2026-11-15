package com.fixmyride.servicerequest.web;

import com.fixmyride.servicerequest.domain.OilType;
import com.fixmyride.servicerequest.domain.RepairComplexity;
import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.validation.ValidServiceRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ValidServiceRequest
public class ServiceRequestForm {
    @NotBlank
    @Size(max = 80)
    private String customerName;

    @NotBlank
    @Email
    @Size(max = 120)
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9 ()-]{10,20}$", message = "Use 10-20 digits and common phone punctuation.")
    private String phone;

    @NotBlank
    @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "VIN must contain 17 valid characters (I, O, and Q are excluded).")
    private String vin;

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9 -]{2,10}$", message = "Use 2-10 letters, digits, spaces, or hyphens.")
    private String licensePlate;

    @NotBlank
    @Size(max = 40)
    private String make;

    @NotBlank
    @Size(max = 40)
    private String model;

    @NotNull
    @Min(1980)
    @Max(2100)
    private Integer modelYear;

    @NotNull
    private ServiceType serviceType;

    private Long mechanicId;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime scheduledFor;

    private OilType oilType = OilType.FULL_SYNTHETIC;

    @Min(3)
    @Max(16)
    private Integer numberOfQuarts = 5;

    @DecimalMin(value = "0.00")
    private BigDecimal filterCost = new BigDecimal("12.00");

    @Min(1)
    @Max(8)
    private Integer numberOfTires = 4;

    private boolean rotation;
    private boolean balancing;
    private boolean alignment;

    @DecimalMin(value = "0.25")
    private BigDecimal diagnosticHours = new BigDecimal("1.00");

    @DecimalMin(value = "1.00")
    private BigDecimal laborRate = new BigDecimal("125.00");

    @DecimalMin(value = "0.00")
    private BigDecimal partsCost = BigDecimal.ZERO;

    private RepairComplexity complexity = RepairComplexity.MEDIUM;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin == null ? null : vin.toUpperCase(); }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate == null ? null : licensePlate.toUpperCase(); }
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getModelYear() { return modelYear; }
    public void setModelYear(Integer modelYear) { this.modelYear = modelYear; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public Long getMechanicId() { return mechanicId; }
    public void setMechanicId(Long mechanicId) { this.mechanicId = mechanicId; }
    public LocalDateTime getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(LocalDateTime scheduledFor) { this.scheduledFor = scheduledFor; }
    public OilType getOilType() { return oilType; }
    public void setOilType(OilType oilType) { this.oilType = oilType; }
    public Integer getNumberOfQuarts() { return numberOfQuarts; }
    public void setNumberOfQuarts(Integer numberOfQuarts) { this.numberOfQuarts = numberOfQuarts; }
    public BigDecimal getFilterCost() { return filterCost; }
    public void setFilterCost(BigDecimal filterCost) { this.filterCost = filterCost; }
    public Integer getNumberOfTires() { return numberOfTires; }
    public void setNumberOfTires(Integer numberOfTires) { this.numberOfTires = numberOfTires; }
    public boolean isRotation() { return rotation; }
    public void setRotation(boolean rotation) { this.rotation = rotation; }
    public boolean isBalancing() { return balancing; }
    public void setBalancing(boolean balancing) { this.balancing = balancing; }
    public boolean isAlignment() { return alignment; }
    public void setAlignment(boolean alignment) { this.alignment = alignment; }
    public BigDecimal getDiagnosticHours() { return diagnosticHours; }
    public void setDiagnosticHours(BigDecimal diagnosticHours) { this.diagnosticHours = diagnosticHours; }
    public BigDecimal getLaborRate() { return laborRate; }
    public void setLaborRate(BigDecimal laborRate) { this.laborRate = laborRate; }
    public BigDecimal getPartsCost() { return partsCost; }
    public void setPartsCost(BigDecimal partsCost) { this.partsCost = partsCost; }
    public RepairComplexity getComplexity() { return complexity; }
    public void setComplexity(RepairComplexity complexity) { this.complexity = complexity; }
}
