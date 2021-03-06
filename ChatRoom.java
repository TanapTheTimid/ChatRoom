import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.Random;

public class ChatRoom
{
    public static final String WRONG_PASSWORD = "Authentication failed. Please try again...\nEnter password:";
    public static final String KICKED_MESSAGE = "You have been kicked!";
    public static final String BANNED_MESSAGE = "You are banned from this server.";
    public static final String ACCEPTED_MESSAGE = "joined";
    
    private static final String DEFAULT_PASSWORD = "PASSWORD";
    //default port of the server
    private static final int DEFAULT_PORT = 24686;
    //info used to create server or client
    private static String nickname;
    private static String serverIp;
    private static int serverPort;
    private static String password;
    //window frame
    private static JFrame frame;

    public static void main(String[] args){
        //create a window frame
        frame = new JFrame("Connect");
        frame.setSize(300,140);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //create menu
        JPanel menu = createMenu();
        frame.setContentPane(menu);

        frame.setVisible(true);
    }

    //creates the menu screen
    private static JPanel createMenu(){
        JPanel menu = new JPanel();
        menu.setLayout(new GridLayout(5,2));
        JLabel label = new JLabel("IP Address");
        //field for entering IP address of the server
        JTextField ipField = new JTextField();
        ipField.setEditable(true);
        ipField.setColumns(20);
        ipField.setText("0.0.0.0");

        JLabel label2 = new JLabel("Port");
        //field for entering the port of the server
        JTextField portField = new JTextField();
        portField.setEditable(true);
        portField.setColumns(20);
        portField.setText(DEFAULT_PORT + "");

        JLabel label3 = new JLabel("Nickname");
        //field for nickname used in the chatroom
        JTextField nameField = new JTextField();
        nameField.setEditable(true);
        nameField.setColumns(20);
        Random ran = new Random();
        String s = "";
        for(int x = 0; x < 6;x++){
            s = s + (ran.nextInt(9)+"");
        }
        nameField.setText(s);
        //field for password
        JLabel label4 = new JLabel("Password");
        //field for nickname used in the chatroom
        JTextField passField = new JTextField();
        passField.setEditable(true);
        passField.setColumns(20);
        //button for launching client
        JButton enter = new JButton("Enter Room");
        //when pressed
        enter.addActionListener((ActionEvent e) -> {
                //collect info
                serverIp = ipField.getText();
                serverPort = Integer.valueOf(portField.getText());
                nickname = nameField.getText();
                password = passField.getText();
                if(password.isEmpty()){
                    password = DEFAULT_PASSWORD;
                }
                //create a new client Panel object
                ChatClientPanel client = new ChatClientPanel(serverIp, serverPort, nickname, password);
                //sets up window
                frame.setSize(600,400);
                frame.setContentPane(client);
                frame.setTitle("Room: "+serverIp);
                //connects to server
                client.enterChatRoom();
            });
        JButton server = new JButton("Host Server");
        server.addActionListener((ActionEvent e) -> {
                //collect info
                serverIp = ipField.getText();
                serverPort = Integer.valueOf(portField.getText());
                nickname = nameField.getText();
                password = passField.getText();
                
                if(password.isEmpty()){
                    password = DEFAULT_PASSWORD;
                }
                //create a new server Panel object
                ChatServerPanel cserver = new ChatServerPanel(serverIp, serverPort, nickname, password);
                //setup window
                frame.setSize(600,400);
                frame.setContentPane(cserver);
                frame.setTitle("Server");
                //run server
                cserver.enterChatRoom();
            });

        menu.add(label);
        menu.add(ipField);
        menu.add(label2);
        menu.add(portField);
        menu.add(label3);
        menu.add(nameField);
        menu.add(label4);
        menu.add(passField);
        menu.add(enter);
        menu.add(server);

        return menu;
    }

}
