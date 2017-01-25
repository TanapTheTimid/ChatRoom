import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.*;
import java.util.Arrays;
import java.util.LinkedList;
public class ChatServerPanel extends JPanel
{
    private final String[] OPTIONS_CLIENT = {"Cancel", "Kick", "Ban IP"};
    private final String[] OPTIONS_IP = {"Cancel", "Unban IP"};

    //info for server
    private byte[] globalPasswordSalt;
    private String nickname;
    private String serverIp;
    private int serverPort;
    private String password;
    //gui components that can be updated
    private JTextArea chatText;
    private JScrollPane scroller;
    private JList onlines;
    private DefaultListModel onlinesListModel;
    private DefaultListModel bannedListModel;
    //serversocket for accepting connection
    private ServerSocket serverSocket;
    //list of client handlers
    private List<ClientService> clientServices = new ArrayList<>();
    public ChatServerPanel(String ip, int port, String name, String pass){
        super();
        serverIp = ip;
        serverPort = port;
        nickname = name;
        password = pass;
        globalPasswordSalt = Hash.getRandomSalt();
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
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setPreferredSize(new Dimension(400,340));

        //input field for sending messages
        JTextField input = new JTextField();
        input.setEditable(true);
        input.addActionListener((ActionEvent ev) -> {
                String intxt = input.getText();
                if(!intxt.replace("\\s","").isEmpty()){
                    String s = new String(nickname+": " + intxt);

                    sendToSelf(s);

                    sendMessage(encrypt(s), false, null);
                    input.setText("");
                }
            });

        //text area for showing online users
        onlinesListModel = new DefaultListModel();
        onlinesListModel.addElement("~OPTIONS~");
        onlinesListModel.addElement(nickname);

        onlines = new JList(onlinesListModel);
        onlines.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        onlines.setLayoutOrientation(JList.VERTICAL);

        onlines.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){
                    if(e.getClickCount() == 2){
                        int index = onlines.locationToIndex(e.getPoint());
                        if(index > 1){
                            int selectedValue = JOptionPane.showOptionDialog(null, onlinesListModel.get(index),"Options"
                                , JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE
                                , null, OPTIONS_CLIENT, OPTIONS_CLIENT[0]);

                            ClientService cs = clientServices.get(index-2);
                            
                            if(selectedValue == 1){
                                cs.kick();
                                sendToSelf(cs.getNickname()+" has been kicked!");
                                sendMessage(encrypt(cs.getNickname()+" has been kicked!"), false, null);
                            }else if(selectedValue == 2){
                                bannedListModel.addElement(cs.getIpAddress());
                                cs.kick();
                                sendToSelf(cs.getNickname()+" has been banned!");
                                sendMessage(encrypt(cs.getNickname()+" has been banned!"), false, null);
                            }
                        }else if(index == 1){
                            JOptionPane.showMessageDialog(null, "Server");
                        }
                    }
                }
            });

        JScrollPane onlineScroll  = new JScrollPane(onlines, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        onlineScroll.setPreferredSize(new Dimension(100,400));

        //banned list
        //text area for showing online users
        bannedListModel = new DefaultListModel();
        bannedListModel.addElement("Banned IP(s):");

        JList banned = new JList(bannedListModel);
        banned.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        banned.setLayoutOrientation(JList.VERTICAL);
        
        banned.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){
                    if(e.getClickCount() == 2){
                        int index = banned.locationToIndex(e.getPoint());
                        if(index > 0){
                            int selectedValue = JOptionPane.showOptionDialog(null, bannedListModel.get(index),"Options"
                                , JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE
                                , null, OPTIONS_IP, OPTIONS_IP[0]);

                            if(selectedValue == 1){
                                bannedListModel.remove(index);
                            }
                        }
                    }
                }
            });

        JScrollPane bannedPane  = new JScrollPane(banned, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        bannedPane.setPreferredSize(new Dimension(100,400));
        bannedPane.setLocation(100,0);
        
        //adds components
        add(onlineScroll,BorderLayout.WEST);
        add(bannedPane, BorderLayout.EAST);
        add(scroller,BorderLayout.CENTER);
        add(input,BorderLayout.SOUTH);
    }

    public MessageHolder encrypt(String s){
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

        Encryption encrypt = new Encryption(password, globalPasswordSalt);
        byte[] msgByte = encrypt.encrypt(finalMsg);

        MessageHolder mh = new MessageHolder();
        mh.msg = msgByte;
        mh.iv = encrypt.ivBytes;

        return mh;
    }

    public void sendToSelf(String decryptedText){
        //appends message to the chat window
        chatText.append("\n" + decryptedText);

        //checks if the scroll bar is at the bottom of the screen before appending text
        boolean maxScroll = false;
        JScrollBar vbar = scroller.getVerticalScrollBar();
        int extent = vbar.getModel().getExtent();
        if(vbar.getValue()+extent == vbar.getMaximum()){
            maxScroll = true;
        }

        //if scroll bar WAS at the btm, then move it down, else dont because the user might be looking at history
        if(maxScroll){
            chatText.setCaretPosition(chatText.getDocument().getLength()-1);
        }
    }

    public void sendMessage(MessageHolder s, boolean toSelf, ClientService originator){
        // sends it to client
        clientServices.forEach((clientService) -> {
                if(!clientService.equals(originator))
                    clientService.sendObject(s);
            });

        if(toSelf){
            Encryption enc = new Encryption(password, globalPasswordSalt);
            String decryptedText = enc.decrypt(s.msg,s.iv );
            sendToSelf(decryptedText);
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
                            }
                        }catch(IOException ex){//if io exception happens
                            ex.printStackTrace();
                        }finally{
                            try{
                                serverSocket.close();
                            }catch(IOException e){
                                e.printStackTrace();
                            }
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
        //Atomic Reference - referencing the output stream and input stream
        private AtomicReference<ObjectOutputStream> out = new AtomicReference<ObjectOutputStream>();
        private AtomicReference<ObjectInputStream> in = new AtomicReference<ObjectInputStream>();
        //nickname of the client
        private String nickname;
        //authentication
        private byte[] salt;

        public ClientService(Socket socket){
            super();
            this.socket = socket;
            ip = socket.getInetAddress().getHostAddress();
        }

        public String getNickname(){return nickname;}

        public String getIpAddress(){return ip;}

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

        //sends an object to the client-- ONLY THIS CLIENT
        //when sending to all clients, use SENDMESSAGE method
        public void sendObject(Object ob){
            try{
                out.get().writeObject(ob);
            }catch(IOException ex){
                ex.printStackTrace();
            }
        }

        public void updateOnlines(){
            String s = "";
            for(int x = 1; x < onlinesListModel.getSize(); x++){
                s += onlinesListModel.getElementAt(x)+ "\n";
            }

            OnlineUsers on = new OnlineUsers(s);
            sendObject(on);
        }

        public void kick(){
            sendObject(encrypt(ChatRoom.KICKED_MESSAGE));
            closeStreams();
        }

        public void closeStreams(){
            try{
                out.get().close();
                in.get().close();
                socket.close();
            }catch(IOException ex){
                ex.printStackTrace();
            }
        }

        //creates and return a task that receives and handles messages from the client
        private FutureTask<Void> createTask() {
            FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>(){
                        public Void call(){
                            try{
                                int count = 1;
                                while(count < bannedListModel.getSize()){
                                    if(bannedListModel.get(count).equals(ip)){
                                        sendObject(ChatRoom.BANNED_MESSAGE);
                                        clientServices.remove(ClientService.this);
                                        closeStreams();
                                        //quit this task
                                        return null;
                                    }
                                    count++;
                                }
                                if(count >= bannedListModel.getSize()){
                                    sendObject(ChatRoom.ACCEPTED_MESSAGE);
                                }

                                //wait for client to send its nickname
                                nickname = in.get().readObject().toString();
                                salt = Hash.getRandomSalt();
                                sendObject(salt);
                                //authentication
                                byte[] receivedHash = (byte[]) in.get().readObject();
                                while(!Hash.verifyPassword(password, salt, receivedHash)){
                                    sendObject(ChatRoom.WRONG_PASSWORD);
                                    receivedHash = (byte[]) in.get().readObject();
                                }
                                sendObject(new String("Authenticated!"));

                                sendObject(globalPasswordSalt);

                                //add the service to list of client services-- used when sending messages
                                clientServices.add(ClientService.this);

                                onlinesListModel.addElement(nickname);//add to list of onlines
                                updateOnlines();

                                sendMessage(encrypt(nickname + " joined the chat!"), true, null);
                            }catch(ClassNotFoundException e){
                                e.printStackTrace();
                            }catch(IOException e){
                                e.printStackTrace();
                            }
                            while(true){
                                try{
                                    //wait for message then call sendMessage(String s);
                                    sendMessage((MessageHolder)in.get().readObject(), true, ClientService.this);
                                }catch(ClassNotFoundException ex){
                                    ex.printStackTrace();
                                }catch(IOException ex){
                                    //if client disconnects
                                    sendMessage(encrypt(nickname + " left the chat."), true, null);
                                    //close streams and socket
                                    try{
                                        in.get().close();
                                        out.get().close();
                                        socket.close();
                                    }catch(IOException exx){
                                        exx.printStackTrace();
                                    }
                                    //remove the name from online

                                    for(int x = 1; x < onlinesListModel.getSize(); x++){
                                        if(onlinesListModel.getElementAt(x) == nickname){
                                            onlinesListModel.remove(x);
                                        }
                                    }

                                    updateOnlines();
                                    closeStreams();
                                    //remove this clientservice from the list
                                    clientServices.remove(ClientService.this);
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
