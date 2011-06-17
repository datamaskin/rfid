package com.sexingtechnologies.rfid.jumparena;
/**
 * Created by IntelliJ IDEA.
 * User: david
 * Date: 5/26/11
 * Time: 9:46 AM
 * To change this template use File | Settings | File Templates.
 */


import de.avetana.bluetooth.connection.JSR82URL;
import de.avetana.bluetooth.hci.LinkQuality;
import de.avetana.bluetooth.hci.Rssi;
import de.avetana.bluetooth.stack.BlueZ;
import de.avetana.bluetooth.util.BTAddress;
import de.avetana.bluetooth.util.IntDeviceFinder;
import de.avetana.bluetooth.util.ServiceFinderPane;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.im.InputContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// InqDevice imports
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

import javax.bluetooth.*;

import de.avetana.bluetooth.sdp.RemoteServiceRecord;
import de.avetana.bluetooth.stack.BlueZ;
import de.avetana.bluetooth.util.MSServiceRecord;

/*<applet
        code="rfid"
        width=880
        height=190
        class="rfid"
        archive="avetanaBluetooth.jar, rfid.jar">
</applet>*/

public class JumpArena extends Applet implements ItemListener, ActionListener {

    private Panel   tlPanel, inquiry, top, wandData, btDevices, connectPanel;
    private Frame   finderFrame;
    private Button  inquire;
    private Button  remote; // = new Button("Get remote device info")
    private Button  localAddress, localName, localDevClass, record, property, setLocalDevClass, discoverable, getRFIDS, clearRFIDS;
    private ToggleButton connect, storeRFIDS;
    private TextArea connectionURL;
    private TextArea btDisplay;
    private Label   protoLabel, inquiryLable, btLable, connectLabel, dummy;
    private Label   dataReceived = new Label("Received 0");
    private Label   RFID = new Label("0");
    private Choice  protoChoose, inquiryChoose;
    private String  protoStr, msg;
    private CheckboxGroup radioGrp = null;
    private Checkbox lightningRod, rfidServer;
    private int currentIndex = 0;

  private DiscoveryAgent m_agent;
  private LocalDevice m_local;

    // Connection streams. These streams are only used with BluetoothStream connections (RFCOMN)
  private InputStream is = null;
  private OutputStream os = null;

  // The connection instance. Can be an L2CAPConnectionImpl or an RFCOMMConnectionImpl, depending on
  // the protocol choosen. (VERSION 1.2)
  private Connection streamCon = null;
  // Connection notifier for SDP server profiles
  private Connection notify=null;

  // Own thread for receiving and sending data for the protocols, which use connection streams.
  private DStreamThread receiverThread = null;
//  private StoreRFIDThread storeRFIDThread = null;
  private Thread getRFIDThread = null;

  private static final int rfcommPackLen = 100;
  private static final int xoffset = 100;
  private static final int yoffset = 100;

  static String[] connectStr  = {"Connect", "Disconnect"};
  static String[] storeStr    = {"Store:ON", "Store:OFF"};

//  private MsgBox notConnected = new MsgBox(false, "Not Connected...", "Connection Error");
  private OKDialog remoteInfo, devNotFound, connectError, storeRFIDon, storeRFIDoff;
  private String btName;

