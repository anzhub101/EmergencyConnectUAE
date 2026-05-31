package com.emergencyconnectuae;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbTest {
    public static void main(String[] args) {
        String[] urls = {
            "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require",
            "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require",
            "jdbc:postgresql://db.jdevzqbzbxnhqnynrmgg.supabase.co:5432/postgres?sslmode=require",
            "jdbc:postgresql://db.jdevzqbzbxnhqnynrmgg.supabase.co:6543/postgres?sslmode=require",
            "jdbc:postgresql://jdevzqbzbxnhqnynrmgg.pooler.supabase.com:5432/postgres?sslmode=require",
            "jdbc:postgresql://jdevzqbzbxnhqnynrmgg.pooler.supabase.com:6543/postgres?sslmode=require"
        };
        String username = "postgres.jdevzqbzbxnhqnynrmgg";
        String[] passwords = {"EmergencyConnectUAE", "Demo1234!", "<EmergencyConnectUAE>", "postgres"};

        for (String url : urls) {
            for (String password : passwords) {
                System.out.println("Testing connection to: " + url + " as " + username + " with password: " + password);
                try (Connection conn = DriverManager.getConnection(url, username, password);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        System.out.println("SUCCESS: Connected to " + url + " with password: " + password);
                    }
                } catch (Exception e) {
                    System.out.println("FAILED: " + url + " with password: " + password + " - " + e.getMessage());
                }
            }

            // Also test with username plain "postgres" just in case
            String altUsername = "postgres";
            for (String password : passwords) {
                System.out.println("Testing connection to: " + url + " as " + altUsername + " with password: " + password);
                try (Connection conn = DriverManager.getConnection(url, altUsername, password);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        System.out.println("SUCCESS: Connected to " + url + " as " + altUsername + " with password: " + password);
                    }
                } catch (Exception e) {
                    System.out.println("FAILED: " + url + " as " + altUsername + " with password: " + password + " - " + e.getMessage());
                }
            }
            System.out.println("----------------------------------------");
        }
    }
}
