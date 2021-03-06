/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
---------------------- */

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.Timer;

public class Client{
  
  //GUI
  //----
  JFrame f = new JFrame("Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JButton optionButton = new JButton("Options");
  JButton describeButton = new JButton("Describe");
  
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JPanel fecPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  JLabel statistic = new JLabel(); 
  
  JCheckBox fecCheckBox = new JCheckBox("FEC");
  ImageIcon icon;
  
  //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received from the server
  DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
  static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets
  
  Timer timer1, timer2; //timer used to receive data from the UDP socket
  byte[] buf; //buffer used to store data received from the server
  
  //RTSP variables
  //----------------
  //rtsp states
  final static int INIT = 0;
  final static int READY = 1;
  final static int PLAYING = 2;
  static int state; //RTSP state == INIT or READY or PLAYING
  Socket RTSPsocket; //socket used to send/receive RTSP messages
  
//input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String VideoFileName; //video file to request to the server
  int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
  int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)
  
// statistics
  int receive = 0; // amount of packages received
  int lost = 0; // amount of packages los
  int repaired = 0;
  int notrepaired = 0;
  int lastSequencenumber = 0; // seqnr of last package receive
  int lasttimestamp = 0;
  
  
  FECpacket fec_packet = new FECpacket();
  private boolean fecEnabled = false;

        
  int imagenumber = 1;
  int timecounter= 900;
  final static String CRLF = "\r\n";
  
  //Video constants:
  //------------------
  static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
  
  //--------------------------
  //Constructor
  //--------------------------
  public Client() {
    
    //build GUI
    //--------------------------
    
    //Frame
    f.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    
    //Buttons
    buttonPanel.setLayout(new GridLayout(1,0));
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);
    buttonPanel.add(optionButton);
    buttonPanel.add(describeButton);
    setupButton.addActionListener(new setupButtonListener());
    playButton.addActionListener(new playButtonListener());
    pauseButton.addActionListener(new pauseButtonListener());
    tearButton.addActionListener(new tearButtonListener());
    optionButton.addActionListener(new optionButtonListener());
    describeButton.addActionListener(new describeButtonListener());
    fecPanel.setLayout(new GridLayout(6,0));
    fecPanel.add(fecCheckBox);
    fecCheckBox.addItemListener(new fecCheckBoxListener());

    //Image display label
    iconLabel.setIcon(null);
    
