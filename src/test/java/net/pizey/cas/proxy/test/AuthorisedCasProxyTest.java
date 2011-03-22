package net.pizey.cas.proxy.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.pizey.cas.proxy.AuthorisedCasProxy;

import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.UnrecognizedOptionException;

/**
 * Unit test for simple AuthorisedCasProxy.
 */
public class AuthorisedCasProxyTest
    extends TestCase {
  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public AuthorisedCasProxyTest(String testName) {
    super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(AuthorisedCasProxyTest.class);
  }

  public void testCLI() throws Exception {
    try {
      AuthorisedCasProxy.configure(new String[] {});
      fail("Should have bombed");
    } catch (MissingOptionException e) {
      e = null;
    }
    try {
      AuthorisedCasProxy.configure(new String[] { "-u", "adam@example.org" });
      fail("Should have bombed");
    } catch (UnrecognizedOptionException e) {
      e = null;
    }
    try {
      AuthorisedCasProxy.configure(new String[] { "-us", "adam@example.org" });
      fail("Should have bombed");
    } catch (UnrecognizedOptionException e) {
      e = null;
    }
    AuthorisedCasProxy.configure(new String[] { "-user", "adam@example.org", "-password", "bar", "-host", "cloud1.cggh.org", "-ticketGrantingServiceUrl", "http://cloud1.cggh.org/sso/v1/tickets" });
    
    AuthorisedCasProxy.configure(new String[] { "-port", "7777", "-user", "adam@example.org", "-password", "bar", "-host", "cloud1.cggh.org", "-ticketGrantingServiceUrl", "http://cloud1.cggh.org/sso/v1/tickets" });

  }

  public void testStopWhenNotStarted() throws Exception {
    
    AuthorisedCasProxy.configure(new String[] { "-port", "7777", "-user", "adam@example.org", "-password", "bar", "-host", "cloud1.cggh.org", "-ticketGrantingServiceUrl", "http://cloud1.cggh.org/sso/v1/tickets" });
    
    AuthorisedCasProxy.stop();
    
  }
}
