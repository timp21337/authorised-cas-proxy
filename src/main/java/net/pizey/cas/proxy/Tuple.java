package net.pizey.cas.proxy;

import java.io.File;

/**
 * @author timp
 * @since 11/02/11 23:15
 */
public class Tuple {
  public File file;
  public int status;
  public Tuple(File f, int status){
    this.file = f;
    this.status = status;
  }


}
