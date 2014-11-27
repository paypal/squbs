package org.squbs.concurrent.util;

import java.util.GregorianCalendar;
import java.util.Calendar;


/**
 * RandNum is a random number/value generator. This
 * is a primitive facility for RandomValues.
 * RandomValues and all subclasses generate
 * application-specific random values.
 *
 * @author Shanti Subramanyam
 */
public class Random {

    private java.util.Random        r;
    private static char[] alpha =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
                    'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
                    'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
                    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
                    'u', 'v', 'w', 'x', 'y', 'z'};
    private static char[] characs =
            {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
                    'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    /**
     * Constructs the random value generator.
     */
    public Random() {
        r = new java.util.Random();
    }

    /**
     * Constructs the random value generators with a seed.
     * @param seed The seed for the random value generator
     */
    public Random(long seed) {
        r = new java.util.Random(seed);
    }

    /**
     * Selects a random number uniformly distributed between x and y,
     * inclusively, with a mean of (x+y)/2.
     *
     * @param x the x value
     * @param y the y value
     * @return the random value between x and y, inclusive
     */
    public int random(int x, int y) {
        // Switch x and y if y less than x
        if (y < x) {
            int t = y;
            y = x;
            x = t;
        }
        return x + Math.abs(r.nextInt() % (y - x + 1));
    }

    /*
     * Function lrandom
     */

    /**
     * Selects a long random number uniformly distributed between x and y,
     * inclusively, with a mean of (x+y)/2.
     *
     * @param x the x value
     * @param y the y value
     * @return the random value between x and y, inclusive
     */
    public long lrandom(long x, long y) {
        // Switch x and y if y less than x
        if (y < x) {
            long t = y;
            y = x;
            x = t;
        }
        return x + Math.abs(r.nextLong() % (y - x + 1));
    }

    /**
     * Selects a double random number uniformly distributed between x and y,
     * inclusively, with a mean of (x+y)/2.
     *
     * @param x the x value
     * @param y the y value
     * @return the random value between x and y, exclusive
     */
    public double drandom(double x, double y) {
        return (x + (r.nextDouble() * (y - x)));
    }

    /**
     * NURand integer non-uniform random number generator.
     * TPC-C function NURand(A, x, y) =
     *      (((random(0,A) | random(x,y)) + C) % (y - x + 1)) + x
     *
     * @param A the A value
     * @param x the x value
     * @param y the y value
     * @return the random value between x and y, inclusive
     */
    public int NURand(int A, int x, int y) {

        int C, nurand;

        C      = 123;    /* Run-time constant chosen between 0, A */
        nurand = (((random(0, A) | random(x, y)) + C) % (y - x + 1)) + x;

        return (nurand);
    }

    /**
     * makeAString [x..y] generates a random string of alphanumeric
     * characters of random lengthof mininum x, maximum y and mean (x+y)/2
     *
     * @param x the minimum length
     * @param y the maximum length
     * @return the random string of length between x and y
     */
    public String makeAString(int x, int y) {

        int    len;    /* len of string */

        if (x == y) {
            len = x;
        } else {
            len = random(x, y);
        }

        char[] buffer = new char[len];

        for (int i = 0; i < len; i++) {
            int j = random(0, alpha.length - 1);
            buffer[i] = alpha[j];
        }
        return new String(buffer);
    }

    /**
     * makeCString [x..y] generates a random string of only alpahabet
     * characters of random length of mininum x, maximum y and
     * mean (x+y)/2
     *
     * @param x the minimum length
     * @param y the maximum length
     * @return the random character string of length between x and y
     */
    public String makeCString(int x, int y) {

        int    len;    /* len of string */

        if (x == y) {
            len = x;
        } else {
            len = random(x, y);
        }

        char[] buffer = new char[len];

        for (int i = 0; i < len; i++) {
            int j = random(0, characs.length - 1);
            buffer[i] = characs[j];
        }

        return new String(buffer);
    }

    /**
     * makeDateInInterval generates a java.sql.Date instance representing
     * a Date within the range specified by (input Date + x) and
     * (inputDate + y)
     *
     * @param inDate the reference date
     * @param x minimum offset from the reference date
     * @param y maximum offset from the reference date
     *
     * @return the resulting random date
     *
     */
    public java.sql.Date makeDateInInterval(java.sql.Date inDate,int x, int y) {

        int    dys;

        GregorianCalendar gcal = new GregorianCalendar();
        gcal.setTimeInMillis(inDate.getTime());

        if (x == y) {
            dys = x;
        } else {
            dys = random(x, y);
        }

        gcal.add(Calendar.DATE,dys);
        java.sql.Date outDate = new java.sql.Date(gcal.getTimeInMillis());

        return outDate;
    }

    /**
     * Creates a random calendar between time ref + min and ref + max.
     * @param ref The reference calendar
     * @param min The lower time offset from ref
     * @param max The upper time offset from ref
     * @param units The units of min and max, referencing the
     *              fields of Calendar, e.g. Calendar.YEAR
     * @return The randomly created calendar
     */
    public Calendar makeCalendarInInterval(Calendar ref, int min,
                                           int max, int units) {

        // As the calendar ref is passed in by reference and as a reference,
        // it is not a good idea to change it. So we clone it instead.
        Calendar baseCal = (Calendar) ref.clone();
        baseCal.add(units, min);
        long minMs = baseCal.getTimeInMillis();

        Calendar maxCal = (Calendar) ref.clone();
        maxCal.add(units, max);
        long maxMs = maxCal.getTimeInMillis();

        // Here I just reuse baseCal instead of creating a new one
        baseCal.setTimeInMillis(lrandom(minMs, maxMs));
        return baseCal;
    }

    /**
     * Creates a random calendar between Calendar min and max.
     * @param min The minimum time
     * @param max The maximum time
     * @return The randomly created calendar
     */
    public Calendar makeCalendarInInterval(Calendar min, Calendar max) {
        long minMs = min.getTimeInMillis();
        long maxMs = max.getTimeInMillis();

        // We use cloning so Calendar type, timezone, locale, and stuff
        // stay the same as min.
        Calendar result = (Calendar) min.clone();
        result.setTimeInMillis(lrandom(minMs, maxMs));
        return result;
    }

    /**
     * makeNString [x..y] generates a random string of only numeric
     * characters of random length of mininum x, maximum y and
     * mean (x+y)/2.
     *
     * @param x the minimum length
     * @param y the maximum length
     * @return the random character string of length between x and y
     */
    public String makeNString(int x, int y) {

        int    len, i;
        //String str = "";

        if (x == y) {
            len = x;
        } else {
            len = random(x, y);
        }

        char[] buffer = new char[len];

        for (i = 0; i < len; i++) {
            buffer[i] = (char) random('0', '9');
        }

        return new String(buffer);
    }
}