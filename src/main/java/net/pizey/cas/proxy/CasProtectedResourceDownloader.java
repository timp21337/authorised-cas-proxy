package net.pizey.cas.proxy;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Get a protected resource, store locally and return a URL to local resource.
 *
 * see https://wiki.jasig.org/display/CASUM/RESTful+API
 *
 * @author timp
 * @since 2010-12-09 17.05
 */
public class CasProtectedResourceDownloader {
  final static String charSet = "UTF-8";
  private static final Logger LOG = Logger.getLogger(CasProtectedResourceDownloader.class.getName());

  /**
   * @param uri         protected resource
   */
  public Tuple download(String uri) throws IOException {
    String ticketedUri = uri;
    String ticket = getTicket(AuthorisedCasProxy.ticketGrantingServiceUrl,
                AuthorisedCasProxy.user,
                AuthorisedCasProxy.password, uri);

    if (uri.indexOf('?') > -1)
      ticketedUri += "&";
    else
      ticketedUri += "?";

    ticketedUri += "ticket=" + ticket;

    String name = uri.substring(uri.lastIndexOf('/') + 1);
    name = URLEncoder.encode(name, "UTF-8");
    File existing = new File(AuthorisedCasProxy.root, name);
    if (existing.exists()) {
      System.err.println("Returning local file");
      return new Tuple(existing, AuthorisedCasProxy.HTTP_OK);
    } else
      return downloadUrlToFile(ticketedUri, name);
  }

