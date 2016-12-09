import java.io.Serializable;
public class OnlineUsers implements Serializable{
    String onlineUsers;
    public OnlineUsers(String s){
        onlineUsers = s;
    }
    public String get(){
        return onlineUsers;
    }
    public void set(String s){
        onlineUsers = s;
    }
    public void append(String s){
        onlineUsers += s;
    }
}
