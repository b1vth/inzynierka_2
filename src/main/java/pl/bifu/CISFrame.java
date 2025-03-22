package pl.bifu;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

import javax.swing.Timer;

import jtermios.testsuite.TestBase;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.general.AbstractSeriesDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


public class CISFrame extends ApplicationFrame {


    //private static boolean EIGHTBITS = false;
    private static boolean EIGHTBITS = true;

    // ARDUINO Schnittstelle USART1 via FTDI-Adapter
    //private static final int BAUDRATE = 1000000;
    //private static final String SERIALPORT = "COM6";
    private static final int BAUDRATE = 1000000;
    private static final String SERIALPORT = "COM8";


    /*
    // ARDUINO USB-Schnittstelle USART0
	private static final int BAUDRATE = 326400; 
	private static final String SERIALPORT = "COM5"; 
	*/
    private static final String TITLE = "Dynamic Series";
    private static final String START = "Start";
    private static final String STOP = "Stop";
    private static final float MINMAX = 100;
    private static final int COUNT = 2 * 60;
    private static final int FAST = 100;
    private static final int SLOW = FAST * 5;
    private static final Random random = new Random();
    private Timer timer;

    private static double s_avgErrorCount=0.0;
    private static int s_totalErrorCount=0;
    private static int s_lines=0;

    CanonCISReader cisReader=null;

    long ntotal=0;
    private static double s_oldErrorCount=0.0;
    private byte[] readData()
    {
        cisReader.readLine();
        s_lines++;
        m_T1 = System.currentTimeMillis();
        System.out.println("(Err:"+ cisReader.getErrorCount()  + ") Lines/s=" + (1000.0* (double)s_lines / ( m_T1-m_T0)));
        return cisReader.getBuffer();
    }

    XYDataset dataset;
    XYSeries cissensordata=null;
    JFreeChart xylineChart=null;

    private double calcAverage(double k, double x, double yold)
    {
        return k*x + (1.0-k)*yold;
    }

    private XYSeriesCollection updateOrCreateDataset( )
    {
        byte[] y = readData();
        int tmp;
        XYSeriesCollection datasetcol=null;
        if(cissensordata==null)
        {
            cissensordata = new XYSeries( "CIS Sensor Data" );
            datasetcol = new XYSeriesCollection( );
            datasetcol.addSeries( cissensordata );
            if(EIGHTBITS)
                for(int i=0; i<y.length ; i++)
                {
                    cissensordata.add(i, Byte.toUnsignedInt(y[i]));
                }
            else
                for(int i=0; i<y.length/2 ; i++)
                    cissensordata.add((i),y[i*2]*4+y[2*i+1]);
        }
        else
        {
            if(EIGHTBITS)
                for(int i=0; i<y.length; i++)
                    cissensordata.update((Number)i,Byte.toUnsignedInt(y[i]));
            else
                for(int i=0; i<y.length/2; i++)
                    cissensordata.update((Number)(i),y[i*2]*4+y[2*i+1]);
        }

        return datasetcol;
    }
    long m_T1,m_T0;

    public CISFrame(final String title) {
        super(title);

        cisReader = new CanonCISReader();

        try {
            TestBase.init(new String [] {SERIALPORT});
            System.out.println("PureJavaComm Test Suite");
            System.out.println("Using port: " + TestBase.getPortName());
            TestFreeFormPortIdentifiers.testMissingPortInCommPortIdentifier();
            TestFreeFormPortIdentifiers.testDevicePathInCommPortIdentifier();
            TestFreeFormPortIdentifiers.testDevicePathToInvalidTTYInCommPortIdentifier();

            cisReader.initReader(SERIALPORT, BAUDRATE);
            if(EIGHTBITS)
                cisReader.set8Bits(true);
            else
                cisReader.set8Bits(false);

        } catch (Exception e) {
            e.printStackTrace();
        }

        XYSeriesCollection datasetcol=updateOrCreateDataset();
        xylineChart = ChartFactory.createXYLineChart(
                "CIS" ,
                "Sensor Pixel" ,
                "Intensity" ,
                datasetcol ,
                PlotOrientation.VERTICAL ,
                true ,true , false);

        ChartPanel chartPanel = new ChartPanel( xylineChart );
        chartPanel.setPreferredSize( new java.awt.Dimension( 1120 , 720 ) );
        final XYPlot plot = xylineChart.getXYPlot( );
        plot.setBackgroundPaint(Color.BLACK);

        NumberAxis domain = (NumberAxis) plot.getDomainAxis();
        if(EIGHTBITS)
        {
            System.out.println(CanonCISReader.getMaxDataPoints());
            domain.setRange(0.00, CanonCISReader.getMaxDataPoints());
        }
        else
        {
            System.out.println(CanonCISReader.getMaxDataPoints());
            domain.setRange(0.00, (double) cisReader.getMaxDataPoints() /2);

        }
        domain.setTickUnit(new NumberTickUnit(200));
        domain.setVerticalTickLabels(true);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        if(EIGHTBITS)
            //	range.setRange(0.0, 128.0);
            range.setRange(0.0,256.0);
        else
            range.setRange(0.0, 128.0*4);
        range.setTickUnit(new NumberTickUnit(50));

        m_T0 = System.currentTimeMillis();

        setContentPane( chartPanel );

        timer = new Timer(FAST, new ActionListener() {


            @Override
            public void actionPerformed(ActionEvent e) {

                updateOrCreateDataset();
                XYPlot cdplot = (XYPlot)xylineChart.getPlot();
                XYItemRenderer xyir1 = cdplot.getRenderer();
                xyir1.setSeriesPaint(0, Color.GREEN);
                ((AbstractSeriesDataset)cdplot.getDataset(0)).seriesChanged(null);  // will cause plot 1 to redraw

                //   dataset.advanceTime();

            }
        });



		/*
        final JButton run = new JButton(STOP);
        run.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String cmd = e.getActionCommand();
                if (STOP.equals(cmd)) {
                    timer.stop();
                    run.setText(START);
                } else {
                    timer.start();
                    run.setText(STOP);
                }
            }
        });

        final JComboBox combo = new JComboBox();
        combo.addItem("Fast");
        combo.addItem("Slow");
        combo.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if ("Fast".equals(combo.getSelectedItem())) {
                    timer.setDelay(FAST);
                } else {
                    timer.setDelay(SLOW);
                }
            }
        });

        this.add(new ChartPanel(chart), BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(run);
        btnPanel.add(combo);
        this.add(btnPanel, BorderLayout.SOUTH);

        timer = new Timer(FAST, new ActionListener() {

            float[] newData = new float[cisReader.getMaxDataPoints()];

            @Override
            public void actionPerformed(ActionEvent e) {

            	  cisReader.readLine();
                  //xSeriesData=0;
                  for(int i=0;i<cisReader.getMaxDataPoints();i++)
                  {
                	  newData[i] = cisReader.getBuffer()[i]  
                  }

             //   dataset.advanceTime();
                dataset.et.appendData(newData);
            }
        });
		 */
    }




    public void start() {
        timer.start();
    }

    public static void main(final String[] args) {
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                CISFrame demo = new CISFrame(TITLE);
                demo.pack();
                demo.setVisible(true);
                demo.start();
            }
        });
    }
}