package com.treishvaam.financeapi.dto;

public class ContactInfoDTO {
  private String email;
  private String phone;
  private String address;

  public ContactInfoDTO(String email, String phone, String address) {
    this.email = email;
    this.phone = phone;
    this.address = address;
  }

  // Getters
  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddress() {
    return address;
  }

  // Setters
  public void setEmail(String email) {
    this.email = email;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public void setAddress(String address) {
    this.address = address;
  }
}
