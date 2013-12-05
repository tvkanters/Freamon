package de.blanksteg.freamon.hal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SerializedFreamonHalTools
{
  public static FreamonHal read(File source) throws ClassNotFoundException, IOException
  {
    FileInputStream in = new FileInputStream(source);
    GZIPInputStream gin = new GZIPInputStream(in);
    ObjectInputStream oin = new ObjectInputStream(gin);
    FreamonHal hal = (FreamonHal)oin.readObject();
    oin.close();
    hal.reinit(source);
    return hal;
  }
  
  public static void write(File target, FreamonHal hal) throws IOException
  {
    FileOutputStream out = new FileOutputStream(target);
    GZIPOutputStream gout = new GZIPOutputStream(out);
    ObjectOutputStream oout = new ObjectOutputStream(gout);
    oout.writeObject(hal);
    oout.close();
  }
  
  public static void writeThreaded(final File target, final FreamonHal hal)
  {
    Thread thread = new Thread()
    {
      @Override
      public void run()
      {
        try
        {
          synchronized (hal)
          {
            write(target, hal);
          }
        }
        catch (IOException e)
        {
          e.printStackTrace();
        }
      }
    };
    
    thread.setDaemon(false);
    thread.start();
  }
}