  /**
   * Download the url content and save into the file.
   */
  public Tuple downloadUrlToFile(String url, String name) throws IOException {

    File file = new File(AuthorisedCasProxy.root, name);
    System.err.println("Destination:" + file.getAbsolutePath());
    GetMethod get = new GetMethod(url);
    get.setRequestHeader("User-Agent",
                "Mozilla/4.0 (compatible; MSIE 6.0; Windows 2000)");

    get.setFollowRedirects(true);
    HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager());
    try {
      client.executeMethod(get);
      if (get.getStatusCode() != AuthorisedCasProxy.HTTP_OK)
        return new Tuple(file, get.getStatusCode());
      InputStream in = get.getResponseBodyAsStream();
      FileOutputStream out = new FileOutputStream(file);
      byte[] buffer = new byte[1024];
      int count = -1;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      out.flush();
      out.close();

    } finally {
      get.releaseConnection();
    }
    System.err.println("File:" + file.getAbsolutePath());
    return new Tuple(file, get.getStatusCode());
  }

  /**
   * Get Ticket Granting Ticket.
   * TGT=`wget --post-data="username=${USERNAME}&password=${PASSWORD}" -d -O -  ${TICKETS} 2>&1 > t.tmp  | grep TGT | awk -F/ '{printf $NF}' | tr -d "\n" | tr -d "\r" `
   */
  public static String getTicketGrantingTicketUri(String ticketGrantingServiceUrl, String username, String password) throws IOException {
    URL ticketGrantingUrl = new URL(ticketGrantingServiceUrl);
    HttpURLConnection ticketGrantingConnection = (HttpURLConnection)ticketGrantingUrl.openConnection();
    ticketGrantingConnection.setRequestMethod("POST");
    ticketGrantingConnection.setDoOutput(true);
    String query = String.format("username=%s&password=%s",
                URLEncoder.encode(username, charSet),
                URLEncoder.encode(password, charSet));

    ticketGrantingConnection.setRequestProperty("Accept-Charset", charSet);
    ticketGrantingConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charSet);

    OutputStream output = null;
    try {
      output = ticketGrantingConnection.getOutputStream();
      output.write(query.getBytes(charSet));
    } finally {
      if (output != null)
        try {
          output.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

    int postStatus = ticketGrantingConnection.getResponseCode();
    System.err.println(postStatus);
    String location = ticketGrantingConnection.getHeaderField("Location");
    System.err.println(location);

    String ticket = location.substring(location.lastIndexOf('/') + 1);
    System.err.println(ticket);
    return ticket;
  }

  /**
   * Get Service Ticket.
   * ST=`wget --post-data="service=${TARGET}" -q -O -  ${TICKETS}/${TGT}`
   */
  public static String getServiceTicket(String location, String protectedUri) throws IOException {
    URL serviceTicketUrl = new URL(location);
    HttpURLConnection serviceTicketConnection = (HttpURLConnection)serviceTicketUrl.openConnection();
    serviceTicketConnection.setRequestMethod("POST");
    serviceTicketConnection.setDoOutput(true);

    serviceTicketConnection.setRequestProperty("Accept-Charset", charSet);
    serviceTicketConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charSet);

    String query = String.format("service=%s",
                URLEncoder.encode(protectedUri, charSet));

    OutputStream output = null;
    try {
      output = serviceTicketConnection.getOutputStream();
      output.write(query.getBytes(charSet));
    } finally {
      if (output != null)
        try {
          output.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

    int postStatus = serviceTicketConnection.getResponseCode();
    System.err.println("Post status" + postStatus);

    //Get the resource
    //wget -O - -d ${TARGET}?ticket=$ST |grep "atom:feed"

    InputStream is = serviceTicketConnection.getInputStream();
    BufferedReader in = new BufferedReader(new InputStreamReader(is));
    String line;
    String responsePage = "";
    while ((line = in.readLine()) != null)
      responsePage += line + "\n";
    in.close();

    System.err.println(responsePage);
    return "";

  }

  public static HttpURLConnection post(String uri, Properties params, String protectedUri) throws IOException {
    URL serviceTicketUrl = new URL(uri);
    HttpURLConnection connection = (HttpURLConnection)serviceTicketUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Accept-Charset", charSet);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charSet);

    String query = "";
    String separator = "";
    for (Object key : params.keySet()) {
      query += separator;
      separator = "&";
      query += String.format("%s=%s",
                    URLEncoder.encode((String)key, charSet),
                    URLEncoder.encode((String)params.get(key), charSet)
            );
    }

    OutputStream output = null;
    try {
      output = connection.getOutputStream();
      output.write(query.getBytes(charSet));
    } finally {
      if (output != null)
        try {
          output.close();
        } catch (IOException logOrIgnore) {
        }
    }
    return connection;
  }

  /**
   * Get the resource
   * wget -O - -d ${TARGET}?ticket=$ST |grep "atom:feed"
   */

  public static String getTicket(final String server, final String username,
                                   final String password, final String service) {
    notNull(server, "server must not be null");
    notNull(username, "username must not be null");
    notNull(password, "password must not be null");
    notNull(service, "service must not be null");

    return getServiceTicket(server, getTicketGrantingTicket(server, username,
                password), service);
  }

  private static String getServiceTicket(final String server,
                                           final String ticketGrantingTicket, final String service) {
    notNull(ticketGrantingTicket, "ticketGrantingTicket must not be null");

    final HttpClient client = new HttpClient();

    final PostMethod post = new PostMethod(server + "/" + ticketGrantingTicket);

    post.setRequestBody(new NameValuePair[] { new NameValuePair("service",
                service) });

    try {
      client.executeMethod(post);

      final String response = post.getResponseBodyAsString();

      switch (post.getStatusCode()) {
      case 200:
        return response;

      default:
        throw new RuntimeException("Invalid response code (" + post.getStatusCode()
              + ") from CAS server! - Response (1k): "
                            + response.substring(0, Math.min(1024, response.length())));
      }
    } catch (final IOException e) {
      LOG.warning(e.getMessage());
    } finally {
      post.releaseConnection();
    }

    return null;
  }

  private static String getTicketGrantingTicket(final String server,
      final String username, final String password) {
    final HttpClient client = new HttpClient();

    final PostMethod post = new PostMethod(server);

    post.setRequestBody(new NameValuePair[] {
                new NameValuePair("username", username),
                new NameValuePair("password", password) });

    try {
      client.executeMethod(post);

      final String response = post.getResponseBodyAsString();

      if (post.getStatusCode() == 201) {
        final Matcher matcher = Pattern.compile(".*action=\".*/(.*?)\".*")
                            .matcher(response);

        if (matcher.matches())
          return matcher.group(1);

        throw new RuntimeException("Successful ticket granting request, but no ticket found!" +
            "Response (first 1k): "
                            + response.substring(0, Math.min(1024, response.length())));
      } else {
        throw new RuntimeException("Invalid response code (" + post.getStatusCode()
              + ") from CAS server " + server
              + "\nResponse (1k): "
                            + response.substring(0, Math.min(1024, response.length())));
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    finally {
      post.releaseConnection();
    }

  }

  private static void notNull(final Object object, final String message) {
    if (object == null)
      throw new IllegalArgumentException(message);
  }

}
