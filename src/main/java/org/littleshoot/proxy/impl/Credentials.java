package org.littleshoot.proxy.impl;

public class Credentials {

  private final String username;
  private final String password;

  Credentials(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
