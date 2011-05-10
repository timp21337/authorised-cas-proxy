package net.pizey.cas.proxy;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

public class ProxyWorker extends AuthorisedCasProxy implements Runnable {

  final static int BUF_SIZE = 2048;

  static final byte[] EOL = { (byte)'\r', (byte)'\n' };

  /* buffer to use for requests */
  byte[] buf;
  /* Socket to client we're handling */
  private Socket socket;

  ProxyWorker() {
    buf = new byte[BUF_SIZE];
    socket = null;
  }

  synchronized void setSocket(Socket s) {
    this.socket = s;
    notify();
  }

  public synchronized void run() {
    while (AuthorisedCasProxy.keepGoing) {
      if (socket == null) {
        /* nothing to do */
        try {
          wait();
        } catch (InterruptedException e) {
          /* should not happen */
          continue;
        }
      }
      try {
        handleClient();
      } catch (Exception e) {
        handleException(e);
      }
      /* go back in wait queue if there's fewer
      * than numHandler connections.
      */
      socket = null;
      Vector<ProxyWorker> pool = AuthorisedCasProxy.threads;
      synchronized (pool) {
        if (pool.size() >= AuthorisedCasProxy.workers) {
          /* too many threads, exit this one */
          return;
        } else {
          pool.addElement(this);
        }
      }
    }
  }

  private void handleException(Exception e) {
    e.printStackTrace();
  }

  void handleClient() throws IOException {
    InputStream is = new BufferedInputStream(socket.getInputStream());
    PrintStream responsePrintStream = new PrintStream(socket.getOutputStream());
    /* we will only block in read for this many milliseconds
    * before we fail with java.io.InterruptedIOException,
    * at which point we will abandon the connection.
    */
    socket.setSoTimeout(AuthorisedCasProxy.timeout);
    socket.setTcpNoDelay(true);
    /* zero out the buffer from last time */
    for (int i = 0; i < BUF_SIZE; i++) {
      buf[i] = 0;
    }
    try {
      /* We only support HTTP GET/HEAD, and don't
      * support any fancy HTTP options,
      * so we're only interested really in
      * the first line.
      */
      int nread = 0, r = 0;

      outerloop: while (nread < BUF_SIZE) {
        r = is.read(buf, nread, BUF_SIZE - nread);
        if (r == -1) {
          /* EOF */
          return;
        }
        int i = nread;
        nread += r;
        for (; i < nread; i++) {
          if (buf[i] == (byte)'\n' || buf[i] == (byte)'\r') {
            /* read one line */
            break outerloop;
          }
        }
      }

      /* are we doing a GET or just a HEAD */
      boolean doingGet;
      String method = "";
      /* beginning of file name */
      int index;
      if (buf[0] == (byte)'G' &&
                    buf[1] == (byte)'E' &&
                    buf[2] == (byte)'T' &&
                    buf[3] == (byte)' ') {
        doingGet = true;
        index = 4;
        method = "GET";
      } else if (buf[0] == (byte)'H' &&
          buf[1] == (byte)'E' &&
          buf[2] == (byte)'A' &&
          buf[3] == (byte)'D' &&
          buf[4] == (byte)' ') {
        doingGet = false;
        index = 5;
        method = "HEAD";
      } else {
        /* we don't support this method */

        responsePrintStream.print("HTTP/1.0 " + HTTP_BAD_METHOD +
                        " unsupported method type: ");
        responsePrintStream.write(buf, 0, 5);
        responsePrintStream.write(EOL);
        responsePrintStream.flush();
        socket.close();
        return;
      }

      int i = 0;
      /* find the file name, from:
      * GET /foo/bar.html HTTP/1.0
      * extract "/foo/bar.html"
      */
      for (i = index; i < nread; i++) {
        if (buf[i] == (byte)' ') {
          break;
        }
      }
      String relativeUrl = new String(buf, index, i - index, "UTF-8");
      log(method + " " + relativeUrl + ":");
      String targetUrl = "http://" + host + relativeUrl;

      String name = relativeUrl.substring(relativeUrl.lastIndexOf('/') + 1);
      File file = new File(AuthorisedCasProxy.root, name);
      
      int status = CasProtectedResourceDownloader.downloadUrlToFile(targetUrl, file);
      log("Status:" + status);
      log("target:" + file);
      if (status != HTTP_OK)
        if (status == HTTP_NOT_FOUND)
          send404(responsePrintStream);
        else
          sendException(responsePrintStream, new RuntimeException("Unexpected status " + status));

      if (!file.exists()) {
        send404(responsePrintStream);
      } else {

        printHeaders(file, responsePrintStream);
        if (doingGet) {
          sendFile(file, responsePrintStream);
        }
      }
    } finally {
      socket.close();
    }
  }

