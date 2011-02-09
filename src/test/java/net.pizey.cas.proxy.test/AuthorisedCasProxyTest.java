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
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AuthorisedCasProxyTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AuthorisedCasProxyTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }

    public void testCLI() throws Exception {
      AuthorisedCasProxy it = new AuthorisedCasProxy();
      try {
        it.configure(new String [] {});
        fail("Should have bombed");
      } catch (MissingOptionException e) {
        e = null;
      }
      try {
        it.configure(new String [] {"-u", "adam@example.org"});
        fail("Should have bombed");
      } catch (UnrecognizedOptionException e) {
        e = null;
      }
      try {
        it.configure(new String [] {"-us", "adam@example.org"});
        fail("Should have bombed");
      } catch (UnrecognizedOptionException e) {
        e = null;
      }
        it.configure(new String [] {"-user", "adam@example.org", "-password", "bar", "-host", "cloud1.cggh.org", "-ticketGrantingServiceUrl", "http://cloud1.cggh.org/sso/v1/tickets"});
        it.stop();
    
    }




}
