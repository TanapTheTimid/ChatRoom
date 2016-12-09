import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class ChatClientPanel extends JPanel
{
    //info for the client
    private String nickname;
    private String serverIp;
    private int serverPort;
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
    public ChatClientPanel(String ip, int port, String name){
        super();
        serverIp = ip;
        serverPort = port;
        nickname = name;
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
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setPreferredSize(new Dimension(500,340));

        input = new JTextField();
        input.setEditable(false);
        input.addActionListener((ActionEvent ev) -> {
                try{
                    String intxt = input.getText();
                    if(!intxt.replaceAll("\\s","").isEmpty()){
                        String s = new String(nickname+": "+ intxt);
                        outStream.writeObject(s);
                        input.setText("");
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            });

        onlines = new JTextArea();
        onlines.setEditable(false);
        JScrollPane onlineScroll  = new JScrollPane(onlines, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
        //write nickname to server
        try{
            outStream.writeObject(new String(nickname));
            //notify everyone of entry
            outStream.writeObject(new String(nickname + " joined the chat room!"));
        }catch(IOException ex){
            ex.printStackTrace();
        }
        //enable sending messages
        input.setEditable(true);
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
                                                    appendText(((String)obj).toString());
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