    public void init() {

        /*tlPanel         = new Panel();
        tlPanel.setBackground(Color.BLUE);
        tlPanel.setVisible(true);*/

        try {
            initStack();
        } catch (Exception e) {
            e.printStackTrace();
        }
        inquiry         = new Panel();
        inquiry.setBackground(Color.GREEN);
        inquiry.setVisible(true);
        Dimension dInquiry = inquiry.getPreferredSize();
        System.out.println("inquiry Dim: " + dInquiry.getHeight() + ", " + dInquiry.getWidth());
        inquiry.setSize(dInquiry);
        inquiry.setMaximumSize(dInquiry);

        top             = new Panel();
        top.setBackground(Color.GRAY);
        top.setVisible(true);
        top.setLayout(new GridLayout(2, 3, 5, 5));
        Dimension dTop = top.getPreferredSize();
        System.out.println("top Dim: " + dTop.getHeight() + ", " + dTop.getWidth());
        top.setSize(dTop);
        top.setMaximumSize(dTop);

        wandData = new Panel();
        wandData.setBackground(Color.MAGENTA);
        wandData.setVisible(true);
        wandData.setLayout(new GridLayout(1, 3, 5, 5));
        getRFIDS        = new Button("Download");
        clearRFIDS      = new Button("Clear");
        storeRFIDS      = new ToggleButton(storeStr);
        storeRFIDS.addActionListener(storeRFIDS);
        storeRFIDS.setPrevString(1);
        wandData.add(getRFIDS);
        wandData.add(clearRFIDS);
        wandData.add(storeRFIDS);

        protoChoose     = new Choice();
        protoChoose.add("L2CAP");
        protoChoose.add("RFCOMM");
        protoChoose.add("OBEX");
        protoChoose.select("RFCOMM");
        protoChoose.addItemListener(this);
        protoLabel = new Label("Choose service protocol: ");
        dummy = new Label("                ");

        // Create the BT device remote and local discovery panels and components
        inquiry         = new Panel();
        inquiry.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        inquire         = new Button("View remote devices");
        inquire.addActionListener(this);
        inquiry.add(inquire);
        inquiryLable    = new Label("Local devices: ");
        inquiry.add(inquiryLable);
        inquiryChoose   = new Choice();
        inquiryChoose.addItem("Default (first available)");
        inquiry.add(inquiryChoose);
        top.add(inquire);
        top.add(inquiry);
        top.add(inquiryChoose);
        top.add(protoLabel);
        top.add(protoChoose);
        top.add(dummy);

        connectPanel    = new Panel();
        connectPanel.setBackground(Color.ORANGE);
        connectPanel.setLayout(new GridLayout(2, 3, 5, 5));
        connectLabel    = new Label("Connections:");

//        connect         = new Button("Connect");

        connect         = new ToggleButton(connectStr);
        connect.addActionListener(connect);
        connect.setPrevString(0);
        System.out.println("Connection status: " + connect.getPrevString());
        radioGrp        = new CheckboxGroup();
        lightningRod    = new Checkbox("LightningRod", radioGrp, true);
        rfidServer      = new Checkbox("RFID Server", radioGrp, false);

        connectionURL   = new TextArea("", 5, 25, TextArea.SCROLLBARS_VERTICAL_ONLY);
//        connectPanel.setSize(); work the Dimension class here and the other components.
        connectPanel.add(connectLabel);
        connectPanel.add(lightningRod);
        connectPanel.add(connect);
        connectPanel.add(connectionURL);
        connectPanel.add(dummy);
        connectPanel.add(dummy);
        /*tlPanel.add(wandData);
        tlPanel.add(connectPanel);*/

        // Create the local device display panel
        btDevices       = new Panel();
        btDevices.setLayout(new GridLayout(0, 1, 5, 5));
        btDevices.setBackground(Color.GREEN);
        btDevices.setVisible(true);
        btLable = new Label("Local Device");
        btDevices.add(btLable);
        btDisplay       = new TextArea(msg, 8, 25, TextArea.SCROLLBARS_VERTICAL_ONLY);
        btDisplay.setBackground(Color.YELLOW);
        btDisplay.setEditable(false);

        try {
            LocalDevice.getLocalDevice();
            msg = BlueZ.hciDevBTAddress(0).toString().replaceAll(":", "-");
        } catch (Exception e) {
             msg = e.getLocalizedMessage();
            e.printStackTrace();
        }

        btDisplay.setText(msg);
        btDevices.add(btDisplay);

        this.setLayout(new BorderLayout());
        this.setBackground(Color.RED);
        this.add(top, BorderLayout.NORTH);
        this.add(btDevices, BorderLayout.WEST);
        this.add(connectPanel, BorderLayout.EAST);
        this.add(wandData, BorderLayout.SOUTH);
        Dimension xDim = this.getPreferredSize();
        System.out.println("xDim: " + xDim.getHeight() + ", " + xDim.getWidth());
        xDim.setSize(xDim.getWidth(), xDim.getHeight()/2);
        this.setSize(xDim);
        this.setPreferredSize(xDim);
        addListeners();
        enableConnAttributes(false);
    }

    public void itemStateChanged(ItemEvent ie) {

        if(protoChoose.getSelectedItem().equalsIgnoreCase("l2cap"))
            protoStr = "l2cap";
        else if(protoChoose.getSelectedItem().equalsIgnoreCase("rfcomm"))
            protoStr = "rfcomm";
        else
            protoStr = "obex";
    }

