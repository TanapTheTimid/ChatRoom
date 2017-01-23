import javax.crypto.Cipher;
import javax.crypto.spec.*;
import javax.crypto.*;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;
import java.io.*;

public class Encryption{
    private Cipher cipher;
    private SecretKeySpec keySpec;
    private IvParameterSpec ivSpec;
    private SecretKey key;

    public byte[] ivBytes;
    public int ivLength;

    public Encryption(String password, byte[] salt){
        try{
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
            SecretKey tmp = factory.generateSecret(spec);
            key = new SecretKeySpec(tmp.getEncoded(), "AES");

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecureRandom random = new SecureRandom();
            //get random iv bytes
            ivLength = cipher.getBlockSize();
            ivBytes = new byte[ivLength];
            random.nextBytes(ivBytes);
            //convert string to byte
            //byte[] keyBytes = key.getBytes("UTF-8");
            //wrap key data in key/IV specs to pass to cipher
            //keySpec = new SecretKeySpec(keyBytes, "AES");
            ivSpec = new IvParameterSpec(ivBytes);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public byte[] encrypt(String input){
        try{
            byte[] inputBytes = input.getBytes("UTF-8");
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

            byte[] encryptedBytes = cipher.doFinal(inputBytes);
            return encryptedBytes;
        }catch(Exception e){
            e.printStackTrace();
        }

        return new byte[1];
    }

    public String decrypt(byte[] input, byte[] ivb){
        try{
            IvParameterSpec ivs = new IvParameterSpec(ivb);
            cipher.init(Cipher.DECRYPT_MODE, key, ivs);

            byte[] plainBytes = cipher.doFinal(input);

            String plainText = new String(plainBytes, "UTF-8");

            return plainText;
        }catch(Exception e){
            e.printStackTrace();
        }

        return "";
    }
}