    //frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    mainPanel.add(fecPanel);
    mainPanel.add(statistic);
    iconLabel.setBounds(110,0,380,280);
    buttonPanel.setBounds(0,280,600,50);
    fecPanel.setBounds(500,350,600,100);
    statistic.setBounds(10, 335, 500, 100);
    
    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(600,450));
    f.setVisible(true);
    
    timer1 = new Timer(20, new timer1Listener());
    timer1.setInitialDelay(0);
    timer1.setCoalesce(true);
    
    timer2 = new Timer(40, new timer2Listener());
    timer2.setInitialDelay(2000);
    timer2.setCoalesce(true);
    
    //allocate enough memory for the buffer used to receive data from the server
    buf = new byte[15000];
  }
  
  public static void main(String argv[]) throws Exception{

    //Create a Client object
    Client theClient = new Client();
    
    //get server RTSP port and IP address from the command line
    //------------------
    int RTSP_server_port = Integer.parseInt(argv[1]);
    String ServerHost = argv[0];
    InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
    
    //get video filename to request:
    VideoFileName = argv[2];
    
    //Establish a TCP connection with the server to exchange RTSP messages
    //------------------
    theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);
    
    //Set input and output stream filters:
    RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()) );
    RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()) );
    
    //init RTSP state:
    state = INIT;
  }
  
  
  //------------------------------------
  //Handler for buttons
  //------------------------------------
  
  //Handler for Setup button
  //-----------------------
  class setupButtonListener implements ActionListener{
    public void actionPerformed(ActionEvent e){
      
      System.out.println("\nSetup Button pressed !");
      
      if (state == INIT) {
        //Init non-blocking RTPsocket that will be used to receive data
        try{
          //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
          RTPsocket = new DatagramSocket(RTP_RCV_PORT);
          
          //set TimeOut value of the socket to 5msec.
          RTPsocket.setSoTimeout(5);
        } catch (SocketException se) {
          System.out.println("Socket exception: "+se);
          System.exit(0);
        }
        
        //init RTSP sequence number
        RTSPSeqNb = 1;
        
        //Send SETUP message to the server
        send_RTSP_request("SETUP");
        
        if (parse_server_response() != 200){
          System.out.println("Invalid Server Response");
        } else {
          //change RTSP state and print new state
          state = READY;
          System.out.println("New RTSP state: READY");
        }
      }//else if state != INIT then do nothing
    }
  }
  
  //Handler for Play button
  //-----------------------
  class playButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      
      System.out.println("\nPlay Button pressed !");
      
      if (state == READY) {
        //increase RTSP sequence number
        RTSPSeqNb++;
        
        //Send PLAY message to the server
        send_RTSP_request("PLAY");
        
        if (parse_server_response() != 200){
          System.out.println("Invalid Server Response");
        } else {
          //change RTSP state and print out new state
          state = PLAYING;
          System.out.println("New RTSP state: PLAYING");
          
          //start the timer
          timer1.start();
          
          // start the displaytimer
          timer2.start();
        }
      }//else if state != READY then do nothing
    }
  }
  
  
  //Handler for Pause button
  //-----------------------
  class pauseButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      
      System.out.println("\nPause Button pressed !");
      
      if (state == PLAYING) {
        //increase RTSP sequence number
        RTSPSeqNb++;
        
        //Send PAUSE message to the server
        send_RTSP_request("PAUSE");
        
        if (parse_server_response() != 200){
          System.out.println("Invalid Server Response");
        } else {
          //change RTSP state and print out new state
          state = READY;
          System.out.println("New RTSP state: READY");
          
          //stop the timer
          timer1.stop();
          
          
          // start the displaytimer
          timer2.stop();
        }
      }
      //else if state != PLAYING then do nothing
    }
  }
  
  //Handler for Teardown button
  //-----------------------
  class tearButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      
      System.out.println("\nTeardown Button pressed !");
      
      //increase RTSP sequence number
      RTSPSeqNb++;
      
      //Send TEARDOWN message to the server
      send_RTSP_request("TEARDOWN");
      
      if (parse_server_response() != 200){
        System.out.println("Invalid Server Response");
      } else {
        //change RTSP state and print out new state
        state = INIT;
        System.out.println("New RTSP state: INIT");
        
        //stop the timer
        timer1.stop();
        
        
        // start the displaytimer
        timer2.stop();
        
        //exit
        System.exit(0);
      }
    }
  }
  
  
  //Handler for Option button
  //-----------------------
  class optionButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      
      System.out.println("\nOption Button pressed !");
      
      //increase RTSP sequence number
      RTSPSeqNb++;
      
      
      //Send TEARDOWN message to the server
      send_RTSP_request("OPTIONS");
      
      try {
        //Wait for the response
        if (parse_options() != 200)
          System.out.println("Invalid Server Response");
      } catch (IOException ex) {
        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }
  
  //Handler for Describe button
  //-----------------------
  
  class describeButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      System.out.println("\nDescribe Button pressed !");
      
      //increase RTSP sequence number
      RTSPSeqNb++;
      
      //Send TEARDOWN message to the server
      send_RTSP_request("DESCRIBE");
      
      
      if (parse_server_response() != 200) {
        System.out.println("Invalid Server Response");
      } else {
        System.out.println("Received response for DESCRIBE");
      }
    }
  }
  
  private class fecCheckBoxListener implements ItemListener {
			@Override
			public void itemStateChanged(ItemEvent e) {
				int stateChange = e.getStateChange();
				if (stateChange == ItemEvent.SELECTED) {
					fecEnabled = true;
				} else if (stateChange == ItemEvent.DESELECTED) {
					fecEnabled = false;
				}
			}
		}
  
  //------------------------------------
  //Handler for timer
  //------------------------------------
  
  class timer1Listener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      
      // Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(buf, buf.length);
      
      try {
        // receive the DP from the socket:
        RTPsocket.receive(rcvdp);
        
        // create an RTPpacket object from the DP
        RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
        // System.out.println(rtp_packet.PayloadType);
        
        if (rtp_packet.getpayloadtype() == 26) { // rtp
          
          // update statistics
          receive++;
          lost += rtp_packet.getsequencenumber() - lastSequencenumber - 1;
          lastSequencenumber = rtp_packet.getsequencenumber();
          
          
          // add receveid package to ArrayList
          fec_packet.rcvdata(rtp_packet);
          
        } else if (rtp_packet.getpayloadtype() == 127 && fecEnabled) { // fec
          
          fec_packet.rcvfec(rtp_packet);
        }
        
        if (rtp_packet.gettimestamp() > timecounter) {
          printstatistik(rtp_packet.gettimestamp());
          lasttimestamp = rtp_packet.gettimestamp();
          timecounter +=1000;
        }
        
        
      } catch (InterruptedIOException iioe) {
      } catch (IOException ioe) {
        System.out.println("Exception caught: " + ioe);
      }
      
      if(timecounter==20999){
        timer1.stop();
      }
    }
  }
  
  class timer2Listener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      try {
        byte[] payload = fec_packet.getjpeg(imagenumber);
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(payload, 0, payload.length);
        
        icon = new ImageIcon(image);
        iconLabel.setIcon(icon);
      } catch (NullPointerException npe) {
      }
      imagenumber++;
    }
  }
  
  public void printstatistik(int timestamp) {
    repaired = fec_packet.getrepaired();
    if( notrepaired < lost - repaired){
      notrepaired = lost - repaired;
    }
    double lostrate = (double) (lost) / (receive + lost) * 100;
    double datenrate = (double) (receive) / timestamp * 1000;
        
    String statistik = "";
    statistik += String.format("Erhalten: %d<br>", receive);
    statistik += String.format("Verloren: %d<br>", lost);
    statistik += String.format("Verlustrate: %.2f%%<br>", lostrate);
    statistik += String.format("Rekonstruierte Pakete: %d<br>", repaired);
    statistik += String.format("Nicht-Rekonstruierte Pakete: %d<br>", notrepaired);
    statistik += String.format("Datenrate: %.2f Pakete/s<br>", datenrate);
    statistic.setText("<html>" + statistik + "</html>");
  }
  //------------------------------------
  //Parse Server Response
  //------------------------------------
  private int parse_server_response() {
    int reply_code = 0;
    
    try{
      //parse status line and extract the reply_code:
      String StatusLine = RTSPBufferedReader.readLine();
      System.out.println("RTSP Client - Received from Server:");
      System.out.println(StatusLine);
      
      StringTokenizer tokens = new StringTokenizer(StatusLine);
      tokens.nextToken(); //skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());
      
      //if reply code is OK get and print the 2 other lines
      if (reply_code == 200) {
        String SeqNumLine = RTSPBufferedReader.readLine();
        System.out.println(SeqNumLine);
        
        String SessionLine = RTSPBufferedReader.readLine();
        System.out.println(SessionLine);
        
        tokens = new StringTokenizer(SessionLine);
        String temp = tokens.nextToken();
        
        //if state == INIT gets the Session Id from the SessionLine
        if (state == INIT && temp.compareTo("Session:") == 0) {
          RTSPid = Integer.parseInt(tokens.nextToken());
        } else if (temp.compareTo("Content-Base:") == 0) {
          // Get the DESCRIBE lines
          String newLine;
          for (int i = 0; i < 6; i++) {
            newLine = RTSPBufferedReader.readLine();
            System.out.println(newLine);
          }
        }
      }
    }catch(Exception ex){
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }
    return(reply_code);
  }
  
  private int parse_options() throws IOException {
    int reply_code = 0;
    
    try{
      //parse status line and extract the reply_code:
      String StatusLine = RTSPBufferedReader.readLine();
      System.out.println("RTSP Client - Received from Server:");
      System.out.println(StatusLine);
      
      StringTokenizer tokens = new StringTokenizer(StatusLine);
      tokens.nextToken(); //skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());
      
      //if reply code is OK get and print the 2 other lines
      if (reply_code == 200) {
        String SeqNumLine = RTSPBufferedReader.readLine();
        System.out.println(SeqNumLine);
        
        String OptionsLine = RTSPBufferedReader.readLine();
        System.out.println(OptionsLine);
      }
      
    } catch(Exception ex){
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }
    
    return(reply_code);
  }
  
  //------------------------------------
  //Send RTSP Request
  //------------------------------------
  
  private void send_RTSP_request(String request_type) {
    try {
      //Use the RTSPBufferedWriter to write to the RTSP socket
      
      //write the request line:
      RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);
      
      //write the CSeq line:
      RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
      
      //check if request_type is equal to "SETUP" and in this case write the Transport: line advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
      if (request_type == "SETUP") {
        RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
      }
      else if (request_type == "DESCRIBE") {
        RTSPBufferedWriter.write("Accept: application/sdp" + CRLF);
      }
      else {
        //otherwise, write the Session line from the RTSPid field
        RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
      }
      
      RTSPBufferedWriter.flush();
    }
    catch(Exception ex) {
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }
  }
}//end of Class Client

