package org.opentripplanner.raptor.rangeraptor.transit;

import org.opentripplanner.raptor.api.request.SearchParams;

public interface TimeCalculator {
  /**
   * Use this constant to represent an uninitialized time value.
   */
  int TIME_NOT_SET = SearchParams.TIME_NOT_SET;

  /**
   * Return {@code true} is searching forward in space and time, {@code false} if search direction
   * is in reverse.
   */
  boolean searchForward();

  /**
   * Add duration to time and return the result. In the case of a normal forward search this will be
   * a plus '+' operation, while in a reverse search (moving back in time) this will be a minus '-'
   * operation: 'time - duration'.
   */
  int plusDuration(int time, int duration);

  /**
   * Subtract a positive duration from given time and return the result. In the case of a normal
   * forward search this will be a minus '-' operation, while in a reverse search (moving back in
   * time) this will be a plus '+' operation.
   */
  int minusDuration(int time, int duration);

  /**
   * Subtract a time (B) from time (A) and return the result. In the case of a normal forward search
   * this will be: 'B - A' operation, while in a reverse search (moving back in time) this will 'A -
   * B'.
   */
  int duration(int timeA, int timeB);

  /**
   * Forward search: Return {@code true} if the first argument ({@code subject}) is BEFORE the
   * second argument ({@code candidate}). If both are equal {@code false} is returned.
   * <p/>
   * Reverse search: Return {@code true} if the first argument ({@code subject}) is AFTER the second
   * argument ({@code candidate}). If both are equal {@code false} is returned.
   *
   * @return true if subject is better than the candidate; if not false.
   */
  boolean isBefore(int subject, int candidate);

  /**
   * Forward search: Return {@code true} if the first argument ({@code subject}) is AFTER the second
   * argument ({@code candidate}). If both are equal {@code false} is returned.
   * <p/>
   * Reverse search: Return {@code true} if the first argument ({@code subject}) is BEFORE the
   * second argument ({@code candidate}). If both are equal {@code false} is returned.
   *
   * @return true if subject is better than the candidate; if not false.
   */
  boolean isAfter(int subject, int candidate);

  /**
   * Uninitialized time values is set to this value to mark them as not set, and to mark the arrival
   * as unreached. A big value(or very small value) is used to simplify the comparisons to see if a
   * new arrival time is better (less).
   * <p/>
   * For a normal forward search this should be Integer.MAX_VALUE and for a reverse search this
   * should be Integer.MIN_VALUE.
   */
  int unreachedTime();
}
