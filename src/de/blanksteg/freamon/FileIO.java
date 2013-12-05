package de.blanksteg.freamon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Simple FileIO class since Java is lacking this basic functionality.
 * 
 * @author Marc Müller
 */
public class FileIO
{
  /**
   * Reads a file either from the local directory pointed at or from the classpath. It first
   * attempts to find the file locally and falls back on the classpath if it can't be found.
   * 
   * @param path The path to the file.
   * @return The file's contents.
   * @throws IOException
   * @throws IllegalArgumentExcetion If the given path is null.
   */
  public static String readLocalOrClassPath(String path) throws IOException
  {
    if (path == null)
    {
      throw new IllegalArgumentException("The path is null.");
    }
    
    File file = new File(path);
    if (file.exists())
    {
      return readFile(file);
    }
    else
    {
      return readClassPathFile(path);
    }
  }
  
  /**
   * Reads a file from the given path relative to the classpath.
   * 
   * @param path The path to read from.
   * @return The file's lines concatenated into one string.
   * @throws IOException
   */
  public static String readClassPathFile(String path) throws IOException
  {
    if (path == null)
    {
      throw new IllegalArgumentException("The given path is null.");
    }
    
    InputStream       in  = ClassLoader.getSystemResourceAsStream(path);
    InputStreamReader inr = new InputStreamReader(in);
    
    return readStream(inr);
  }
  
  /**
   * Reads the file at the given path fully and returns its contents as a string.
   * 
   * @param path The path to the file.
   * @return The file's contents.
   * @throws IOException
   * @throws IllegalArgumentException If the path is null.
   */
  public static String readFile(String path) throws IOException
  {
    if (path == null)
    {
      throw new IllegalArgumentException("The given path is null.");
    }
    
    File file = new File(path);
    return readFile(file);
  }
  
  /**
   * Reads the given file fully and returns its contents as a string.
   * 
   * @param file The file to read.
   * @return The file's contents.
   * @throws IOException
   * @throws IllegalArgumentException If the file is null.
   */
  public static String readFile(File file) throws IOException
  {
    if (file == null)
    {
      throw new IllegalArgumentException("The given file is null.");
    }
    
    FileReader in = new FileReader(file);
    return readStream(in);
  }
  
  /**
   * Reads each line of the reader and gathers them in {@link String}.
   * 
   * @param in The reader to read from.
   * @return All lines concatenated and separated by \n.
   * @throws IOException
   */
  private static String readStream(Reader in) throws IOException
  {
    BufferedReader reader = new BufferedReader(in);
    
    StringBuffer sb = new StringBuffer();
    while (reader.ready())
    {
      sb.append(reader.readLine()+"\n");
    }
    return sb.toString();
  }
}
