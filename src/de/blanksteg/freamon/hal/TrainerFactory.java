package de.blanksteg.freamon.hal;

import java.io.File;

public interface TrainerFactory
{
  public Trainer createTrainerFor(File file);
}