  boolean printHeaders(File target, PrintStream ps) throws IOException {
    boolean ret;
    int rCode;
    rCode = HTTP_OK;
    ps.print("HTTP/1.0 " + HTTP_OK + " OK");
    ps.write(EOL);
    ret = true;
    log("From " + socket.getInetAddress().getHostAddress() + ": GET " +
                target.getAbsolutePath() + "-->" + rCode);
    ps.print("Server: Simple java");
    ps.write(EOL);
    ps.print("Date: " + (new Date()));
    ps.write(EOL);
    if (ret) {
      if (!target.isDirectory()) {
        ps.print("Content-length: " + target.length());
        ps.write(EOL);
        ps.print("Last Modified: " + (new
                        Date(target.lastModified())));
        ps.write(EOL);
        String name = target.getName();
        int ind = name.lastIndexOf('.');
        String ct = null;
        if (ind > 0) {
          ct = (String)map.get(name.substring(ind));
        }
        if (ct == null) {
          ct = "text/plain";
        }
        ps.print("Content-type: " + ct);
        ps.write(EOL);
      } else {
        ps.print("Content-type: text/html");
        ps.write(EOL);
      }
    }
    return ret;
  }

  void send404(PrintStream ps) throws IOException {
    ps.print("HTTP/1.0 " + HTTP_NOT_FOUND + " not found");
    ps.write(EOL);
    ps.write(EOL);
    ps.write(EOL);
    ps.println("Not Found\n\n" +
                "The requested resource was not found.\n");
  }

  private void sendException(PrintStream ps, RuntimeException unexpected) throws IOException {
    ps.print("HTTP/1.0 " + HTTP_SERVER_ERROR + " not found");
    ps.write(EOL);
    ps.write(EOL);
    ps.write(EOL);
    ps.println("Server Error \n\n" +
                unexpected.getMessage() + "\n");
  }

  void sendFile(File targ, PrintStream ps) throws IOException {
    InputStream is = null;
    ps.write(EOL);

    is = new FileInputStream(targ.getAbsolutePath());

    try {
      int n;
      while ((n = is.read(buf)) > 0) {
        ps.write(buf, 0, n);
      }
    } finally {
      is.close();
    }
  }

  /* mapping of file extensions to content-types */
  static Hashtable<String, String> map = new Hashtable<String, String>();

  static {
    fillMap();
  }

  static void setSuffix(String k, String v) {
    map.put(k, v);
  }

  static void fillMap() {
    setSuffix("", "text/plain");
    setSuffix(".uu", "application/octet-stream");
    setSuffix(".exe", "application/octet-stream");
    setSuffix(".ps", "application/postscript");
    setSuffix(".zip", "application/zip");
    setSuffix(".sh", "application/x-shar");
    setSuffix(".tar", "application/x-tar");
    setSuffix(".snd", "audio/basic");
    setSuffix(".au", "audio/basic");
    setSuffix(".wav", "audio/x-wav");
    setSuffix(".gif", "image/gif");
    setSuffix(".jpg", "image/jpeg");
    setSuffix(".jpeg", "image/jpeg");
    setSuffix(".htm", "text/html");
    setSuffix(".html", "text/html");
    setSuffix(".text", "text/plain");
    setSuffix(".c", "text/plain");
    setSuffix(".cc", "text/plain");
    setSuffix(".c++", "text/plain");
    setSuffix(".h", "text/plain");
    setSuffix(".pl", "text/plain");
    setSuffix(".txt", "text/plain");
    setSuffix(".java", "text/plain");
  }

}
