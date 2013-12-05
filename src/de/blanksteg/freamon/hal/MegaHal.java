package de.blanksteg.freamon.hal;

public interface MegaHal
{
  public void add(String sentence);
  public String getSentence(String token);
  public void close();
}