    /**
    * Shows a dialog containing a list of all BT devices.
    */
   public void startInquiry() {
     finderFrame = new Frame("Device Finder");
     try {
       IntDeviceFinder myFinder = new IntDeviceFinder(finderFrame, false);
       myFinder.setVisible(true);
     }catch(Exception ex) {ex.printStackTrace();}
   }

    public void actionPerformed(ActionEvent ae) {
        try {
            System.out.println("Connect Status: " + connect.getPrevString());
            if(receiverThread != null) {
                System.out.println("Connect Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
            }
            if(ae.getSource() == inquire) {
                try {
                    InqDevice inqDevice = new InqDevice(null, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ae.getSource() == connect && connect.getPrevString().equalsIgnoreCase("Connect")) {
             System.out.println ("Trying to connect to " + connectionURL.getText() + " " + streamCon);
             streamCon = Connector.open(connectionURL.getText());
             System.out.println ("Connected to " + connectionURL.getText() + " " + streamCon);

             if(streamCon instanceof StreamConnection) {
               is = ((StreamConnection)streamCon).openInputStream();
               os = ((StreamConnection)streamCon).openOutputStream();
               if (this.receiverThread != null) { receiverThread.stopReading(); receiverThread = null; }
               receiverThread = new DStreamThread();
               receiverThread.start();
               System.out.println("Connect Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
             }
             enableConnAttributes(true);
           }
           else if (ae.getSource() == connect && connect.getPrevString().equalsIgnoreCase("Disconnect")) {
             closeConnection();
           } else if (ae.getSource() == storeRFIDS && storeRFIDS.getPrevString().equalsIgnoreCase("Store:ON") &&
                    protoChoose.getSelectedIndex() == JSR82URL.PROTOCOL_RFCOMM) {
                System.out.println("Command J Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                byte b[] = new byte[] {(char)'J'}; // Store IDs off
                int csum = 0;
                /*for (int i = 0; i < b.length; i++) {
                    csum += (int)(b[i] & 0xff);
                }*/
                System.out.println ("Checksum of burst " + (csum % 1024));
                int count = 0;
                boolean sendForever = System.getProperty("de.avetana.bluetooth.test.sendForever", "false").equals("true");
                System.out.println("sendData.sendForever: " + sendForever);
                int sendTimes = Integer.parseInt(System.getProperty("de.avetana.bluetooth.test.sendPackets", "1"));
                System.out.println("sendData.sendTimes: " + sendTimes);
                int countTimes = 0;
                do {
                    countTimes++;
                    synchronized (os) {
                        System.out.println("J command before synchronized(os) Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                        os.write(b);
//                        System.out.println("J command bytes received: "+(byte)is.read());
                        System.out.println("J command after synchronized(os) Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                    }

                    count += b.length;
                    System.out.println ("J command sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);

                System.out.println (" storeRFIDS:ON Wrote " + (char)(b[0]));
            } else if (ae.getSource() == storeRFIDS && storeRFIDS.getPrevString().equalsIgnoreCase("Store:OFF") &&
                    protoChoose.getSelectedIndex() == JSR82URL.PROTOCOL_RFCOMM) {
                byte b[] = new byte[] {(char)'I'}; // Store IDs off
                int csum = 0;
                /*for (int i = 0; i < b.length; i++) {
                    csum += (int)(b[i] & 0xff);
                }*/
                System.out.println ("Checksum of burst " + (csum % 1024));
                int count = 0;
                boolean sendForever = System.getProperty("de.avetana.bluetooth.test.sendForever", "false").equals("true");
                System.out.println("sendData.sendForever: " + sendForever);
                int sendTimes = Integer.parseInt(System.getProperty("de.avetana.bluetooth.test.sendPackets", "1"));
                System.out.println("sendData.sendTimes: " + sendTimes);
                int countTimes = 0;
                do {
                    countTimes++;
                    synchronized (os) {
                        os.write(b);
//                        System.out.println("I command bytes received: "+(byte)is.read());
                    }
                    count += b.length;
                    System.out.println ("I command sent " + count + " bytes");
                    System.out.println("I command Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                } while (sendForever || countTimes < sendTimes);
                System.out.println (" storeRFIDS:OFF Wrote " + (char)(b[0]));
           } else if (ae.getSource() == getRFIDS ) {
                byte b[] = new byte[] {(char)'G'}; // download all stored IDs
                int csum = 0;
                for (int i = 0; i < b.length; i++) {
                    csum += (int)(b[i] & 0xff);
                }
                System.out.println ("Checksum of burst " + (csum % 1024));
                int count = 0;
                boolean sendForever = System.getProperty("de.avetana.bluetooth.test.sendForever", "false").equals("true");
                System.out.println("sendData.sendForever: " + sendForever);
                int sendTimes = Integer.parseInt(System.getProperty("de.avetana.bluetooth.test.sendPackets", "1"));
                System.out.println("sendData.sendTimes: " + sendTimes);
                int countTimes = 0;
                do {
                    countTimes++;
                    synchronized (os) {
                        os.write(b);
//                        System.out.println("G command bytes received: " + is.read());
                        System.out.println("G command Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                    }
                    count += b.length;
                    System.out.println ("G command sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println (" getRFIDS Wrote " + (char)(b[0]));
           }  else if (ae.getSource() == clearRFIDS ) {
                byte b[] = new byte[] {(char)'M'}; // clear memory of all stored IDs
                int csum = 0;
                for (int i = 0; i < b.length; i++) {
                    csum += (int)(b[i] & 0xff);
                }
                System.out.println ("Checksum of burst " + (csum % 1024));
                int count = 0;
                boolean sendForever = System.getProperty("de.avetana.bluetooth.test.sendForever", "false").equals("true");
                System.out.println("sendData.sendForever: " + sendForever);
                int sendTimes = Integer.parseInt(System.getProperty("de.avetana.bluetooth.test.sendPackets", "1"));
                System.out.println("sendData.sendTimes: " + sendTimes);
                int countTimes = 0;
                do {
                    countTimes++;
                    synchronized (os) {
                        os.write(b);
//                        System.out.println("M command bytes received: "+is.read());
                        System.out.println("M command Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
                    }
                    count += b.length;
                    System.out.println ("M command sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println (" clearRFIDS Wrote " + (char)(b[0]));
           }
        } catch (Exception e2) {
            e2.printStackTrace();
            Frame f = findParentFrame();
            GraphicsConfiguration gc = getGraphicsConfiguration();
            Rectangle bounds = gc.getBounds();
            String noDevStr = "LightningROD not found: ";
            devNotFound = new OKDialog(f, noDevStr + e2.getMessage(), xoffset + bounds.x, yoffset + bounds.y);
            devNotFound.setVisible(true);
        }
    }

    private void addListeners() {
        inquire.addActionListener(this);
        protoChoose.addItemListener(this);
        connect.addActionListener(this);
        getRFIDS.addActionListener(this);
        clearRFIDS.addActionListener(this);
        storeRFIDS.addActionListener(this);
    }

    /**
    * Thread used to read data from an RFCOMM connection
    */

   private class DStreamThread extends Thread {

     private boolean running;
     private boolean recvdCommand = false;
     private int received;

     public DStreamThread() {
       super();
     }

     public void run() {
       running = true;
       received = 0;
       byte b[] = new byte[rfcommPackLen];
       byte c[] = new byte[rfcommPackLen];
       byte d[] = new byte[rfcommPackLen];
       StringBuilder doneStr = new StringBuilder();
       try {
		 int csum = 0, csumc = 0, a = 0;
         while (running) {
           System.out.println("DStreamThread is.read(): " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
           int v1 = is.read();
           int v2 = is.read();
           c[0] = (byte)v1;
           c[1] = (byte)v2;
           char c0 = (char)c[0];
           char c1 = (char)c[1];
           System.out.println ("DStreamThread.v1: " + v1 + " v2: " + v2);
           if(c0 == 'J')
               System.out.println("DStreamThread found J command: " + c0);
//           System.out.println("DStreamThread.c0: " + c0 + " c1: " + c1);
//           dataReceived.setText("Received " + received);
           System.out.println(" DStreamThread.received: " + received);
           if (is.available() > 0) {
            System.out.println("DStreamThread is.available: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
            a = is.read(b); // get a count of character data bytes
            System.out.println();
            System.out.println(" DStreamThread.charcount: " + a);
           } else {
               a = 0;
        	   try { synchronized (this) { wait(100); }} catch (Exception e) {}
           }

		   for (int i = 0;i < a;i++) {
               c[i+2] = b[i];
			   System.out.print (" DStreamThread: " + Integer.toHexString((int)(b[i] & 0xff)));
               System.out.print(" DStreamThread: " + (int)(b[i] & 0xff));
               if(c0 == 'J' && (b[i] != 13 && b[i] != 10))
                   doneStr.append((char)b[i]);
               System.out.println(" DStreamThread: " + (char)(b[i]));
			   csum += (int)(b[i] & 0xff);
		   }

           String str2 = doneStr.toString();
           if(c0 == 'J' && str2.equalsIgnoreCase("done")) {
               System.out.println("DStreamThread.str2 = Done: " + str2);
               Frame f = findParentFrame();
               GraphicsConfiguration gc = getGraphicsConfiguration();
               Rectangle bounds = gc.getBounds();
               String storeRFIDonStr = "Store RFID: ON";
               storeRFIDon = new OKDialog(f, storeRFIDonStr, xoffset+bounds.x, yoffset+bounds.y);
               storeRFIDon.setVisible(true);
           } else if (c0 == 'I' && str2.equalsIgnoreCase("done")) {
               Frame f = findParentFrame();
               GraphicsConfiguration gc = getGraphicsConfiguration();
               Rectangle bounds = gc.getBounds();
               String storeRFIDoffStr = "Store RFID: OFF";
               storeRFIDon = new OKDialog(f, storeRFIDoffStr, xoffset+bounds.x, yoffset+bounds.y);
               storeRFIDon.setVisible(true);
           }
           String str = new String(c);
//           RFID.setText("RFID:" + str);
           System.out.println ();
           System.out.println("DStreamThread print str: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
           System.out.println("**************************************DStreamThread.RFID: " + str);
		   System.out.println ();
		   csumc += a;
		   if (csumc >= rfcommPackLen) {
			   System.out.println ("Checksum after " + csumc + " is " + (csum % 1024));
			   csumc = csum = 0;
		   }
           received += a;
           //os.write (new byte[100]);
         }
       } catch (Exception e) {e.printStackTrace(); if (connect.getPrevString().equalsIgnoreCase("Disconnect")) { connect.setPrevString(0); try { closeConnection(); } catch (Exception ec) {}} }
     }

     public void stopReading() {
       running = false;
     }
   }

   private class sendCommand {

       private boolean countFound = false;
       private sendCommand() {
       }
   }

   private class ToggleButton extends Button implements ActionListener {

        static final int BUTTON_BORDER = 12 ;
        public static final int NO_Prev = -1 ;

        String[] strings ;
        int      n ;
        int      prevN ;

        public ToggleButton( String[] a ) {
            super( a[0] );

            strings = a;
            n       = 0;
            prevN   = NO_Prev;
        }

       public void actionPerformed(ActionEvent ae) {
            prevN = n ;
            System.out.println("ToggleButton.actionPerformed.prevN: " + prevN);
            if ( n < strings.length - 1 ) {
                 n++ ;
            } else {
                 n = 0 ;
            }

            FontMetrics fm = getFontMetrics( getFont() );

            int width = fm.stringWidth( strings[ n ] );
            int height = getHeight();

            setSize(width + BUTTON_BORDER, height);
            System.out.println("ToggleButton.actionPerformed.string[" + n + "]: " + strings[n]);
            setLabel( strings[ n ] );
        }

        public int getPrevN() {
            return prevN ;
        }

        public String getPrevString() {
            if ( prevN == NO_Prev ) {
                return "" ;
            }
            return strings[ prevN ] ;
        }

        public void setPrevString(int idx) {
            if(prevN == NO_Prev)
                prevN = idx;
            System.out.println("ToggleButton.prevN: " + prevN);
        }
    }

     /**
    * Closes the connection managed by this class
    * @throws Exception
    */
   public void closeConnection() throws Exception{
     System.out.println ("Closing receiverThread");
     if (this.receiverThread != null) { receiverThread.stopReading(); receiverThread = null; connect.setPrevString(0);}
     System.out.println ("Closing InputStream");
     if (is != null) { is.close(); is = null; }
     System.out.println ("Closing OutputStream");
     if (os != null) { os.close(); os = null; }
     System.out.println ("Closing streamCon");
     if (streamCon != null) { streamCon.close(); streamCon = null; }
     System.out.println ("Closing Connections");
     enableConnAttributes(false);

   }

   public class MsgBox implements ActionListener {
      Button b, closeB;
      Dialog d;
      boolean modality;
      String name, aLabel;


      public MsgBox(boolean modality, String name, String aLabel) {
          super();
          this.modality = modality;
          this.name = name;
          this.aLabel = aLabel;
      }

      private Frame findParentFrame() {

        Component parent = getParent();
         while( parent.getParent() != null )
         {
            parent = parent.getParent();
         }
         Frame topFrame = ( Frame )parent;
         return topFrame;
      }

      public void actionPerformed(ActionEvent e){
        Frame f = findParentFrame();
        if(f != null){
    //      d = new Dialog(f, "modalDialog", false);
          if(d != null) {
              d.setModal(modality);
              d.setName(name);
              d.setLayout(new FlowLayout());
              d.add(new Label(aLabel));
              d.pack();
              d.setLocation(100,100);
              d.setVisible(false);
              d.dispose();
          } else {
              d = new Dialog(f, aLabel, false);
              d.setModal(modality);
              d.setName(name);
              d.setLayout(new FlowLayout());
//              d.add(new Label(aLabel));
              d.pack();
              d.setLocation(150,150);
              d.setVisible(true);
          }
        }
      }
   }


   public class YesNoDialog extends Dialog implements ActionListener {

      private Button yes = new Button("Yes");
      private Button no = new Button("No");

      public YesNoDialog(Frame parent, String message) {

        super(parent, true);
        this.add(BorderLayout.CENTER, new Label(message));
        Panel p = new Panel();
        p.setLayout(new FlowLayout());
        yes.addActionListener(this);
        p.add(yes);
        no.addActionListener(this);
        p.add(no);
        this.add(BorderLayout.SOUTH, p);
        this.setSize(300,100);
        this.setLocation(100, 200);
        this.pack();

      }

      public void actionPerformed(ActionEvent evt) {
        this.setVisible(false);
        this.dispose();
      }

   }

   public class OKDialog extends Dialog implements ActionListener {

      private Button ok = new Button("OK");

      public OKDialog(Frame parent, String message, int xoffset, int yoffset) {

        super(parent, true);
        this.add(BorderLayout.CENTER, new Label(message));
        Panel p = new Panel();
        p.setLayout(new FlowLayout());
        ok.addActionListener(this);
        p.add(ok);
        this.add(BorderLayout.SOUTH, p);
        this.setSize(300,100);
        this.setLocation(xoffset, yoffset);
        this.pack();
      }

      public void actionPerformed(ActionEvent evt) {
        this.setVisible(false);
        this.dispose();
      }
   }

  /**
    * Inits the stack.
    *
    * @throws Exception
    */
   public void initStack() throws Exception{
	   de.avetana.bluetooth.stack.BluetoothStack.init(new de.avetana.bluetooth.stack.AvetanaBTStack());
     m_local=LocalDevice.getLocalDevice();
     m_agent=m_local.getDiscoveryAgent();
   }

    /**
    * Shows information about the remote device (name, device class, BT address ..etc..)
    */
   public void getRemoteDevInfos() {
   	 RemoteDevice rd = null;
   	 String name = "unknown";
   	 int rssi = 0;
	 int lq = 0;
   	 try {
     	rd = RemoteDevice.getRemoteDevice(streamCon);
		name = rd.getFriendlyName(false);
		rssi = Rssi.getRssi(rd.getBTAddress());
		lq = LinkQuality.getLinkQuality(rd.getBTAddress());
	} catch (Exception e) {

		return;
	}
     Frame f = findParentFrame();
     GraphicsConfiguration gc = getGraphicsConfiguration();
     Rectangle bounds = gc.getBounds();
     String remoteInfoStr = "Remote Device Address, Name, Rssi und Quality\n" + rd.getBluetoothAddress() + " " + name + " " + rssi + " " + lq;
     remoteInfo = new OKDialog(f, remoteInfoStr, xoffset + bounds.x, yoffset + bounds.y);
     remoteInfo.setVisible(true);
   }

    /**
    * Shows information about the remote device (name, device class, BT address ..etc..)
    */
   public String getRemoteDevName() {
   	 RemoteDevice rd = null;
   	 String name = "unknown";
   	 int rssi = 0;
	 int lq = 0;
     Frame f = findParentFrame();
     GraphicsConfiguration gc = getGraphicsConfiguration();
     Rectangle bounds = gc.getBounds();
   	 try {
     	rd = RemoteDevice.getRemoteDevice(streamCon);
		name = rd.getFriendlyName(false);
		rssi = Rssi.getRssi(rd.getBTAddress());
		lq = LinkQuality.getLinkQuality(rd.getBTAddress());
	 } catch (Exception e) {
        remoteInfo = new OKDialog(f, e.getMessage(), xoffset + bounds.x, yoffset + bounds.y);
        remoteInfo.setVisible(true);
		return null;
	 }
     return name;
   }

   /**
    * Shows information about the remote device (name, device class, BT address ..etc..)
    */
   public String getRemoteDevBTAddress() {
   	 RemoteDevice rd = null;
     BTAddress btAddress = null;
   	 String name = "unknown";
   	 int rssi = 0;
	 int lq = 0;
     Frame f = findParentFrame();
     GraphicsConfiguration gc = getGraphicsConfiguration();
     Rectangle bounds = gc.getBounds();
   	 try {
     	rd = RemoteDevice.getRemoteDevice(streamCon);
		name = rd.getFriendlyName(false);
        btAddress = rd.getBTAddress();
		rssi = Rssi.getRssi(rd.getBTAddress());
		lq = LinkQuality.getLinkQuality(rd.getBTAddress());
	 } catch (Exception e) {
        remoteInfo = new OKDialog(f, e.getMessage(), xoffset + bounds.x, yoffset + bounds.y);
        remoteInfo.setVisible(true);
		return null;
	 }
     if(name.indexOf("LightningRod") > 0)
        return btAddress.toString();
     else
        return null;
   }

    private Frame findParentFrame() {

        Component parent = getParent();
         while( parent.getParent() != null )
         {
            parent = parent.getParent();
         }
         Frame topFrame = ( Frame )parent;
         return topFrame;
      }

    /**
     *  if the program is called without any parameters, it does an inquiry.
     *  If it is called with 1 parameter it does a service serach on the device specified (e.g. 000d9305170e).
     *  If it is called with 2 parameters, the second parameter is considered a UUID on which the service search
     *  is supposed to be restricted on
     */

    public class InqDevice implements DiscoveryListener {

        private static final boolean doException = false;
        private boolean searchCompleted = false;
        private Vector devices;

        /* (non-Javadoc)
         * @see javax.bluetooth.DiscoveryListener#deviceDiscovered(javax.bluetooth.RemoteDevice, javax.bluetooth.DeviceClass)
         */
        public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
            devices.add(btDevice);
            try {
                System.out.println ("device discovered " + btDevice + " name " + btDevice.getFriendlyName(true) + " majc 0x" + Integer.toHexString(cod.getMajorDeviceClass()) + " minc 0x" + Integer.toHexString(cod.getMinorDeviceClass()) + " sc 0x" + Integer.toHexString(cod.getServiceClasses()));
                if(btDevice.getFriendlyName(true).startsWith("LightningROD")) {
                    connectionURL.setText("btspp://"+btDevice+":1");
                    btName = btDevice.getFriendlyName(true);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (doException) {
                byte[] b = new byte[0];
                b[1] = 0;
            }
        }

        /* (non-Javadoc)
         * @see javax.bluetooth.DiscoveryListener#servicesDiscovered(int, javax.bluetooth.ServiceRecord[])
         */
        public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
            // TODO Auto-generated method stub
            System.out.println (servRecord.length + " services discovered " + transID);

            if (doException) {
                byte[] b = new byte[0];
                b[1] = 0;
            }

            for (int i = 0;i < servRecord.length;i++) {
                try {
                    System.out.println ("Record " + (i + 1));
                    System.out.println ("" + servRecord[i]);
                    } catch (Exception e) { e.printStackTrace(); }
                    /*if (servRecord[0] instanceof RemoteServiceRecord) {
                        RemoteServiceRecord rsr = (RemoteServiceRecord)servRecord[0];
                        if (rsr.raw.length == 0) return;
                        for (int j = 0; j < rsr.raw.length;j++) {
                            System.out.print (" " + Integer.toHexString(rsr.raw[j] & 0xff));
                        }
                        System.out.println("\n----------------");

                        ServiceRecord rsr2;
                        try {
                            rsr2 = RemoteServiceRecord.createServiceRecord("000000000000", new byte[0][0], new int[] { 0,1,2,3,4,5,6,7,8,9,10, 256 }, rsr.raw);
                            byte[] raw2 = MSServiceRecord.getByteArray (rsr2);
                            for (int j = 0; j < raw2.length;j++) {
                                System.out.print (" " + Integer.toHexString(raw2[j] & 0xff));
                            }
                            System.out.println();

                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }*/
            }
        }

        /* (non-Javadoc)
         * @see javax.bluetooth.DiscoveryListener#serviceSearchCompleted(int, int)
         */
        public synchronized void serviceSearchCompleted(int transID, int respCode) {
            // TODO Auto-generated method stub
            System.out.println ("Service search completed " + transID + " / " + respCode);
            searchCompleted = true;
            notifyAll();
        }

        /* (non-Javadoc)
         * @see javax.bluetooth.DiscoveryListener#inquiryCompleted(int)
         */
        public synchronized void inquiryCompleted(int discType) {
            System.out.println ("Inquiry completed " + discType);
            searchCompleted = true;
            notifyAll();
            /*try {
                LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, InqDevice.this);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

        }

        public InqDevice(String addr, String uuid) throws Exception {
            devices = new Vector();
            System.out.println ("Getting discoveryAgent");
            DiscoveryAgent da = LocalDevice.getLocalDevice().getDiscoveryAgent();

            if (addr == null) {
                da.startInquiry(DiscoveryAgent.GIAC, this);
            } else {
                UUID uuids[];
                System.out.println ("Setting up uuids");
                if (uuid == null) uuids = new UUID[] { };
                else if (uuid.length() == 32) uuids = new UUID[] { new UUID (uuid, false) };
                else uuids = new UUID[] { new UUID (uuid, true) };
                System.out.println ("Starting search");
                int transID = da.searchServices(new int[] { 0x05, 0x06, 0x07, 0x08, 0x09, 0x100, 0x303 }, uuids, new RemoteDevice (addr), this);
                System.out.println ("Started transID: " + transID);
                //BufferedReader br = new BufferedReader (new InputStreamReader (System.in));
                //br.readLine();
                //if (!searchCompleted) da.cancelServiceSearch(transID);
                //System.out.println ("Canceled");

    /*			Thread.currentThread().sleep(100);

                System.out.println ("Interrupting service search " + transID);
                da.cancelServiceSearch(transID);
                Thread.currentThread().sleep(1500);
                System.out.println ("Restarting service search");
                searchCompleted = false;
                transID = da.searchServices(new int[] { 0x05, 0x06, 0x07, 0x08, 0x09, 0x100, 0x303 }, uuids, new RemoteDevice ("000E0799107C"), this);
                */
            }

                while (!searchCompleted) {
                    synchronized (this) { wait(100); }
                }
    //			BlueZ.authenticate (((RemoteDevice)devices.get(0)).getBluetoothAddress(), "6624");
                System.out.println("devices: " + devices.toString());
//                System.exit(0);

        }

        public InqDevice(String addr) throws BluetoothStateException, InterruptedException {
            devices = new Vector();
            int loop = 0;
            DiscoveryAgent da = LocalDevice.getLocalDevice().getDiscoveryAgent();
            while (true) {
                System.out.println ("Performing loop " + loop++);
                searchCompleted = false;
                devices.clear();
                if (addr == null || addr.equals("inq")) {
                    da.startInquiry(DiscoveryAgent.GIAC, this);
                    synchronized (this) { wait(60000); }
                    if (!searchCompleted) {
                        System.out.println ("Cancelling inquiry");
                        da.cancelInquiry(this);
                    }
                    if (("" + addr).equals("inq"))
                        continue;
                } else
                    devices.add(new RemoteDevice (addr));
                for (int i = 0;i < devices.size();i++) {
                    RemoteDevice rc = (RemoteDevice) devices.get(i);
                    searchCompleted = false;
                    System.out.println ("Searching services on " + rc.getBluetoothAddress());
                    int transID = da.searchServices(new int[] { 0x05, 0x06, 0x07, 0x08, 0x09, 0x100, 0x303 }, new UUID[0], rc, this);
                    synchronized (this) { wait(60000); }
                    if (!searchCompleted) {
                        System.out.println ("Cancelling service search");
                        da.cancelServiceSearch(transID);
                    }
                }
            }
        }
    }

    /**
    * Enables or disables all Widgets related with a connection.
    * @param enable <code>true</code> - Enable the widgets.<br>
    *               <code>false</code> - Disable them.
    */
   public void enableConnAttributes(boolean enable) {
     this.storeRFIDS.setEnabled(enable);
     this.dataReceived.setEnabled(enable);
     this.getRFIDS.setEnabled(enable);
     this.clearRFIDS.setEnabled(enable);
   }

}
