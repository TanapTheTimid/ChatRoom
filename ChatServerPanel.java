import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.*;
public class ChatServerPanel extends JPanel
{
    //info for server
    private String nickname;
    private String serverIp;
    private int serverPort;
    //gui components that can be updated
    private JTextArea chatText;
    private JScrollPane scroller;
    private JTextArea onlines;
    //serversocket for accepting connection
    private ServerSocket serverSocket;
    //list of client handlers
    private List<ClientService> clientServices = new ArrayList<>();
    public ChatServerPanel(String ip, int port, String name){
        super();
        serverIp = ip;
        serverPort = port;
        nickname = name;
    }

    public void enterChatRoom(){
        createChatPanel();
        acceptConnections();
    }

    private void createChatPanel(){
        //setup the panel
        setSize(600,400);
        setLayout(new BorderLayout());

        //chat text area for showing the chat content that can scroll
        chatText = new JTextArea();
        chatText.setEditable(false);
        scroller = new JScrollPane(chatText, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setPreferredSize(new Dimension(500,340));

        //input field for sending messages
        JTextField input = new JTextField();
        input.setEditable(true);
        input.addActionListener((ActionEvent ev) -> {
                String intxt = input.getText();
                if(!intxt.replace("\\s","").isEmpty()){
                    String s = new String(nickname+": " + intxt);
                    sendMessage(s);
                    input.setText("");
                }
            });

        //text area for showing online users
        onlines = new JTextArea();
        onlines.setEditable(false);
        JScrollPane onlineScroll  = new JScrollPane(onlines, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        onlineScroll.setPreferredSize(new Dimension(100,400));
        onlines.setText("Users:\n"+nickname);
        //adds components
        add(onlineScroll,BorderLayout.WEST);
        add(scroller,BorderLayout.CENTER);
        add(input,BorderLayout.SOUTH);
    }

    public void sendMessage(String s){
        //checks if the scroll bar is at the bottom of the screen before appending text
        boolean maxScroll = false;
        JScrollBar vbar = scroller.getVerticalScrollBar();
        int extent = vbar.getModel().getExtent();
        if(vbar.getValue()+extent == vbar.getMaximum()){
            maxScroll = true;
        }

        //appends message to the chat window and sends it to client
        chatText.append("\n" + s);
        clientServices.forEach((clientService) -> {
                clientService.sendObject(s);
            });
        //if scroll bar WAS at the btm, then move it down, else dont because the user might be looking at history
        if(maxScroll){
            chatText.setCaretPosition(chatText.getDocument().getLength()-1);
        }
    }

    private void acceptConnections(){
        //create a new thread for waiting for new clients to connect
        chatText.append("Clients can now connect...");
        FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>(){
                    public Void call(){
                        chatText.append("\nServer openned at port: " + serverPort);
                        //start the server
                        try{
                            //open a new server socket at the specified port
                            serverSocket = new ServerSocket(serverPort);
                            serverSocket.setSoTimeout(0);//no timeout
                            while(true){
                                //wait for client to connect
                                Socket socket = serverSocket.accept();
                                //create new ClientService to handle each client in a seperate thread
                                ClientService service = new ClientService(socket);
                                //start the service to receive messages from client
                                service.start();
                                //add the service to list of client services-- used when sending messages
                                clientServices.add(service);
                            }
                        }catch(IOException ex){//if io exception happens
                            ex.printStackTrace();
                        }
                        return null;
                    }
                });
        //create a new thread to execute the task
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    //client service for holding information and functions relating to communication with the client
    //create a task to receive messages
    //handles sending messages on call
    public class ClientService{
        //socket for connecting to server
        private Socket socket;
        //client info
        private String ip;
        //reference to this client service for removing itself from the list of clientServices
        private ClientService clientService;
        //Atomic Reference - referencing the output stream and input stream
        private AtomicReference<ObjectOutputStream> out = new AtomicReference<ObjectOutputStream>();
        private AtomicReference<ObjectInputStream> in = new AtomicReference<ObjectInputStream>();
        //nickname of the client
        private String nickname;

        public ClientService(Socket socket){
            super();
            this.socket = socket;
            ip = socket.getInetAddress().getHostAddress();
            clientService = this;
        }

        //start this client and runs
        public void start(){
            setupStreams();
            Thread thread = new Thread(createTask());
            thread.setDaemon(true);
            thread.start();
        }

        //set up the streams for input and output from/to the client
        public void setupStreams(){
            try{
                out.set(new ObjectOutputStream(socket.getOutputStream()));
                out.get().flush();
                in.set(new ObjectInputStream(socket.getInputStream()));
            }catch(IOException io){
                io.printStackTrace();
            }
        }

        //sends an object to the client
        public void sendObject(Object ob){
            try{
                out.get().writeObject(ob);
            }catch(IOException ex){
                ex.printStackTrace();
            }
        }

        public void updateOnlines(){
            OnlineUsers on = new OnlineUsers(onlines.getText());
            sendObject(on);
        }

        //creates and return a task that receives and handles messages from the client
        private FutureTask<Void> createTask() {
            FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>(){
                        public Void call(){
                            try{
                                //first thing when connected, wait for client to send its nickname
                                nickname = in.get().readObject().toString();
                                onlines.append("\n"+nickname);//add to list of onlines
                                updateOnlines();
                            }catch(ClassNotFoundException e){
                                e.printStackTrace();
                            }catch(IOException e){
                                e.printStackTrace();
                            }
                            while(true){
                                try{
                                    //wait for message then call sendMessage(String s);
                                    sendMessage(in.get().readObject().toString());
                                }catch(ClassNotFoundException ex){
                                    ex.printStackTrace();
                                }catch(IOException ex){
                                    //if client disconnects
                                    sendMessage(nickname + " left the chat room.");
                                    //close streams and socket
                                    try{
                                        in.get().close();
                                        out.get().close();
                                        socket.close();
                                    }catch(IOException exx){
                                        exx.printStackTrace();
                                    }
                                    //remove the name from online
                                    String name = onlines.getText();
                                    int index = name.indexOf(nickname);
                                    char[] namearr = name.toCharArray();
                                    for(int x = index-1; x < namearr.length;x++){
                                        namearr[x] = 0;
                                    }
                                    onlines.setText(new String(namearr));
                                    updateOnlines();
                                    //remove this clientservice from the list
                                    clientServices.remove(clientService);
                                    //quit this task
                                    return null;
                                }
                            }
                        }
                    });
            return task;
        }
    }
}
