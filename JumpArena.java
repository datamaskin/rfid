package com.sexingtechnologies.rfid.jumparena; /**
 * Created by IntelliJ IDEA.
 * User: david
 * Date: 5/26/11
 * Time: 9:46 AM
 * To change this template use File | Settings | File Templates.
 */
 
//package com.sexingtechnologies.rfid.jumparena;

import de.avetana.bluetooth.connection.JSR82URL;
import de.avetana.bluetooth.stack.BlueZ;
import de.avetana.bluetooth.util.IntDeviceFinder;
import de.avetana.bluetooth.util.ServiceFinderPane;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.InputStream;
import java.io.OutputStream;


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

  private static final int rfcommPackLen = 100;

    static String[] connectStr  = {"Connect", "Disconnect"};
    static String[] storeStr    = {"Store:ON", "Store:OFF"};

  private MsgBox notConnected = new MsgBox(false, "Not Connected...", "Connection Error");

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

//        this.setLayout(new BorderLayout());
        protoChoose     = new Choice();
        protoChoose.add("L2CAP");
        protoChoose.add("RFCOMM");
        protoChoose.add("OBEX");
        protoChoose.select("RFCOMM");
        protoChoose.addItemListener(this);
        protoLabel = new Label("Choose service protocol: ");
        dummy = new Label("                ");

        /*wandData.add(protoLabel);
        wandData.add(protoChoose);*/

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
        connectionURL   = new TextArea("btspp://", 5, 25, TextArea.SCROLLBARS_VERTICAL_ONLY);
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
        btLable = new Label("Bluetooth Devices");
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
       IntDeviceFinder myFinder=new IntDeviceFinder(finderFrame, true);
       myFinder.setVisible(true);
     }catch(Exception ex) {ex.printStackTrace();}
   }

    public void actionPerformed(ActionEvent ae) {
        try {
            System.out.println("Connect Status: " + connect.getPrevString());
            if(receiverThread != null) {
                System.out.println("Thread status: " + receiverThread.getName() + " State: " + receiverThread.getState() + " Alive: " + receiverThread.isAlive());
            }
            if(ae.getSource() == inquire) {
                startInquiry();
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
             }
    //         enableConnAttributes(true);
           }
           else if (ae.getSource() == connect && connect.getPrevString().equalsIgnoreCase("Disconnect")) {
             closeConnection();
           } else if (ae.getSource() == storeRFIDS && storeRFIDS.getPrevString().equalsIgnoreCase("Store:ON") &&
                    protoChoose.getSelectedIndex() == JSR82URL.PROTOCOL_RFCOMM) {
                byte b[] = new byte[] {(char)'J'}; // Store IDs on
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
                    os.write(b);
                    count += b.length;
                    System.out.println ("Sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println ("Wrote " + (int)(b[0] & 0xff));
           } else if (ae.getSource() == storeRFIDS && storeRFIDS.getPrevString().equalsIgnoreCase("Store:OFF") &&
                    protoChoose.getSelectedIndex() == JSR82URL.PROTOCOL_RFCOMM) {
                byte b[] = new byte[] {(char)'I'}; // Store IDs off
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
                    os.write(b);
                    count += b.length;
                    System.out.println ("Sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println ("Wrote " + (int)(b[0] & 0xff));
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
                    os.write(b);
                    count += b.length;
                    System.out.println ("Sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println ("Wrote " + (int)(b[0] & 0xff));
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
                    os.write(b);
                    count += b.length;
                    System.out.println ("Sent " + count + " bytes");
                } while (sendForever || countTimes < sendTimes);
                System.out.println ("Wrote " + (int)(b[0] & 0xff));
           }
        } catch (Exception e2) {
                e2.printStackTrace();
                Button b = new Button("Exception");
                b.addActionListener(new MsgBox(false, "Exception", e2.getMessage()));
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
     private int received;

     public DStreamThread() {
       super();
     }

     public void run() {
       running = true;
       received = 0;
       byte b[] = new byte[rfcommPackLen];
       byte c[] = new byte[rfcommPackLen];
       try {
		   int csum = 0, csumc = 0, a = 0;
         while (running) {
           int v1 = is.read();
           int v2 = is.read();
           c[0] = (byte)v1;
           c[1] = (byte)v2;
           System.out.println (v1 + " " + v2);
           dataReceived.setText("Received " + received);
           if (is.available() > 0) {
            a = is.read(b); // get a count of character data bytes
            /*System.out.println();
            System.out.println("a: " + a);*/
           } else {
               a = 0;
        	   try { synchronized (this) { wait(100); }} catch (Exception e) {}
           }

		   for (int i = 0;i < a;i++) {
               c[i+2] = b[i];
			   System.out.print (" " + Integer.toHexString((int)(b[i] & 0xff)));
               System.out.println(" " + (int)(b[i] & 0xff));
			   csum += (int)(b[i] & 0xff);
		   }
           String str = new String(c);
           RFID.setText("RFID:" + str);
           System.out.println ();
           System.out.println(str);
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
//     enableConnAttributes(false);

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
              d = new Dialog(f);
              d.setModal(modality);
              d.setName(name);
              d.setLayout(new FlowLayout());
              d.add(new Label(aLabel));
              d.pack();
              d.setLocation(100,100);
              d.setVisible(true);
          }
        }
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

}
