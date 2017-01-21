import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Hash{
    private static final String SKF_NAME = "PBKDF2WithHmacSHA512";

    public static byte[] hash(String pass, byte[] salt) {
        try{
            KeySpec spec = new PBEKeySpec(pass.toCharArray(),salt,20000,128);
            SecretKeyFactory f = SecretKeyFactory.getInstance(SKF_NAME);
            return(f.generateSecret(spec).getEncoded());
        }catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        }catch(InvalidKeySpecException e){
            e.printStackTrace();
        }
        return null;
    }
    public static byte[] getRandomSalt(){
        byte[] salt = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return salt;
    }
    public static boolean verifyPassword(String pass, byte[] salt , byte[] hash){
        return Arrays.equals(hash(pass,salt) , hash);
    }
}