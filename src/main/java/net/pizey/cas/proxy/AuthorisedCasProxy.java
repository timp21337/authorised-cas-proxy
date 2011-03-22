package net.pizey.cas.proxy;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * 
 * @see http://java.sun.com/developer/technicalArticles/Networking/Webserver/
 * @see http://java.sun.com/developer/technicalArticles/Networking/Webserver/WebServer.java
 */

public class AuthorisedCasProxy {
  protected static String password;
  protected static String ticketGrantingServiceUrl;
  protected static String host;
  protected static String user;
  public static final int HTTP_OK = 200;
  public static final int HTTP_NOT_FOUND = 404;
  public static final int HTTP_BAD_METHOD = 405;
  public static final int HTTP_SERVER_ERROR = 500;
  private static final int DEFAULT_LOCAL_PORT = 7777;
  private static final int DEFAULT_TIMEOUT_MILLIS = 5000;
  private static final int DEFAULT_WORKER_COUNT = 5;

  static boolean keepGoing = true;
  private static int port;

  protected static void log(String s) {
    System.out.println(s);
  }

  /* Where worker threads stand idle */
  static Vector<ProxyWorker> threads = new Vector<ProxyWorker>();

  /* the web server's virtual root */
  static File root;

  /* timeout on client connections */
  static int timeout = 0;

  /* max # worker threads */
  static int workers = 5;

  public static void main(String[] args) throws Exception {
    configure(args);

    /* start worker threads */
    for (int i = 0; i < workers; ++i) {
      ProxyWorker w = new ProxyWorker();
      (new Thread(w, "worker #" + i)).start();
      threads.addElement(w);
    }

    ServerSocket ss = new ServerSocket(port);

    while (keepGoing) {

      Socket s = ss.accept();

      ProxyWorker w;
      synchronized (threads) {
        if (threads.isEmpty()) {
          ProxyWorker ws = new ProxyWorker();
          ws.setSocket(s);
          (new Thread(ws, "additional worker")).start();
        } else {
          w = threads.elementAt(0);
          threads.removeElementAt(0);
          w.setSocket(s);
        }
      }
    }

  }

  public static void stop() {
    keepGoing = false;
    for (ProxyWorker pw : threads) {
      System.err.println("Stopping " + pw);
      pw.notify();
    }
  }

  public static void configure(String[] args) throws ParseException {

    root = new File(System.getProperty("user.dir"));
    timeout = DEFAULT_TIMEOUT_MILLIS;
    workers = DEFAULT_WORKER_COUNT;

    Options options = new Options();

    Option h = new Option("host", true, "The protected host, required");
    h.setRequired(true);
    options.addOption(h);

    Option portOption = new Option("port", true, "The local port, default = 7777");
    h.setRequired(false);
    options.addOption(portOption);

    Option tgu = new Option("ticketGrantingServiceUrl", true, "The ticket granting service url, required");
    tgu.setRequired(true);
    options.addOption(tgu);

    Option u = new Option("user", true, "The user to authenticate as, required");
    h.setRequired(true);
    options.addOption(u);
    Option p = new Option("password", true, "The user password, required");
    p.setRequired(true);
    options.addOption(p);

    // create the parser
    CommandLineParser parser = new GnuParser();
    CommandLine line = parser.parse(options, args);

    host = line.getOptionValue("host");
    ticketGrantingServiceUrl = line.getOptionValue("ticketGrantingServiceUrl");
    user = line.getOptionValue("user");
    password = line.getOptionValue("password");

    String portOptionValue = line.getOptionValue("port");
    if (portOptionValue != null)
      port = new Integer(portOptionValue).intValue();
    else
      port = DEFAULT_LOCAL_PORT;      

    log("root=" + root);
    log("timeout=" + timeout);
    log("workers=" + workers);
    log("port=" + port);
    log("host=" + host);
    log("user=" + user);
    log("password=****");
    log("ticketGrantingServiceUrl=" + ticketGrantingServiceUrl);

  }
}
