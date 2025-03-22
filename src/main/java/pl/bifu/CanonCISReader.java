package pl.bifu;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.SerialPort;

/**
 * Reading  CIS-Reader attached to Arduino via Serial USB
 * 1 MBaud
 * (2 MBaud possible)
 * <p>
 * Serial Data Format:
 * Sending 'S'
 * Reading MAX_DATA_POINTS plus a 0 at the end
 *
 * @author Florian
 */

public class CanonCISReader {
    private static boolean m_Done;
    private static int CIS_SENSOR_PIXEL = 5000;
    private static int MAX_DATA_POINTS = CIS_SENSOR_PIXEL * 1;


    //private static volatile int[] m_ReceiveBuffer;
    private static volatile int m_BytesReceived = 0;
    private static volatile int m_TotalReceived;
    private static volatile long m_T0;
    private static volatile long m_T1;
    private static volatile int m_TxCount = 0;
    private static volatile int m_RxCount = 0;
    private static volatile int m_ErrorCount = 0;
    private static int N = 1000;
    public static final String APPLICATION_NAME = "CanonCISReader";

    protected static volatile String m_TestPortName;
    protected static volatile SerialPort m_Port;
    protected static volatile OutputStream m_Out;
    protected static volatile InputStream m_In;
    protected static int m_Progress;

    private static byte[] buffer;


    public static byte[] getBuffer() {
        return buffer;
    }

    public static int getMaxDataPoints() {
        return MAX_DATA_POINTS;
    }


    static boolean toogle8Bits() {
        byte[] buf = new byte[2];

        try {
            m_Port.getOutputStream().write('8');
            m_Port.getOutputStream().flush();
            while (m_Port.getInputStream().read(buf, 0, 1) == 0) ;
            System.out.println(buf[0]);
            if (buf[0] == 'y') return true;
            else return false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    static void set8Bits(boolean tobe) {
        boolean asis = toogle8Bits();
        if (asis != tobe) toogle8Bits();
        if (tobe) {
            MAX_DATA_POINTS = CIS_SENSOR_PIXEL;
        } else {
            MAX_DATA_POINTS = CIS_SENSOR_PIXEL * 2;

        }
        buffer = new byte[getMaxDataPoints() + 1];
    }

    static protected void openPort(String portName) throws Exception {
        try {
            System.out.println("openPort port=" + portName);
            m_TestPortName = portName;
            CommPortIdentifier portid = CommPortIdentifier.getPortIdentifier(m_TestPortName);
            System.out.println(portid);
            m_Port = (SerialPort) portid.open(APPLICATION_NAME, 1000);
            m_Out = m_Port.getOutputStream();
            m_In = m_Port.getInputStream();
            drain(m_In);
            System.out.println("openPort:done");
        } catch (NoSuchPortException e) {
            System.err.println("could no open port " + m_TestPortName);
        }

    }

    static protected void closePort() {
        if (m_Port != null) {
            try {
                m_Out.flush();
                m_Port.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                m_Port = null;
            }
        }
    }

    /**
     * Sleep a short amount of time to allow hardware feedback, which isn't
     * instant
     */
    static protected void sleep() throws InterruptedException {
        sleep(40);
    }

    static protected void sleep(int t) throws InterruptedException {
        int m = 1000;
        while (t > 0) {
            Thread.sleep(t > m ? m : t);
            t -= m;
            while ((System.currentTimeMillis() - m_T0) / m > m_Progress) {
                System.out.print(".");

            }
        }
    }


    static protected void drain(InputStream ins) throws Exception {
        sleep(100);
        int n;
        while ((n = ins.available()) > 0) {
            for (int i = 0; i < n; ++i)
                ins.read();
            sleep(100);
        }
    }

    static void begin(String name) {
        System.out.printf("%-46s", name);
        m_T0 = System.currentTimeMillis();
        m_Progress = 0;
    }

    public static int readLine() {
        int i;
        int zeroreads = 0;
        boolean error = false;
        m_ErrorCount = 0;

        int maxdatapoints = getMaxDataPoints();


        //System.out.println("maxdatapoints=" +maxdatapoints);

        m_T0 = System.currentTimeMillis();
        try {
            do {
                error = false;
                m_Port.getOutputStream().write('S');
                m_Port.getOutputStream().flush();
                m_TotalReceived = 0;
                do {

                    //System.out.print("?");
                    int m = maxdatapoints - m_TotalReceived;
                    m_BytesReceived = m_Port.getInputStream().read(buffer, m_TotalReceived, m);
                    if (m_BytesReceived == 0) zeroreads++;
                    else zeroreads = 0;
                    //if(zeroreads>50) break;
                    //System.out.print("!"+ m_BytesReceived);
                    //System.out.println ( m_TotalReceived);
					/*	for(i=0;i<m_BytesReceived;i++)
					{
						System.out.print(buffer[i] + " ");
					}
					System.out.println();
					 */

                    //if(m_BytesReceived>0)
                    m_TotalReceived += m_BytesReceived;

                }
                while (zeroreads < 3); // m_TotalReceived< maxdatapoints+1); //>0 && buffer[m_TotalReceived-1] != 0 );
                //
                //	for(int k=m_TotalReceived-5; k<m_TotalReceived-1; k++)
                //	System.out.println(m_TotalReceived + " " + buffer[k]);
                if (m_TotalReceived != maxdatapoints) {
                    error = true;
                    m_ErrorCount++;
                    System.out.println("Error TotalReceived=" + m_TotalReceived);
                }
            } while (error);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        m_T1 = System.currentTimeMillis();
        //	System.out.println("Done");
        return m_TotalReceived;
    }

    public int getErrorCount() {
        return m_ErrorCount;
    }

    public double getBytesPerSecond() {
        double actual = m_TotalReceived * 1000.0 / (m_T1 - m_T0);
        return actual;
    }


    public static void initReader(String portName, int speed) throws Exception {
        begin("CanonCISReader\n");

        System.out.println("initReader port=" + portName + " baud=" + speed);
        m_Done = false;

        m_BytesReceived = 0;
        m_TotalReceived = 0;
        m_TxCount = 0;
        m_RxCount = 0;
        m_ErrorCount = 0;

        openPort(portName);

        buffer = new byte[getMaxDataPoints() + 1];


        m_Port.notifyOnDataAvailable(true);
        m_Port.notifyOnOutputEmpty(true);
        m_Port.enableReceiveTimeout(100);
        m_Port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
        m_Port.setSerialPortParams(speed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

        //readLine();

        //readLine();

        //m_Port.getOutputStream().write('0' +mex);
        //m_Port.getOutputStream().flush();

		/*
			try {
			    Thread.sleep(22);                 //1000 milliseconds is one second.
			} catch(InterruptedException ex) {
			    Thread.currentThread().interrupt();
			}
			int n = m_Port.getInputStream().read(buffer,0,1);

			System.out.println( n + " " + buffer[0] );
			System.exit(0);
		 */
        System.out.println("initReader:done");

    }


    public static void main(String[] arg) {
        int n = 10;
        CanonCISReader r = new CanonCISReader();
        try {
            System.out.print("init");
            // ARDUINO Schnittstelle USART1 mit FTDI
            r.initReader("COM8", 250000);
            // ARDUINO USB-Schnittstelle USART0
            //r.initReader("COM5", 326400);
            r.set8Bits(true);
            System.out.println("read");
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                r.readLine();
                System.out.print(".");
            }
            long t1 = System.currentTimeMillis();
            System.out.println();
            System.out.println("Lines/s " + 1000.0 * (double) n / (t1 - t0));

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}