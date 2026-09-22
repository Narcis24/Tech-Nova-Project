package com.neueda.app.model;

import java.time.LocalDate;
import java.util.UUID;
import com.neueda.app.enums.ClientType;

public class Client {
    private UUID clientId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String email;
    private String contactNumber;
    private ClientType clientType;

    public Client(UUID clientId, String firstName, String lastName, LocalDate dateOfBirth, 
            String email, String contactNumber, ClientType clientType) {
        
        if (clientId == null) {
            throw new IllegalArgumentException("Client ID cannot be null");
        }
        
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First Name cannot be null or empty");
        }
        
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last Name cannot be null or empty");
        }
        
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email must be in valid format");
        }
        
        if (contactNumber == null || contactNumber.isBlank()) {
            throw new IllegalArgumentException("Contact Number cannot be null or empty");
        }
        
        if (clientType == null) {
            throw new IllegalArgumentException("Client Type cannot be null");
        }
        
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.email = email;
        this.contactNumber = contactNumber;
        this.clientType = clientType;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

}
