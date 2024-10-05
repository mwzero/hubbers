package com.ui.apps.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileNameHashGenerator {

    public static String generateSafeFileName(String input) {
        // Genera un hash SHA-256 della stringa di input
        String hash = generateSHA256Hash(input);
        
        // Rimuovi eventuali caratteri non validi per i nomi di file (anche se l'hash sarà sicuro)
        String safeFileName = hash.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        return safeFileName;
    }

    private static String generateSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Converti l'array di byte in una stringa esadecimale
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Errore durante la generazione dell'hash SHA-256", e);
        }
    }

    public static void main(String[] args) {
        // Esempio di stringa
        String inputString = "Esempio_di_stringa_per_il_file_name";
        
        // Genera un nome di file sicuro
        String safeFileName = generateSafeFileName(inputString);
        
        System.out.println("Nome file sicuro: " + safeFileName);
    }
}

