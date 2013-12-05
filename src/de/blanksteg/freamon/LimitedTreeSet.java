package de.blanksteg.freamon;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * The LimitedTreeSet class is a very simple wrapper around a conventional {@link TreeSet} to
 * discard the lowest element upon insertion of an element that would exceed a user defined
 * threshold value.
 *
 * @param <T> The type of the values stored in this set.
 */
public class LimitedTreeSet<T>
{
  /** The actual TreeSet elements are stored inside. */
  private final TreeSet<T> base;
  /** The limit after which elements should be deleted. */
  private final int limit;
  
  /**
   * Creates a new LimitedTreeSet instance that stores the given amount of elements ordered according
   * to the given comparator.
   * 
   * @param limit The threshold to delete elements after.
   * @param comp Comparator to sort items with.
   */
  public LimitedTreeSet(int limit, Comparator<? super T> comp)
  {
    this.base  = new TreeSet<T>(comp);
    this.limit = limit;
  }
  
  /**
   * Add an element to this set, possibly discarding a contained one. If an item is removed, it will
   * be returned by this function. Otherwise null is returned.
   * 
   * @param t The element to add.
   * @return The set's first element if it was removed, null otherwise.
   */
  public T add(T t)
  {
    T ret = null;
    if (this.base.size() == this.limit)
    {
      ret = this.base.pollFirst();
    }
    
    this.base.add(t);
    return ret;
  }
  
  /**
   * Get the {@link TreeSet} that is used as the basis of this set.
   * 
   * @return The base {@link TreeSet}.
   */
  public TreeSet<T> asTreeSet()
  {
    return this.base;
  }
}
