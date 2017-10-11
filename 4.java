/*
 * COMP2370 Fall 2017 Programming Assignment 4 Sample Solution
 *   Multiply Large Numbers in base 256
 *
 * @author mikegoss
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

public class Program4 {

  /**
   * @param args the command line arguments
   * @throws java.io.FileNotFoundException
   */
  public static void main(String[] args) throws FileNotFoundException {
    // Check for single argument (use standard input if not specified)
    if (args.length > 1) {
      System.err.println("usage: Program4 fileName");
      System.exit(1);
    }

    // Create Scanner for input
    Scanner dataIn = null;
    if (args.length == 1) {  // if file specified
      dataIn = new Scanner(new File(args[0]));
    } else {                 // use standard input
      dataIn = new Scanner(System.in);
    }

    // Write headings for output
    System.out.println("\"n\",\"Algorithm\",\"CPU Seconds\",\"Result\"");

    MultiplyNumbersFromFile(dataIn);
  }

  /**
   * UB - return unsigned value of byte
   *
   * @param b value of type byte
   * @return equivalent unsigned integer value
   */
  static int UB(byte b) {
    return b & 0xFF;
  }

  /**
   * MultiplyNumbersFromFile
   *   Read a file which specifies two arbitrary-precision byte-digit numbers,
   *   multiply them together, and write the result.
   *
   * @param fileName
   */
  static void MultiplyNumbersFromFile(Scanner dataIn) {
    // Read size of numbers
    int n = dataIn.nextInt();

    // Allocate storage for the two numbers and the result
    byte[] u = new byte[n];
    byte[] v = new byte[n];

    // Read the first number
    for (int b = n - 1; b >= 0; --b) {
      u[b] = (byte) dataIn.nextInt();
    }

    // Read the second number
    for (int b = n - 1; b >= 0; --b) {
      v[b] = (byte) dataIn.nextInt();
    }

    // Compute number of iterations
    int iterations = Integer.max(4, 16*1024/n);

    // Compute brute-force product
    byte[] mw = null;
    CpuTimer bfTimer = new CpuTimer();
    for (int i = 0; i < iterations; ++i) {
      mw = Multiply(u, v);
    }
    double avgBfTime = bfTimer.getElapsedCpuTime() / (double) iterations;

    // Compute recursive product
    byte[] rw = null;
    CpuTimer rpTimer = new CpuTimer();
    for (int i = 0; i < iterations; ++i) {
      rw = MultiplyRecursive(u, v);
    }
    double avgRpTime = rpTimer.getElapsedCpuTime() / (double) iterations;

    // Double-check results
    for (int i = 0; i < mw.length; ++i) {
      if (mw[i] != rw[i]) {
        System.err.println("ERROR: size " + n + ", results differ at byte " + i);
        break;
      }
    }

    System.out.println(n + ",M," + avgBfTime + ",\"" + B256ToString(mw) + "\"");
    System.out.println(n + ",R," + avgRpTime + ",\"" + B256ToString(rw) + "\"");
  }

  /**
   * Multiply
   *   Multiply two arbitrary size base 256 numbers using the brute-force method
   *
   * @param u,v numbers to multiply
   * @return resulting product
   */
  static byte[] Multiply(final byte[] u, final byte[] v) {
    int n = u.length;
    assert v.length == n;
    byte[] w = new byte[n*2];  // result - Java initializes to 0

    // For each byte in v
    for (int j = 0; j < n; ++j) {
      int c = 0;  // carry bit
      // For each byte in u
      for (int i = 0; i < n; ++i) {
        int t = UB(u[i]) * UB(v[j]) + UB(w[i+j]) + c;
        w[i+j] = (byte)(t % 256);    // lower byte
        c = t / 256;                 // upper (carry)
      }

      // Propagate carry
      int k = n + j;
      while (k < w.length && c != 0) {
        int t = UB(w[k]) + c;
        w[k] = (byte)(t % 256);   // lower byte
        c = t / 256;              // upper (carry)
        ++k;
      }
    }

    return w;
  }

  /**
   * MultiplyRecursive
   *   Recursive multiplication of binary numbers
   */
  static byte[] MultiplyRecursive(final byte[] u, final byte[] v) {
    int n = u.length;
    assert v.length == n;
    assert (n % 2) == 0 || n == 1;  // size must be even or 1
    int nhalf = n / 2;
    int n2 = n * 2;

    byte[] w = new byte[n2];  // allocate result (Java clears to 0s)

    // Check for base case
    if (n == 1) {
      int uv = UB(u[0]) * UB(v[0]);
      w[0] = (byte)(uv % 256);
      w[1] = (byte)(uv / 256);
      return w;
    }

    // Define and initialize temporary arrays of size n/2
    byte[] u1 = new byte[nhalf];
    System.arraycopy(u, 0, u1, 0, nhalf);
    byte[] u2 = new byte[nhalf];
    System.arraycopy(u, nhalf, u2, 0, nhalf);
    byte[] v1 = new byte[nhalf];
    System.arraycopy(v, 0, v1, 0, nhalf);
    byte[] v2 = new byte[nhalf];
    System.arraycopy(v, nhalf, v2, 0, nhalf);

    byte[] w1 = MultiplyRecursive(u2, v2);
    byte[] uDiff = Arrays.copyOf(u2, u2.length);
    int uDiffSign = ShiftedSubtractFrom(uDiff, u1, 0);
    byte[] vDiff = Arrays.copyOf(v1, v1.length);
    int vDiffSign = ShiftedSubtractFrom(vDiff, v2, 0);
    byte[] w2 = MultiplyRecursive(uDiff, vDiff);
    byte[] w3 = MultiplyRecursive(u1, v1);

    System.arraycopy(w3, 0, w, 0, n);

    ShiftedAddTo(w, w3, nhalf);
    ShiftedAddTo(w, w1, nhalf);
    ShiftedAddTo(w, w1, n);
    if (uDiffSign == vDiffSign) {  // if product is non-negative
      ShiftedAddTo(w, w2, nhalf);
    } else {
      int sign = ShiftedSubtractFrom(w, w2, nhalf);
      assert sign == 0;
    }

    return w;
  }

  /**
   * ShiftedSubtractFrom
   *   Subtract one number (b) from another (a), giving the absolute
   *   value of the difference
   *
   * @param a number to subtract from (difference on return)
   * @param b number to subtract from a
   * @param k number of bits to shift b left by
   * @return 1 if difference was negative, 0 if positive
   */
  static int ShiftedSubtractFrom(byte[] a, byte[] b, int k) {
    int an = a.length;
    int bn = b.length;
    assert bn + k <= an;
    int borrow = 0;

   int i = 0;
    for (; i < bn; ++i) {
      int t = UB(a[i+k]) - UB(b[i]) - borrow;
      if (t >= 0) {
        a[i+k] = (byte) t;
        borrow = 0;
      } else {
        a[i+k] = (byte)((t) % 256);
        borrow = 1;
      }
    }

    // Propagate borrow
    while (borrow != 0 && i+k < an) {
      if (UB(a[i+k]) != 0) {
        a[i+k] = (byte)(UB(a[i+k])-1);
        borrow = 0;
      } else {
        a[i+k] = (byte) 255;
      }
      ++i;
    }

    if (borrow != 0) {  // result negative
      TwosComplement(a);
    }

    return borrow;
  }

  /**
   * ShiftedAddTo
   *   Compute a = a + b*256^k by offsetting index of b by k
   *
   * @param a addend (twice as many bits as b)
   * @param b addend, multiplied by 256^k
   * @param k power of 2 to multiply by b
   */
  static void ShiftedAddTo(byte[] a, byte[] b, int k) {
    int n = b.length;
    assert a.length >= n+k;

    // Perform the n-bit add
    int c = 0;  // carry
    int i = 0;
    for (; i < n; ++i) {
      int t = UB(a[i+k]) + UB(b[i]) + c;
      a[i+k] = (byte)(t % 256);
      c = t / 256;
    }

    // Propagate the carry bit
    while (c != 0  &&  i+k < a.length) {
      int t = UB(a[i+k]) + c;
      a[i+k] = (byte)(t % 256);
      c = t / 256;
      ++i;
    }

    assert c == 0;
  }

  /**
   * TwosComplement
   *   Take the 2s complement of the argument, in place
   */
  static void TwosComplement(byte[] a) {
    // 2s complement is complement and add 1
    int n = a.length;

    // Complement lowest bit and add 1
    int t = (UB(a[0]) ^ 0xFF) + 1;
    a[0] = (byte)(t % 256);
    int c = t / 256;

    // Complement and propagate carry
    for (int i = 1; i < n; ++i) {
      t = (UB(a[i]) ^ 0xFF) + c;
      a[i] = (byte)(t % 256);
      c = t / 256;
    }
  }

  /**
   * B256ToString
   Convert base 256 number to string
   *
   * @param x base 256 number to convert
   * @return String representation of x
   */
  static String B256ToString(final byte[] x) {
    int n = x.length;
    StringBuilder s = new StringBuilder(n);
    for (int b = n-1; b >= 0; --b) {
      if (b != n-1) s.append(' ');
      s.append(UB(x[b]));
    }
    return s.toString();
  }
}
