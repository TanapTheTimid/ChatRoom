import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
public class ChatClientPanel extends JPanel
{
    //info for the client
    private String nickname;
    private String serverIp;
    private int serverPort;
    private String password;
    //window components that needs accesibility
    private JTextArea chatText;
    private JScrollPane scroller;
    private JTextField input;
    private JTextArea onlines;
    //socket for communicating with server
    private Socket socket;
    //output stream for sending message to server
    private ObjectOutputStream outStream;
    private ObjectInputStream inStream;
    //authentication
    private boolean authenticated = false;
    private byte[] salt;
    private byte[] networkWideSalt;
    public ChatClientPanel(String ip, int port, String name, String pass){
        super();
        serverIp = ip;
        serverPort = port;
        nickname = name;
        password = pass;
    }

    public void enterChatRoom(){
        createChatPanel();
        connectToServer();
    }

    //sets up the window
    private void createChatPanel(){
        setSize(600,400);
        setLayout(new BorderLayout());

        chatText = new JTextArea();
        chatText.setEditable(false);
        scroller = new JScrollPane(chatText, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setPreferredSize(new Dimension(500,340));

        input = new JTextField();
        input.setEditable(false);
        input.addActionListener((ActionEvent ev) -> {
                try{
                    String intxt = input.getText();
                    if(!authenticated){
                        password = intxt;
                        outStream.writeObject(Hash.hash(intxt,salt));
                    }
                    else if(!intxt.replaceAll("\\s","").isEmpty()){
                        String s = new String(nickname+": "+ intxt);
                        appendText(s);
                        
                        LinkedList<Character> charList = new LinkedList<>();

                        for(char c: s.toCharArray()){
                            charList.add(c);
                        }

                        int x = 0;
                        int y = 0;
                        while(y < charList.size()){
                            if(x % 60 == 59){
                                if(!(charList.get(y).charValue() == ' ')){
                                    y++;
                                    continue;
                                }else{
                                    charList.add(y, '\n');
                                }
                            }
                            x++;
                            y++;
                        }

                        String finalMsg = new String();

                        for(char c: charList){
                            finalMsg = finalMsg + c;
                        }

                        Encryption encrypt = new Encryption(password, networkWideSalt);
                        byte[] msgByte = encrypt.encrypt(finalMsg);

                        MessageHolder mh = new MessageHolder();
                        mh.msg = msgByte;
                        mh.iv = encrypt.ivBytes;

                        outStream.writeObject(mh);

                        input.setText("");
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            });

        onlines = new JTextArea();
        onlines.setEditable(false);
        JScrollPane onlineScroll  = new JScrollPane(onlines, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        onlineScroll.setPreferredSize(new Dimension(100,400));
        onlines.setText("Users:");

        add(onlineScroll,BorderLayout.WEST);
        add(scroller,BorderLayout.CENTER);
        add(input,BorderLayout.SOUTH);
    }

    //sets up in/out streams
    private void setupStreams(){
        try{
            outStream = new ObjectOutputStream(socket.getOutputStream());
            outStream.flush();
            inStream = new ObjectInputStream(socket.getInputStream());
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void closeStreams(){
        try{
            outStream.close();
            inStream.close();
            socket.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void attemptConnection(){
        while(true){
            try{
                socket = new Socket(serverIp,serverPort);
                break;
            }catch(ConnectException exx){
                //if connection unsuccessful
                try{
                    Thread.sleep(1000);
                }catch(InterruptedException ee){
                    ee.printStackTrace();
                }
                chatText.append(".");
            }catch(IOException exx){
                exx.printStackTrace();
            }
        }
        //when successful
        setupStreams();
        //send nickname
        input.setText("");
        try{
            outStream.writeObject(new String(nickname));
            salt = (byte[]) inStream.readObject();

            input.setEditable(true);

            String message;
            outStream.writeObject(Hash.hash(password,salt));
            do{
                message = (String) inStream.readObject();
                appendText(message);
            }while(message.equals(ChatRoom.WRONG_PASSWORD));

            networkWideSalt = (byte[])inStream.readObject();

            authenticated = true;
        }catch(IOException ex){
            ex.printStackTrace();
        }catch(ClassNotFoundException ex){
            System.out.println("the heck is the server sending????? (authentication phase)");
        }
    }

    public void appendText(String s){
        //checks if the scroll bar is at the bottom of the screen before appending text
        boolean maxScroll = false;
        JScrollBar vbar = scroller.getVerticalScrollBar();
        int extent = vbar.getModel().getExtent();
        if(vbar.getValue()+extent == vbar.getMaximum()){
            maxScroll = true;
        }
        //appends text
        chatText.append("\n" + s);
        //if scroll bar WAS at the btm, then move it down, else dont because the user might be looking at history
        if(maxScroll){
            chatText.setCaretPosition(chatText.getDocument().getLength()-1);
        }
    }

    //connects to server and waits for messages
    private void connectToServer(){
        //first task - try to connect to server
        FutureTask<Void> connectTask = new FutureTask<Void>(new Callable<Void>(){
                    public Void call(){
                        chatText.append("Trying to connect...");
                        //try to connect to server until successful
                        attemptConnection();
                        //task for continually receiving messages
                        FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>(){

                                    public Void call(){
                                        //loops forever to receive message from client
                                        while(true){
                                            try{
                                                //when receive a message decide what to do
                                                Object obj = inStream.readObject();
                                                if(obj instanceof OnlineUsers){
                                                    onlines.setText(((OnlineUsers) obj).get());
                                                }else{
                                                    //appends message to the chat window
                                                    MessageHolder s = (MessageHolder) obj;
                                                    Encryption enc = new Encryption(password, networkWideSalt);
                                                    String decryptedText = enc.decrypt(s.msg,s.iv );
                                                    appendText(decryptedText);
                                                }
                                            }catch(ClassNotFoundException e){
                                                //idk what the heck you sent
                                                e.printStackTrace();
                                            }catch(IOException e){
                                                //if connection lost, try to reconnect
                                                input.setEditable(false);
                                                appendText("Connection Lost.\nAttempting to Reconnect...");
                                                closeStreams();
                                                //try to reconnect
                                                attemptConnection();
                                                appendText("Reconnected!");
                                            }
                                        }
                                    }
                                });
                        Thread thread = new Thread(task);
                        thread.setDaemon(true);
                        thread.start();

                        return null;
                    }
                });
        Thread connectThread = new Thread(connectTask);
        connectThread.setDaemon(true);
        connectThread.start();
    }
}
