package org.littleshoot.proxy.impl;

public class BasicAuthCredentials implements Credentials {

  private final String username;
  private final String password;

  BasicAuthCredentials(String username, String password) {
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
